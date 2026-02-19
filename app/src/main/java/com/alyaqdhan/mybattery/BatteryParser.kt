package com.alyaqdhan.mybattery

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Locale

object BatteryParser {

    // ── Folder / file helpers ─────────────────────────────────────────────────

    fun isDumpStateLog(name: String) =
        name.startsWith("dumpState_", ignoreCase = true) &&
                name.endsWith(".log", ignoreCase = true)

    fun hasPersistedPermission(context: Context, uri: Uri): Boolean {
        val uriStr = uri.toString()
        if (context.contentResolver.persistedUriPermissions
                .any { it.uri.toString() == uriStr && it.isReadPermission }) return true
        return try {
            val treeDocId = DocumentsContract.getTreeDocumentId(uri)
            val docUri    = DocumentsContract.buildDocumentUriUsingTree(uri, treeDocId)
            context.contentResolver.query(
                docUri,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                null, null, null
            )?.use { it.count >= 0 } ?: false
        } catch (_: Exception) { false }
    }

    fun listChildren(folderUri: Uri, parentId: String, context: Context): List<DocEntry> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, parentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
        val result = mutableListOf<DocEntry>()
        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val idIdx   = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val modIdx  = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                while (cursor.moveToNext()) {
                    val id      = cursor.getString(idIdx)   ?: continue
                    val name    = cursor.getString(nameIdx) ?: ""
                    val mime    = cursor.getString(mimeIdx) ?: ""
                    val lastMod = if (modIdx >= 0) cursor.getLong(modIdx) else 0L
                    result += DocEntry(id, name, mime, lastMod)
                }
            }
        } catch (_: Exception) {}
        return result
    }

    fun findLatestLogEntry(folderUri: Uri, context: Context): DocEntry? {
        val treeDocId = DocumentsContract.getTreeDocumentId(folderUri)
        val topLevel  = listChildren(folderUri, treeDocId, context)
        val dirMime   = DocumentsContract.Document.MIME_TYPE_DIR

        var allLogs = topLevel.filter { isDumpStateLog(it.name) }.toMutableList()
        if (allLogs.isEmpty()) {
            for (child in topLevel) {
                if (child.mime == dirMime)
                    allLogs.addAll(listChildren(folderUri, child.id, context).filter { isDumpStateLog(it.name) })
            }
        }
        if (allLogs.isEmpty()) {
            allLogs = topLevel.filter {
                it.mime != dirMime && it.name.endsWith(".log", ignoreCase = true)
            }.toMutableList()
        }
        return allLogs.maxByOrNull { extractTimestampFromFileName(it.name).takeIf { ts -> ts != 0L } ?: it.lastModified }
    }

    fun listAllLogs(folderUri: Uri, context: Context): List<DocEntry> {
        val treeDocId = DocumentsContract.getTreeDocumentId(folderUri)
        val topLevel  = listChildren(folderUri, treeDocId, context)
        val dirMime   = DocumentsContract.Document.MIME_TYPE_DIR

        var allLogs = topLevel.filter { isDumpStateLog(it.name) }.toMutableList()
        if (allLogs.isEmpty()) {
            for (child in topLevel) {
                if (child.mime == dirMime)
                    allLogs.addAll(listChildren(folderUri, child.id, context).filter { isDumpStateLog(it.name) })
            }
        }
        if (allLogs.isEmpty()) {
            allLogs = topLevel.filter {
                it.mime != dirMime && it.name.endsWith(".log", ignoreCase = true)
            }.toMutableList()
        }
        return allLogs.sortedByDescending { extractTimestampFromFileName(it.name).takeIf { ts -> ts != 0L } ?: it.lastModified }
    }

    // ── Log parsing ───────────────────────────────────────────────────────────

    fun parseLogEntry(folderUri: Uri, entry: DocEntry, context: Context): BatteryInfo {
        val fileUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, entry.id)
        return try {
            context.contentResolver.openInputStream(fileUri)?.use { stream ->
                parseInputStream(stream, entry.name)
            } ?: BatteryInfo(
                healthPercent = null, cycleCount = null,
                logFileName   = entry.name,
                errorMessage  = "Could not open ${entry.name}"
            )
        } catch (e: Exception) {
            BatteryInfo(
                healthPercent = null, cycleCount = null,
                logFileName   = entry.name,
                errorMessage  = "Read error: ${e.localizedMessage}"
            )
        }
    }

    fun parseLatestLog(
        folderUri: Uri,
        context: Context,
        prefs: SharedPreferences
    ): BatteryInfo {
        val logEntry = findLatestLogEntry(folderUri, context)
            ?: return BatteryInfo(healthPercent = null, cycleCount = null, errorMessage = "NO_LOG_FOUND")

        val fileUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, logEntry.id)
        val info = try {
            context.contentResolver.openInputStream(fileUri)?.use { stream ->
                parseInputStream(stream, logEntry.name)
            } ?: BatteryInfo(
                healthPercent = null, cycleCount = null,
                logFileName   = logEntry.name,
                errorMessage  = "Could not open ${logEntry.name}"
            )
        } catch (e: Exception) {
            BatteryInfo(
                healthPercent = null, cycleCount = null,
                logFileName   = logEntry.name,
                errorMessage  = "Read error: ${e.localizedMessage}"
            )
        }

        BatteryCache.cacheBatteryInfo(prefs, info)
        return info
    }

    fun smartScan(
        folderUri: Uri,
        context: Context,
        prefs: SharedPreferences
    ): BatteryInfo {
        val latestEntry = findLatestLogEntry(folderUri, context)
        if (latestEntry == null) {
            val cached = BatteryCache.loadCachedBatteryInfo(prefs)
            if (cached != null) return cached
            return BatteryInfo(healthPercent = null, cycleCount = null, errorMessage = "NO_LOG_FOUND")
        }
        val cached = BatteryCache.loadCachedBatteryInfo(prefs)
        if (cached != null && cached.logFileName == latestEntry.name) return cached
        return parseLatestLog(folderUri, context, prefs)
    }

    fun extractBatterySection(folderUri: Uri, entry: DocEntry, context: Context): String {
        val fileUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, entry.id)
        return try {
            context.contentResolver.openInputStream(fileUri)?.use { stream ->
                val sections = mutableListOf<Pair<String, String>>()
                var currentSection = ""
                var currentLabel   = ""
                var state          = 0

                BufferedReader(InputStreamReader(stream)).use { reader ->
                    reader.forEachLine { line ->
                        when (state) {
                            0 -> when {
                                line.contains("BatteryInfoBackUp") -> {
                                    if (currentSection.isNotEmpty())
                                        sections.add(currentLabel to currentSection.trim())
                                    state = 1; currentLabel = "BatteryInfoBackUp"; currentSection = ""
                                }
                                line.contains("DUMP OF SERVICE battery:") -> {
                                    if (currentSection.isNotEmpty())
                                        sections.add(currentLabel to currentSection.trim())
                                    state = 2; currentLabel = "Battery Service Dump"; currentSection = ""
                                }
                            }
                            1 -> {
                                if (line.isNotBlank() && !line.startsWith(" ") && !line.startsWith("\t")
                                    && !line.trimStart().startsWith("m")) {
                                    state = 0
                                    if (line.contains("DUMP OF SERVICE battery:")) {
                                        if (currentSection.isNotEmpty())
                                            sections.add(currentLabel to currentSection.trim())
                                        state = 2; currentLabel = "Battery Service Dump"; currentSection = ""
                                    } else {
                                        sections.add(currentLabel to currentSection.trim())
                                        currentSection = ""; currentLabel = ""
                                    }
                                } else {
                                    currentSection += line + "\n"
                                }
                            }
                            2 -> {
                                if (line.contains("DUMP OF SERVICE") && !line.contains("DUMP OF SERVICE battery:")) {
                                    state = 0
                                    sections.add(currentLabel to currentSection.trim())
                                    currentSection = ""; currentLabel = ""
                                } else {
                                    currentSection += line + "\n"
                                }
                            }
                        }
                    }
                    if (currentSection.isNotEmpty())
                        sections.add(currentLabel to currentSection.trim())
                }

                if (sections.isEmpty()) return@use "(no battery sections found)"

                val logBufferRegex = Regex("""^\[[\w]+LogBuffer\]""")
                sections.joinToString("\n\n") { (label, content) ->
                    val filtered = buildString {
                        var insideBuffer = false
                        content.lines().forEach { line ->
                            val trimmed = line.trim()
                            when {
                                logBufferRegex.containsMatchIn(trimmed) -> insideBuffer = true
                                insideBuffer && trimmed.isEmpty() -> {}
                                insideBuffer && (trimmed.startsWith("[") || (!trimmed.startsWith(" ") &&
                                        trimmed.isNotEmpty() && !trimmed.first().isDigit())) -> {
                                    insideBuffer = false; appendLine(line)
                                }
                                insideBuffer -> {}
                                else -> appendLine(line)
                            }
                        }
                    }.trimEnd()
                    if (label.isNotEmpty()) "━━━ $label ━━━\n\n$filtered" else filtered
                }
            } ?: "(could not open file)"
        } catch (e: Exception) {
            "(read error: ${e.localizedMessage})"
        }
    }

    fun extractTimestampFromFileName(name: String): Long {
        val match = Regex("""(\d{12})""").find(name) ?: return 0L
        val f = match.groupValues[1]
        val str = "${f.substring(0,4)}-${f.substring(4,6)}-${f.substring(6,8)} " +
                "${f.substring(8,10)}:${f.substring(10,12)}:00"
        return try {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(str)?.time ?: 0L
        } catch (_: Exception) { 0L }
    }

    fun parseInputStream(stream: InputStream, fileName: String): BatteryInfo {
        var asoc: Int?     = null
        var asocSeen       = false
        var asocRaw        = ""
        var bsoh: Int?     = null
        var bsohRaw        = ""
        var usage: Int?    = null
        var usageRaw       = ""
        var llbType        = ""
        var firstUseDateMs = 0L
        var logTimestampMs = 0L

        val fullDateTimeRegex    = Regex("""(?:==\s*dumpstate:|dumpstate:|Build time:)\s*(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})""")
        val bracketDateTimeRegex = Regex("""^\[(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})]""")
        val dateTimeFmt          = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val llbRegex             = Regex("""^LLB\s+(CAL|MAN):\s*(\d{8})\s*$""", RegexOption.IGNORE_CASE)
        val compactDateFmt       = SimpleDateFormat("yyyyMMdd", Locale.US)

        BufferedReader(InputStreamReader(stream)).use { reader ->
            reader.forEachLine { line ->
                val t = line.trim()

                if (t.contains("mSavedBatteryAsoc:")) {
                    asocSeen = true
                    asocRaw  = t.substringAfter("mSavedBatteryAsoc:").trim()
                    asoc     = extractValueOrNull(t, "mSavedBatteryAsoc:")
                }
                if (t.contains("mSavedBatteryBsoh:")) {
                    bsohRaw = t.substringAfter("mSavedBatteryBsoh:").trim()
                    bsoh    = extractValueOrNull(t, "mSavedBatteryBsoh:")
                }
                if (t.contains("mSavedBatteryUsage:")) {
                    usageRaw = t.substringAfter("mSavedBatteryUsage:").trim()
                    val raw  = extractValueOrNull(t, "mSavedBatteryUsage:")
                    usage    = if (raw != null) raw / 1000 else null
                }
                if (firstUseDateMs == 0L) {
                    llbRegex.find(t)?.let { m ->
                        llbType        = m.groupValues[1].uppercase()
                        firstUseDateMs = try {
                            compactDateFmt.parse(m.groupValues[2])?.time ?: 0L
                        } catch (_: Exception) { 0L }
                    }
                }
                if (logTimestampMs == 0L) {
                    fullDateTimeRegex.find(t)?.let { m ->
                        logTimestampMs = dateTimeFmt.parse(m.groupValues[1])?.time ?: 0L
                    } ?: bracketDateTimeRegex.find(t)?.let { m ->
                        logTimestampMs = dateTimeFmt.parse(m.groupValues[1])?.time ?: 0L
                    }
                }
            }
        }

        if (logTimestampMs == 0L) logTimestampMs = extractTimestampFromFileName(fileName)

        val (resolvedHealth, healthSource, healthUnsupported) = when {
            asoc != null -> Triple(asoc, "asoc", false)
            bsoh != null -> Triple(bsoh, "bsoh", false)
            else         -> Triple(null, "", true)
        }

        val readSuccess = resolvedHealth != null || bsoh != null || usage != null ||
                firstUseDateMs != 0L || asocSeen

        return BatteryInfo(
            healthPercent     = resolvedHealth,
            healthSource      = healthSource,
            healthUnsupported = healthUnsupported,
            cycleCount        = usage,
            asocRaw           = asocRaw,
            bsohRaw           = bsohRaw,
            usageRaw          = usageRaw,
            llbType           = llbType,
            firstUseDateMs    = firstUseDateMs,
            logFileName       = fileName,
            logTimestampMs    = logTimestampMs,
            readSuccess       = readSuccess
        )
    }

    private fun extractValueOrNull(line: String, key: String): Int? {
        val raw = line.substringAfter(key, "").trim()
        if (raw.isBlank()) return null
        val stripped = when {
            raw.startsWith("[") && raw.contains("]") ->
                raw.removePrefix("[").substringBefore("]").trim()
            else -> raw.takeWhile { it.isDigit() || it == '-' }
        }
        if (stripped.equals("unsupported", ignoreCase = true)) return null
        val v = stripped.toIntOrNull() ?: return null
        return if (v < 0) null else v
    }
}