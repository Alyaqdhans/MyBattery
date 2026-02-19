package com.alyaqdhan.mybattery

import android.content.Context
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private object Routes {
    const val SETUP           = "setup"
    const val DASHBOARD       = "dashboard"
    const val WRONG_FOLDER    = "wrong_folder"
    const val FOLDER_DELETED  = "folder_deleted"
    const val PERMISSION_LOST = "permission_lost"
    const val LOG_RAW         = "log_raw"
}

private val AccentGreen  = Color(0xFF00E5A0)
private val AccentOrange = Color(0xFFFFAA44)
private val AccentRed    = Color(0xFFFF5C6C)
private val AccentBlue   = Color(0xFF4DA6FF)

// Darker variants used for the gauge in light theme for better contrast
private val AccentGreenLight  = Color(0xFF007A50)
private val AccentOrangeLight = Color(0xFFB85C00)
private val AccentRedLight    = Color(0xFFB52233)
private val AccentBlueLight   = Color(0xFF1A5FAA)

private val DarkBgDeep        = Color(0xFF080D18)
private val DarkBgCard        = Color(0xFF111827)
private val DarkBgCardBorder  = Color(0xFF1F2D42)
private val DarkTextPrimary   = Color(0xFFE8EEFF)
private val DarkTextSecondary = Color(0xFF7A8BA8)
private val DarkTextMuted     = Color(0xFF5A7090)

private val LightBgDeep        = Color(0xFFF1F5F9)
private val LightBgCard        = Color(0xFFFFFFFF)
private val LightBgCardBorder  = Color(0xFFE2E8F0)
private val LightTextPrimary   = Color(0xFF0F172A)
private val LightTextSecondary = Color(0xFF475569)
private val LightTextMuted     = Color(0xFF64748B)



@OptIn(ExperimentalMaterial3ExpressiveApi::class)
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val isDark = isSystemInDarkTheme()
            LaunchedEffect(isDark) {
                val ctrl = WindowCompat.getInsetsController(window, window.decorView)
                ctrl.isAppearanceLightStatusBars     = !isDark
                ctrl.isAppearanceLightNavigationBars = !isDark
            }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                MyBatteryApp()
            }
        }
    }

    data class BatteryInfo(
        val healthPercent: Int?,
        val healthSource: String = "",
        val healthUnsupported: Boolean = false,
        val cycleCount: Int?,           // usageRaw / 1000
        // raw values exactly as found in the log
        val asocRaw: String  = "",      // e.g. "96" or "unsupported" or ""
        val bsohRaw: String  = "",
        val usageRaw: String = "",      // raw integer before /1000
        val llbType: String  = "",      // "CAL" | "MAN" | ""
        val firstUseDateMs: Long = 0L,
        val logFileName:    String = "",
        val logTimestampMs: Long   = 0L,
        val readSuccess:    Boolean = false,
        val errorMessage:   String  = ""
    ) {
        val relativeDate: String
            get() {
                if (logTimestampMs == 0L) return ""
                val diffMs = System.currentTimeMillis() - logTimestampMs
                val mins   = diffMs / 60_000
                val hours  = diffMs / 3_600_000
                val days   = diffMs / 86_400_000
                return when {
                    mins  < 1  -> "just now"
                    mins  < 60 -> "$mins min${if (mins == 1L) "" else "s"} ago"
                    hours < 24 -> "$hours hour${if (hours == 1L) "" else "s"} ago"
                    days  < 7  -> "$days day${if (days == 1L) "" else "s"} ago"
                    else       -> SimpleDateFormat("MMM dd, yyyy", Locale.US)
                        .format(java.util.Date(logTimestampMs))
                }
            }

        val firstUseDateFormatted: String
            get() {
                if (firstUseDateMs == 0L) return ""
                return SimpleDateFormat("MMM yyyy", Locale.US)
                    .format(java.util.Date(firstUseDateMs))
            }
    }

    private fun cacheBatteryInfo(prefs: android.content.SharedPreferences, info: BatteryInfo) {
        if (!info.readSuccess) return
        prefs.edit {
            putString("cache_log_file_name",   info.logFileName)
            putLong  ("cache_log_timestamp_ms", info.logTimestampMs)
            putInt   ("cache_health_percent",   info.healthPercent ?: -1)
            putString("cache_health_source",    info.healthSource)
            putBoolean("cache_health_unsupported", info.healthUnsupported)
            putInt   ("cache_cycle_count",      info.cycleCount ?: -1)
            putLong  ("cache_first_use_date_ms", info.firstUseDateMs)
        }
    }

    private fun loadCachedBatteryInfo(prefs: android.content.SharedPreferences): BatteryInfo? {
        val fileName = prefs.getString("cache_log_file_name", null) ?: return null
        if (fileName.isBlank()) return null
        return BatteryInfo(
            healthPercent      = prefs.getInt("cache_health_percent", -1).takeIf { it >= 0 },
            healthSource       = prefs.getString("cache_health_source", "") ?: "",
            healthUnsupported  = prefs.getBoolean("cache_health_unsupported", false),
            cycleCount         = prefs.getInt("cache_cycle_count", -1).takeIf { it >= 0 },
            firstUseDateMs     = prefs.getLong("cache_first_use_date_ms", 0L),
            logFileName        = fileName,
            logTimestampMs     = prefs.getLong("cache_log_timestamp_ms", 0L),
            readSuccess        = true
        )
    }

    private fun clearBatteryInfoCache(prefs: android.content.SharedPreferences) {
        prefs.edit {
            remove("cache_log_file_name")
            remove("cache_log_timestamp_ms")
            remove("cache_health_percent")
            remove("cache_health_source")
            remove("cache_health_unsupported")
            remove("cache_cycle_count")
            remove("cache_first_use_date_ms")
        }
    }

    private data class DocEntry(
        val id: String, val name: String,
        val mime: String, val lastModified: Long = 0L
    )

    private fun listChildren(folderUri: Uri, parentId: String, context: Context): List<DocEntry> {
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

    private fun isDumpStateLog(name: String) =
        name.startsWith("dumpState_", ignoreCase = true) &&
                name.endsWith(".log", ignoreCase = true)

    private fun findLatestLogEntry(folderUri: Uri, context: Context): DocEntry? {
        val treeDocId = DocumentsContract.getTreeDocumentId(folderUri)
        val topLevel  = listChildren(folderUri, treeDocId, context)
        val dirMime   = DocumentsContract.Document.MIME_TYPE_DIR

        var allLogs = topLevel.filter { isDumpStateLog(it.name) }.toMutableList()
        if (allLogs.isEmpty()) {
            for (child in topLevel) {
                if (child.mime == dirMime) {
                    allLogs.addAll(listChildren(folderUri, child.id, context)
                        .filter { isDumpStateLog(it.name) })
                }
            }
        }
        if (allLogs.isEmpty()) {
            allLogs = topLevel.filter {
                it.mime != dirMime && it.name.endsWith(".log", ignoreCase = true)
            }.toMutableList()
        }
        return allLogs.maxByOrNull { it.name }
    }

    /** Returns all log entries sorted newest-first (by filename). */
    private fun listAllLogs(folderUri: Uri, context: Context): List<DocEntry> {
        val treeDocId = DocumentsContract.getTreeDocumentId(folderUri)
        val topLevel  = listChildren(folderUri, treeDocId, context)
        val dirMime   = DocumentsContract.Document.MIME_TYPE_DIR

        var allLogs = topLevel.filter { isDumpStateLog(it.name) }.toMutableList()
        if (allLogs.isEmpty()) {
            for (child in topLevel) {
                if (child.mime == dirMime) {
                    allLogs.addAll(listChildren(folderUri, child.id, context)
                        .filter { isDumpStateLog(it.name) })
                }
            }
        }
        if (allLogs.isEmpty()) {
            allLogs = topLevel.filter {
                it.mime != dirMime && it.name.endsWith(".log", ignoreCase = true)
            }.toMutableList()
        }
        return allLogs.sortedByDescending { it.name }
    }

    /** Parse a specific log entry by name (not necessarily the newest). */
    private fun parseLogEntry(
        folderUri: Uri,
        entry: DocEntry,
        context: Context
    ): BatteryInfo {
        val fileUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, entry.id)
        return try {
            context.contentResolver.openInputStream(fileUri)?.use { stream ->
                parseInputStream(stream, entry.name)
            } ?: BatteryInfo(healthPercent = null, cycleCount = null,
                logFileName = entry.name, errorMessage = "Could not open ${entry.name}")
        } catch (e: Exception) {
            BatteryInfo(healthPercent = null, cycleCount = null,
                logFileName = entry.name, errorMessage = "Read error: ${e.localizedMessage}")
        }
    }

    /**
     * Extracts organized battery sections from the log, preserving the order they appear in the file.
     */
    private fun extractBatterySection(
        folderUri: Uri,
        entry: DocEntry,
        context: Context
    ): String {
        val fileUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, entry.id)
        return try {
            context.contentResolver.openInputStream(fileUri)?.use { stream ->
                val sections = mutableListOf<Pair<String, String>>()
                var currentSection = ""
                var currentLabel = ""
                var state = 0

                BufferedReader(InputStreamReader(stream)).use { reader ->
                    reader.forEachLine { line ->
                        when (state) {
                            0 -> {
                                when {
                                    line.contains("BatteryInfoBackUp") -> {
                                        if (currentSection.isNotEmpty()) {
                                            sections.add(Pair(currentLabel, currentSection.trim()))
                                        }
                                        state = 1
                                        currentLabel = "BatteryInfoBackUp"
                                        currentSection = ""
                                    }
                                    line.contains("DUMP OF SERVICE battery:") -> {
                                        if (currentSection.isNotEmpty()) {
                                            sections.add(Pair(currentLabel, currentSection.trim()))
                                        }
                                        state = 2
                                        currentLabel = "Battery Service Dump"
                                        currentSection = ""
                                    }
                                }
                            }
                            1 -> {
                                if (line.isNotBlank() && !line.startsWith(" ") && !line.startsWith("\t")
                                    && !line.trimStart().startsWith("m")) {
                                    state = 0
                                    if (line.contains("DUMP OF SERVICE battery:")) {
                                        if (currentSection.isNotEmpty()) {
                                            sections.add(Pair(currentLabel, currentSection.trim()))
                                        }
                                        state = 2
                                        currentLabel = "Battery Service Dump"
                                        currentSection = ""
                                    } else {
                                        sections.add(Pair(currentLabel, currentSection.trim()))
                                        currentSection = ""
                                        currentLabel = ""
                                    }
                                } else {
                                    currentSection += line + "\n"
                                }
                            }
                            2 -> {
                                if (line.contains("DUMP OF SERVICE") && !line.contains("DUMP OF SERVICE battery:")) {
                                    state = 0
                                    sections.add(Pair(currentLabel, currentSection.trim()))
                                    currentSection = ""
                                    currentLabel = ""
                                } else {
                                    currentSection += line + "\n"
                                }
                            }
                        }
                    }
                    if (currentSection.isNotEmpty()) {
                        sections.add(Pair(currentLabel, currentSection.trim()))
                    }
                }

                if (sections.isEmpty()) {
                    "(no battery sections found)"
                } else {
                    // Strip [*LogBuffer] sections from the extracted content
                    val logBufferRegex = Regex("""^\[[\w]+LogBuffer\]""")
                    sections.joinToString("\n\n") { (label, content) ->
                        val filteredContent = buildString {
                            var insideBuffer = false
                            content.lines().forEach { line ->
                                val trimmed = line.trim()
                                when {
                                    logBufferRegex.containsMatchIn(trimmed) -> {
                                        insideBuffer = true
                                        // skip the header line
                                    }
                                    insideBuffer && trimmed.isEmpty() -> {
                                        // blank line might end the buffer block — keep skipping
                                    }
                                    insideBuffer && (trimmed.startsWith("[") || (!trimmed.startsWith(" ") && trimmed.isNotEmpty() && !trimmed.first().isDigit())) -> {
                                        // Next non-buffer section starts
                                        insideBuffer = false
                                        appendLine(line)
                                    }
                                    insideBuffer -> {
                                        // Still inside a buffer section — skip
                                    }
                                    else -> appendLine(line)
                                }
                            }
                        }.trimEnd()
                        if (label.isNotEmpty()) {
                            "━━━ $label ━━━\n\n$filteredContent"
                        } else {
                            filteredContent
                        }
                    }
                }
            } ?: "(could not open file)"
        } catch (e: Exception) {
            "(read error: ${e.localizedMessage})"
        }
    }

    private fun parseLatestLog(
        folderUri: Uri,
        context: Context,
        prefs: android.content.SharedPreferences
    ): BatteryInfo {
        val logEntry = findLatestLogEntry(folderUri, context)
            ?: return BatteryInfo(
                healthPercent = null, cycleCount = null, errorMessage = "NO_LOG_FOUND"
            )

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

        cacheBatteryInfo(prefs, info)
        return info
    }

    private fun smartScan(
        folderUri: Uri,
        context: Context,
        prefs: android.content.SharedPreferences
    ): BatteryInfo {
        val latestEntry = findLatestLogEntry(folderUri, context)

        if (latestEntry == null) {
            val cached = loadCachedBatteryInfo(prefs)
            if (cached != null) return cached
            return BatteryInfo(
                healthPercent = null, cycleCount = null, errorMessage = "NO_LOG_FOUND"
            )
        }

        val cached = loadCachedBatteryInfo(prefs)
        if (cached != null && cached.logFileName == latestEntry.name) {
            return cached
        }

        return parseLatestLog(folderUri, context, prefs)
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

    private fun isExplicitlyUnsupported(line: String, key: String): Boolean {
        val raw = line.substringAfter(key, "").trim()
        if (raw.isBlank()) return false
        val stripped = if (raw.startsWith("[") && raw.contains("]"))
            raw.removePrefix("[").substringBefore("]").trim()
        else raw.takeWhile { it != ' ' && it != '\n' }
        return stripped.equals("unsupported", ignoreCase = true) || stripped == "-1"
    }

    private fun parseInputStream(stream: java.io.InputStream, fileName: String): BatteryInfo {
        var asoc: Int?        = null
        var asocSeen          = false
        var asocRaw           = ""
        var bsoh: Int?        = null
        var bsohRaw           = ""
        var usage: Int?       = null
        var usageRaw          = ""
        var llbType           = ""
        var firstUseDateMs    = 0L
        var logTimestampMs    = 0L

        val fullDateTimeRegex    = Regex("""(?:==\s*dumpstate:|dumpstate:|Build time:)\s*(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})""")
        val bracketDateTimeRegex = Regex("""^\[(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})]""")
        val dateTimeFmt          = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val llbRegex       = Regex("""^LLB\s+(CAL|MAN):\s*(\d{8})\s*$""", RegexOption.IGNORE_CASE)
        val compactDateFmt = SimpleDateFormat("yyyyMMdd", Locale.US)

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
                    val m1 = fullDateTimeRegex.find(t)
                    if (m1 != null) {
                        logTimestampMs = dateTimeFmt.parse(m1.groupValues[1])?.time ?: 0L
                    } else {
                        bracketDateTimeRegex.find(t)?.let { m2 ->
                            logTimestampMs = dateTimeFmt.parse(m2.groupValues[1])?.time ?: 0L
                        }
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

        val readSuccess = resolvedHealth != null ||
                bsoh != null ||
                usage != null ||
                firstUseDateMs != 0L ||
                asocSeen

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

    private fun extractTimestampFromFileName(name: String): Long {
        // Match exactly 12 continuous digits (YYYYMMDDHHmm format)
        val match12 = Regex("""(\d{12})""").find(name)
        if (match12 != null) {
            val full = match12.groupValues[1]
            val year = full.substring(0, 4)
            val month = full.substring(4, 6)
            val day = full.substring(6, 8)
            val hour = full.substring(8, 10)
            val minute = full.substring(10, 12)
            val str = "$year-$month-$day $hour:$minute:00"
            return try {
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(str)?.time ?: 0L
            } catch (_: Exception) { 0L }
        }

        return 0L
    }

    private fun hasPersistedPermission(context: Context, uri: Uri): Boolean {
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

    @Composable
    fun MyBatteryTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
        val colorScheme = if (darkTheme) {
            darkColorScheme(
                primary              = AccentGreen,
                onPrimary            = Color(0xFF003823),
                primaryContainer     = Color(0xFF00533A),
                onPrimaryContainer   = Color(0xFF9CF5C0),
                secondary            = Color(0xFF4DA6FF),
                onSecondary          = Color(0xFF00315E),
                secondaryContainer   = Color(0xFF004884),
                onSecondaryContainer = Color(0xFFD1E4FF),
                tertiary             = AccentOrange,
                onTertiary           = Color(0xFF452B00),
                tertiaryContainer    = Color(0xFF633F00),
                onTertiaryContainer  = Color(0xFFFFDDB3),
                error                = AccentRed,
                onError              = Color(0xFF690005),
                errorContainer       = Color(0xFF93000A),
                onErrorContainer     = Color(0xFFFFDAD6),
                background           = DarkBgDeep,
                onBackground         = DarkTextPrimary,
                surface              = DarkBgCard,
                onSurface            = DarkTextPrimary,
                surfaceVariant       = Color(0xFF1A2740),
                onSurfaceVariant     = DarkTextSecondary,
                outline              = DarkBgCardBorder,
                outlineVariant       = DarkTextMuted,
                scrim                = Color(0xFF000000),
                inverseSurface       = DarkTextPrimary,
                inverseOnSurface     = DarkBgDeep,
                inversePrimary       = Color(0xFF006B4B),
                surfaceTint          = AccentGreen,
                surfaceContainerHighest = Color(0xFF1E2D42),
                surfaceContainerHigh    = Color(0xFF182238),
                surfaceContainer        = Color(0xFF131929),
                surfaceContainerLow     = Color(0xFF101520),
                surfaceContainerLowest  = DarkBgDeep,
            )
        } else {
            lightColorScheme(
                primary              = Color(0xFF006B4B),
                onPrimary            = Color.White,
                primaryContainer     = Color(0xFF9CF5C0),
                onPrimaryContainer   = Color(0xFF002115),
                secondary            = Color(0xFF1565C0),
                onSecondary          = Color.White,
                secondaryContainer   = Color(0xFFD1E4FF),
                onSecondaryContainer = Color(0xFF001C3A),
                tertiary             = Color(0xFF8B5000),
                onTertiary           = Color.White,
                tertiaryContainer    = Color(0xFFFFDDB3),
                onTertiaryContainer  = Color(0xFF2C1600),
                error                = Color(0xFFBA1A1A),
                onError              = Color.White,
                errorContainer       = Color(0xFFFFDAD6),
                onErrorContainer     = Color(0xFF410002),
                background           = LightBgDeep,
                onBackground         = LightTextPrimary,
                surface              = LightBgCard,
                onSurface            = LightTextPrimary,
                surfaceVariant       = Color(0xFFEEF2F8),
                onSurfaceVariant     = LightTextSecondary,
                outline              = LightBgCardBorder,
                outlineVariant       = LightTextMuted,
                scrim                = Color(0xFF000000),
                inverseSurface       = LightTextPrimary,
                inverseOnSurface     = LightBgDeep,
                inversePrimary       = Color(0xFF7FD9A5),
                surfaceTint          = Color(0xFF006B4B),
                surfaceContainerHighest = Color(0xFFE2E8F0),
                surfaceContainerHigh    = Color(0xFFEBF0F6),
                surfaceContainer        = Color(0xFFF1F5F9),
                surfaceContainerLow     = Color(0xFFF6F9FC),
                surfaceContainerLowest  = LightBgCard,
            )
        }
        MaterialTheme(colorScheme = colorScheme, content = content)
    }

    @Composable fun cardBorderColor()      = MaterialTheme.colorScheme.outline
    @Composable fun textPrimary()          = MaterialTheme.colorScheme.onBackground
    @Composable fun textSecondary()        = MaterialTheme.colorScheme.onSurfaceVariant
    @Composable fun textMuted()            = MaterialTheme.colorScheme.outlineVariant
    @Composable fun accentGreenEffective() = MaterialTheme.colorScheme.primary

    // Gauge accent colors — darker in light theme for readability
    @Composable fun gaugeGreen()  = if (isSystemInDarkTheme()) AccentGreen  else AccentGreenLight
    @Composable fun gaugeOrange() = if (isSystemInDarkTheme()) AccentOrange else AccentOrangeLight
    @Composable fun gaugeRed()    = if (isSystemInDarkTheme()) AccentRed    else AccentRedLight
    @Composable fun gaugeBlue()   = if (isSystemInDarkTheme()) AccentBlue   else AccentBlueLight
    @Composable fun gaugeGray()   = MaterialTheme.colorScheme.outlineVariant

    @Composable
    fun MyBatteryApp() {
        val context = LocalContext.current
        val prefs   = context.getSharedPreferences("battery_prefs", Context.MODE_PRIVATE)

        val savedUriStr = prefs.getString("folder_uri", null)
        val savedUri    = savedUriStr?.toUri()
        val hasSavedPerm = savedUri != null && hasPersistedPermission(context, savedUri)
        val hasSavedUri  = savedUri != null   // URI saved but permission may be gone

        var alreadyHasPerm by remember { mutableStateOf(hasSavedPerm) }
        var folderUri      by remember { mutableStateOf(savedUri) }
        var batteryInfo    by remember { mutableStateOf<BatteryInfo?>(null) }
        var folderAccessible           by remember { mutableStateOf(true) }
        var hasEverScannedSuccessfully by remember { mutableStateOf(false) }
        var isRefreshing               by remember { mutableStateOf(false) }
        var allLogEntries  by remember { mutableStateOf<List<DocEntry>>(emptyList()) }
        var selectedLogInfo by remember { mutableStateOf<BatteryInfo?>(null) }
        var showLogSheet   by remember { mutableStateOf(false) }
        var isLoadingDetail by remember { mutableStateOf(false) }
        var rawLogText     by remember { mutableStateOf("") }
        var isLoadingRaw   by remember { mutableStateOf(false) }

        // Gauge animation state hoisted here so it survives navigation (back from log raw, etc.)
        // and never causes a wave-jump redraw when the dashboard recomposes.
        val arcAnimatable  = remember { Animatable(0f) }
        var gaugeAmplitude by remember { mutableStateOf(1f) }
        // Incrementing this key re-triggers the fill animation (e.g. when cache is loaded silently)
        var gaugeReplayKey by remember { mutableStateOf(0) }

        // Drive the amplitude pulse from app scope — survives navigation
        LaunchedEffect(isLoadingDetail) {
            if (!isLoadingDetail) { gaugeAmplitude = 1f; return@LaunchedEffect }
            while (true) {
                delay(900L)
                gaugeAmplitude = if (gaugeAmplitude < 0.5f) 1f else 0f
            }
        }

        val navController = rememberNavController()
        val scope         = rememberCoroutineScope()

        // ── Live access monitor ───────────────────────────────────────────────
        // Folder deletion and permission revocation only happen while the app is
        // backgrounded. Checking on ON_RESUME covers both cases with zero overhead.
        val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
        val currentFolderUri = folderUri
        if (currentFolderUri != null) {
            DisposableEffect(lifecycle, currentFolderUri) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        val hasPerm = hasPersistedPermission(context, currentFolderUri)
                        if (!hasPerm && alreadyHasPerm) {
                            // Permission revoked
                            alreadyHasPerm   = false
                            folderAccessible = false
                            allLogEntries    = emptyList()
                            showLogSheet     = false
                            val cached = loadCachedBatteryInfo(prefs)
                            if (cached != null) {
                                batteryInfo                = cached
                                hasEverScannedSuccessfully = true
                                gaugeReplayKey++
                            }
                        } else if (hasPerm) {
                            // Permission intact — check if folder still exists
                            scope.launch {
                                val exists = withContext(Dispatchers.IO) {
                                    findLatestLogEntry(currentFolderUri, context) != null
                                }
                                if (!exists && folderAccessible) {
                                    folderAccessible = false
                                    allLogEntries    = emptyList()
                                    showLogSheet     = false
                                    val cached = loadCachedBatteryInfo(prefs)
                                    if (cached != null) {
                                        batteryInfo                = cached
                                        hasEverScannedSuccessfully = true
                                        gaugeReplayKey++
                                    }
                                } else if (exists && !folderAccessible && alreadyHasPerm) {
                                    folderAccessible = true
                                }
                            }
                        }
                    }
                }
                lifecycle.addObserver(observer)
                onDispose { lifecycle.removeObserver(observer) }
            }
        }

        // Decide start destination synchronously:
        //   • No saved URI at all → SETUP (fresh install)
        //   • Has saved URI (with or without permission) → DASHBOARD
        //     The startup LaunchedEffect will detect permission loss / folder deletion
        //     and navigate to the appropriate error screen from there.
        val startDestination = if (hasSavedUri) Routes.DASHBOARD else Routes.SETUP

        // Run the startup scan exactly once when we have a saved URI.
        if (hasSavedUri) {
            LaunchedEffect(Unit) {
                val uri = savedUri!!

                // ── 1. Permission lost ────────────────────────────────────────
                if (!hasPersistedPermission(context, uri)) {
                    val cached = loadCachedBatteryInfo(prefs)
                    if (cached != null) {
                        batteryInfo      = cached
                        folderAccessible = false
                        hasEverScannedSuccessfully = true
                        gaugeReplayKey++
                    }
                    isLoadingDetail = false
                    allLogEntries   = emptyList()
                    navController.navigate(Routes.PERMISSION_LOST) { launchSingleTop = true }
                    return@LaunchedEffect
                }

                isLoadingDetail = true
                val cached = loadCachedBatteryInfo(prefs)

                if (cached != null) {
                    // Show cached data immediately while we check for updates
                    batteryInfo = cached
                    folderAccessible = true
                    hasEverScannedSuccessfully = true

                    // Check whether the folder still exists and if there's a newer log
                    val latestEntry = withContext(Dispatchers.IO) { findLatestLogEntry(uri, context) }
                    if (latestEntry == null) {
                        // Folder deleted — cached data already in batteryInfo, replay fill
                        alreadyHasPerm   = false
                        folderAccessible = false
                        isLoadingDetail  = false
                        gaugeReplayKey++
                        navController.navigate(Routes.FOLDER_DELETED) { launchSingleTop = true }
                        return@LaunchedEffect
                    }
                    val hasNewer = latestEntry.name != cached.logFileName
                    if (hasNewer) {
                        val liveInfo = withContext(Dispatchers.IO) { parseLatestLog(uri, context, prefs) }
                        if (liveInfo.readSuccess) batteryInfo = liveInfo
                    }
                    isLoadingDetail = false
                } else {
                    // No cache — full scan
                    val latestEntry = withContext(Dispatchers.IO) { findLatestLogEntry(uri, context) }
                    if (latestEntry == null) {
                        isLoadingDetail  = false
                        alreadyHasPerm   = false
                        folderAccessible = false
                        // Try cache as last resort so dashboard isn't empty
                        val cached = loadCachedBatteryInfo(prefs)
                        if (cached != null) {
                            batteryInfo                = cached
                            hasEverScannedSuccessfully = true
                            gaugeReplayKey++
                        }
                        navController.navigate(Routes.FOLDER_DELETED) { launchSingleTop = true }
                        return@LaunchedEffect
                    }
                    val info = withContext(Dispatchers.IO) { smartScan(uri, context, prefs) }
                    isLoadingDetail = false
                    batteryInfo     = info
                    folderAccessible = true
                    if (info.readSuccess) {
                        hasEverScannedSuccessfully = true
                    } else {
                        navController.navigate(Routes.WRONG_FOLDER) { launchSingleTop = true }
                    }
                }
            }
        }

        val folderPicker = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocumentTree()
        ) { uri ->
            if (uri != null) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) {}
                val isNewFolder = uri.toString() != prefs.getString("folder_uri", null)
                prefs.edit { putString("folder_uri", uri.toString()) }
                if (isNewFolder) clearBatteryInfoCache(prefs)
                folderUri        = uri
                alreadyHasPerm   = true
                folderAccessible = true   // reset the "cached" state — we have live permission now
                allLogEntries    = emptyList()  // will be reloaded when log sheet is opened
                // Navigate to dashboard immediately — gauge will show loading while scan runs
                batteryInfo     = null
                isLoadingDetail = true
                navController.navigate(Routes.DASHBOARD) {
                    popUpTo(0) { inclusive = true }; launchSingleTop = true
                }
                scope.launch {
                    val info = withContext(Dispatchers.IO) { smartScan(uri, context, prefs) }
                    isLoadingDetail = false
                    if (info.readSuccess) {
                        batteryInfo      = info
                        folderAccessible = true
                        hasEverScannedSuccessfully = true
                    } else {
                        navController.navigate(Routes.WRONG_FOLDER) { launchSingleTop = true }
                    }
                }
            }
        }

        fun rescan() {
            if (folderUri == null || isRefreshing) return
            scope.launch {
                isRefreshing = true
                val uri = folderUri!!

                if (!hasPersistedPermission(context, uri)) {
                    isRefreshing  = false
                    showLogSheet  = false
                    allLogEntries = emptyList()
                    // Silently load cache so dashboard has data when user dismisses
                    val cached = loadCachedBatteryInfo(prefs)
                    if (cached != null) {
                        batteryInfo                = cached
                        hasEverScannedSuccessfully = true
                        gaugeReplayKey++
                    }
                    navController.navigate(Routes.PERMISSION_LOST) { launchSingleTop = true }
                    return@launch
                }

                val latestEntry = withContext(Dispatchers.IO) { findLatestLogEntry(uri, context) }

                if (latestEntry == null) {
                    isRefreshing   = false
                    alreadyHasPerm = false
                    showLogSheet   = false
                    // Silently load cache so dashboard has data when user dismisses
                    val cached = loadCachedBatteryInfo(prefs)
                    if (cached != null) {
                        batteryInfo                = cached
                        hasEverScannedSuccessfully = true
                        gaugeReplayKey++
                    }
                    navController.navigate(Routes.FOLDER_DELETED) { launchSingleTop = true }
                    return@launch
                }

                // Same file already displayed — nothing new to parse, but folder is accessible again
                val cached = loadCachedBatteryInfo(prefs)
                if (cached != null && cached.logFileName == latestEntry.name) {
                    alreadyHasPerm   = true
                    folderAccessible = true
                    isRefreshing     = false
                    return@launch
                }

                // New file found — now show gauge loading and parse it
                isLoadingDetail = true
                val info = withContext(Dispatchers.IO) { parseLatestLog(uri, context, prefs) }
                isRefreshing    = false
                isLoadingDetail = false
                if (info.readSuccess) {
                    batteryInfo      = info
                    alreadyHasPerm   = true
                    folderAccessible = true
                    hasEverScannedSuccessfully = true
                } else {
                    navController.navigate(Routes.WRONG_FOLDER) { launchSingleTop = true }
                }
            }
        }

        MyBatteryTheme {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                NavHost(
                    navController      = navController,
                    startDestination   = startDestination,
                    enterTransition    = { EnterTransition.None },
                    exitTransition     = { ExitTransition.None },
                    popEnterTransition = { EnterTransition.None },
                    popExitTransition  = { ExitTransition.None }
                ) {
                    composable(Routes.SETUP) {
                        val showBackButton = navController.previousBackStackEntry != null
                        var backHandled by remember { mutableStateOf(false) }
                        BackHandler(enabled = showBackButton) {
                            if (!backHandled) { backHandled = true; navController.popBackStack() }
                        }
                        SetupScreen(
                            hasLivePermission = alreadyHasPerm,
                            onPickFolder   = { folderPicker.launch(null) },
                            showBackButton = showBackButton,
                            onBack         = {
                                if (!backHandled) { backHandled = true; navController.popBackStack() }
                            },
                            isRefreshing   = isRefreshing,
                            onRescan       = { rescan() }
                        )
                    }

                    composable(Routes.DASHBOARD) {
                        val info = batteryInfo
                        BatteryDashboard(
                            info             = info ?: BatteryInfo(
                                healthPercent     = null, cycleCount = null,
                                healthUnsupported = false, readSuccess = false
                            ),
                            folderAccessible = alreadyHasPerm && folderAccessible,
                            isRefreshing     = isRefreshing,
                            isLoadingDetail  = isLoadingDetail || info == null,
                            arcAnimatable    = arcAnimatable,
                            gaugeAmplitude   = gaugeAmplitude,
                            gaugeReplayKey   = gaugeReplayKey,
                            onRescan         = { rescan() },
                            onShowSteps      = { navController.navigate(Routes.SETUP) },
                            onShowLogs       = {
                                val uri = folderUri
                                if (uri != null && hasPersistedPermission(context, uri)) {
                                    scope.launch {
                                        allLogEntries = withContext(Dispatchers.IO) {
                                            listAllLogs(uri, context)
                                        }
                                        showLogSheet = true
                                    }
                                } else {
                                    showLogSheet = true
                                }
                            },
                            showLogSheet     = showLogSheet,
                            allLogEntries    = allLogEntries,
                            onDismissSheet   = { showLogSheet = false },
                            onSelectEntry    = { entry ->
                                val uri = folderUri
                                if (uri != null) {
                                    scope.launch {
                                        isLoadingDetail = true
                                        showLogSheet    = false
                                        val parsed = withContext(Dispatchers.IO) {
                                            parseLogEntry(uri, entry, context)
                                        }
                                        isLoadingDetail = false
                                        if (parsed.readSuccess) {
                                            batteryInfo      = parsed
                                            folderAccessible = true
                                        }
                                    }
                                }
                            },
                            onReadRaw        = { entry ->
                                val uri = folderUri
                                if (uri != null) {
                                    scope.launch {
                                        rawLogText   = ""
                                        isLoadingRaw = true
                                        showLogSheet = false
                                        selectedLogInfo = BatteryInfo(
                                            healthPercent = null, cycleCount = null,
                                            logFileName = entry.name
                                        )
                                        navController.navigate(Routes.LOG_RAW)
                                        val text = withContext(Dispatchers.IO) {
                                            extractBatterySection(uri, entry, context)
                                        }
                                        rawLogText   = text
                                        isLoadingRaw = false
                                    }
                                }
                            }
                        )
                    }

                    composable(Routes.WRONG_FOLDER) {
                        val canGoBack = navController.previousBackStackEntry != null
                        WrongFolderScreen(
                            errorMessage = batteryInfo?.errorMessage ?: "",
                            canGoBack    = canGoBack,
                            onDismiss    = {
                                if (canGoBack) navController.popBackStack()
                                else navController.navigate(Routes.SETUP) {
                                    popUpTo(0) { inclusive = true }; launchSingleTop = true
                                }
                            },
                            isRefreshing = isRefreshing,
                            onRescan     = { rescan() }
                        )
                    }

                    composable(Routes.FOLDER_DELETED) {
                        FolderDeletedScreen(onDismiss = {
                            navController.popBackStack()
                        })
                    }

                    composable(Routes.PERMISSION_LOST) {
                        PermissionLostScreen(
                            onDismiss = {
                                navController.popBackStack()
                            }
                        )
                    }

                    composable(Routes.LOG_RAW) {
                        LogRawScreen(
                            fileName     = selectedLogInfo?.logFileName ?: "",
                            text         = rawLogText,
                            isLoading    = isLoadingRaw,
                            onBack       = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun RescanPullToRefreshBox(
        isRefreshing: Boolean,
        onRescan: () -> Unit,
        modifier: Modifier = Modifier,
        content: @Composable BoxScope.() -> Unit
    ) {
        val isDark    = isSystemInDarkTheme()
        val blueColor = if (isDark) AccentBlue else AccentBlueLight
        val state     = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = false,
            onRefresh    = onRescan,
            state        = state,
            modifier     = modifier,
            indicator    = {
                PullToRefreshDefaults.Indicator(
                    state        = state,
                    isRefreshing = false,
                    modifier     = Modifier.align(Alignment.TopCenter),
                    color        = blueColor
                )
            },
            content      = content
        )
    }

    @Composable
    fun WrongFolderScreen(
        errorMessage: String,
        canGoBack: Boolean,
        onDismiss: () -> Unit,
        isRefreshing: Boolean,
        onRescan: () -> Unit
    ) {
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        val alpha       by animateFloatAsState(if (shown) 1f else 0f, tween(350), label = "wf_fade")
        val isNotFound  = errorMessage == "NO_LOG_FOUND"
        val accentColor = if (isNotFound) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
        val green = accentGreenEffective(); val card = MaterialTheme.colorScheme.surfaceContainerLow
        val tp = textPrimary(); val ts = textSecondary(); val onPrimary = MaterialTheme.colorScheme.onPrimary

        RescanPullToRefreshBox(isRefreshing = isRefreshing, onRescan = onRescan, modifier = Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().alpha(alpha).verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(88.dp)
                        .background(accentColor.copy(alpha = 0.1f), CircleShape)
                        .border(1.5.dp, accentColor.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(painterResource(R.drawable.warning), null,
                        tint = accentColor, modifier = Modifier.size(40.dp))
                }
                Spacer(Modifier.height(28.dp))
                Text(
                    if (isNotFound) "Wrong Folder Selected" else "Log File Not Readable",
                    color = tp, textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    if (isNotFound)
                        "No dumpState_*.log file was found in that folder.\n\nMake sure you select the correct log folder after running dumpstate/logcat from *#9900#."
                    else
                        "The log file was found but could not be read.\n\nTry generating a fresh log and select the folder again.",
                    color = ts, textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp)
                )
                Spacer(Modifier.height(20.dp))
                ElevatedCard(
                    shape     = RoundedCornerShape(12.dp),
                    colors    = CardDefaults.elevatedCardColors(containerColor = card),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.info), null,
                            tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Expected path: /Device/log/",
                            color = ts, style = MaterialTheme.typography.labelMedium)
                    }
                }
                Spacer(Modifier.height(40.dp))
                Button(
                    onClick   = onDismiss,
                    modifier  = Modifier.fillMaxWidth().height(52.dp),
                    shape     = RoundedCornerShape(14.dp),
                    colors    = ButtonDefaults.buttonColors(containerColor = green, contentColor = onPrimary),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                ) {
                    Icon(painterResource(R.drawable.check_circle), null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Got it", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                }
                Spacer(Modifier.weight(1f))
            }
        }
    }

    @Composable
    fun FolderDeletedScreen(onDismiss: () -> Unit) {
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(350), label = "fd_fade")
        val green = accentGreenEffective(); val tp = textPrimary(); val ts = textSecondary()
        val onPrimary = MaterialTheme.colorScheme.onPrimary

        Column(
            Modifier.fillMaxSize().alpha(alpha).padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(88.dp)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f), CircleShape)
                    .border(1.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(painterResource(R.drawable.delete), null,
                    tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.height(28.dp))
            Text("Folder Not Found", textAlign = TextAlign.Center,
                color = tp, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            Text(
                "The log folder you previously selected has been deleted or is no longer accessible.\n\nYou'll need to generate a new log file.",
                color = ts, textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp)
            )
            Spacer(Modifier.height(40.dp))
            Button(
                onClick   = onDismiss,
                modifier  = Modifier.fillMaxWidth().height(52.dp),
                shape     = RoundedCornerShape(14.dp),
                colors    = ButtonDefaults.buttonColors(containerColor = green, contentColor = onPrimary),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                Icon(painterResource(R.drawable.check_circle), null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Got it", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            }
        }
    }

    @Composable
    fun PermissionLostScreen(onDismiss: () -> Unit) {
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(350), label = "pl_fade")
        val green = accentGreenEffective(); val tp = textPrimary(); val ts = textSecondary()
        val onPrimary = MaterialTheme.colorScheme.onPrimary

        Column(
            Modifier.fillMaxSize().alpha(alpha).padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(88.dp)
                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f), CircleShape)
                    .border(1.5.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(painterResource(R.drawable.warning), null,
                    tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.height(28.dp))
            Text("Permission Removed", textAlign = TextAlign.Center,
                color = tp, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            Text(
                "The folder permission was removed from Android settings.\n\nYou'll need to grant permission again to view your battery information.",
                color = ts, textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp)
            )
            Spacer(Modifier.height(40.dp))
            Button(
                onClick   = onDismiss,
                modifier  = Modifier.fillMaxWidth().height(52.dp),
                shape     = RoundedCornerShape(14.dp),
                colors    = ButtonDefaults.buttonColors(containerColor = green, contentColor = onPrimary),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                Icon(painterResource(R.drawable.check_circle), null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Got it", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            }
        }
    }

    data class SetupStep(val icon: Painter, val title: String, val desc: String)

    @Composable
    fun SetupScreen(
        hasLivePermission: Boolean,
        onPickFolder: () -> Unit,
        showBackButton: Boolean,
        onBack: () -> Unit,
        isRefreshing: Boolean,
        onRescan: () -> Unit
    ) {
        val steps = listOf(
            SetupStep(painterResource(R.drawable.phone), "Dial *#9900#",
                "Open the Phone app, type *#9900# and call. Samsung's SysDump menu opens."),
            SetupStep(painterResource(R.drawable.play_arrow), "Run dumpstate/logcat",
                "Pick option 2 — \"Run dumpstate/logcat\" and wait, it takes a while to create."),
            SetupStep(painterResource(R.drawable.check), "Copy to sdcard",
                "Tap \"Copy to sdcard\". Logs are saved to /Device/log/."),
            SetupStep(painterResource(R.drawable.search), "Grant folder permission",
                "Tap the button below, navigate to /Device/log/, and allow to read the folder.")
        )
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        val alpha     by animateFloatAsState(if (shown) 1f else 0f, tween(350), label = "setup_fade")
        val green     = accentGreenEffective(); val border = cardBorderColor()
        val tp        = textPrimary(); val ts = textSecondary()
        val onPrimary = MaterialTheme.colorScheme.onPrimary

        RescanPullToRefreshBox(isRefreshing = isRefreshing, onRescan = onRescan, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                Spacer(Modifier.height(56.dp))
                Box(Modifier.alpha(alpha)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(48.dp)
                                .background(Brush.radialGradient(listOf(green.copy(alpha = 0.28f), Color.Transparent)), CircleShape)
                                .border(1.5.dp, green.copy(alpha = 0.5f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(painterResource(R.drawable.battery), null,
                                tint = green, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("My Battery", color = tp, style = MaterialTheme.typography.titleLarge)
                            Text("Samsung log reader", color = ts, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Spacer(Modifier.height(40.dp))
                Box(Modifier.alpha(alpha)) {
                    Column {
                        Text("How to get your log", color = tp, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(20.dp))
                        steps.forEachIndexed { i, step ->
                            SetupStepRow(number = i + 1, step = step)
                            if (i < steps.lastIndex) Spacer(Modifier.height(12.dp))
                        }
                    }
                }
                Spacer(Modifier.height(36.dp))
                Box(Modifier.alpha(alpha)) {
                    Column {
                        Button(
                            onClick   = onPickFolder,
                            enabled   = !hasLivePermission,
                            modifier  = Modifier.fillMaxWidth().height(52.dp),
                            shape     = RoundedCornerShape(14.dp),
                            colors    = ButtonDefaults.buttonColors(
                                containerColor         = green,
                                contentColor           = onPrimary,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                disabledContentColor   = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                        ) {
                            Icon(
                                if (hasLivePermission) painterResource(R.drawable.check_circle)
                                else painterResource(R.drawable.search),
                                null, modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                if (hasLivePermission) "Permission Already Granted"
                                else "Grant Permission to Log Folder",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        AnimatedVisibility(
                            visible = showBackButton,
                            enter   = fadeIn(tween(250)),
                            exit    = fadeOut(tween(200))
                        ) {
                            Column {
                                Spacer(Modifier.height(12.dp))
                                OutlinedButton(
                                    onClick  = onBack,
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    shape    = RoundedCornerShape(14.dp),
                                    border   = BorderStroke(1.dp, border),
                                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = tp)
                                ) {
                                    Icon(painterResource(R.drawable.arrow_back), null,
                                        tint = ts, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Text("Back to Dashboard",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(36.dp))
            }
        }
    }

    @Composable
    fun SetupStepRow(number: Int, step: SetupStep) {
        val green  = accentGreenEffective()
        val card   = MaterialTheme.colorScheme.surfaceContainerLow
        val border = cardBorderColor()
        val tp     = textPrimary()
        val ts     = textSecondary()
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(40.dp).background(card, CircleShape).border(1.5.dp, border, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("$number", color = green,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f).padding(top = 4.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(step.icon, null,
                        tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(step.title, color = tp,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
                }
                Spacer(Modifier.height(4.dp))
                Text(step.desc, color = ts, style = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp))
            }
        }
    }

    // ─── Helper: relative time from timestamp ─────────────────────────────────
    private fun relativeTimeFrom(tsMs: Long): String {
        if (tsMs == 0L) return ""
        val diff  = System.currentTimeMillis() - tsMs
        val mins  = diff / 60_000
        val hours = diff / 3_600_000
        val days  = diff / 86_400_000
        return when {
            mins  < 1  -> "just now"
            mins  < 60 -> "$mins min${if (mins == 1L) "" else "s"} ago"
            hours < 24 -> "$hours hour${if (hours == 1L) "" else "s"} ago"
            days  < 7  -> "$days day${if (days == 1L) "" else "s"} ago"
            else       -> SimpleDateFormat("MMM dd, yyyy", Locale.US).format(java.util.Date(tsMs))
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun BatteryDashboard(
        info: BatteryInfo,
        folderAccessible: Boolean,
        isRefreshing: Boolean,
        isLoadingDetail: Boolean,
        arcAnimatable: Animatable<Float, AnimationVector1D>,
        gaugeAmplitude: Float,
        gaugeReplayKey: Int,
        onRescan: () -> Unit,
        onShowSteps: () -> Unit,
        onShowLogs: () -> Unit,
        showLogSheet: Boolean,
        allLogEntries: List<DocEntry>,
        onDismissSheet: () -> Unit,
        onSelectEntry: (DocEntry) -> Unit,
        onReadRaw: (DocEntry) -> Unit
    ) {
        // If we already have real data (e.g. returning from log-raw screen), start fully
        // visible so there's no re-animation and no gauge wave-jump on back navigation.
        val alreadyHasData = remember { info.readSuccess }
        var shown by remember { mutableStateOf(alreadyHasData) }
        LaunchedEffect(Unit) { shown = true }
        val headerAlpha by animateFloatAsState(if (shown) 1f else 0f, tween(400), label = "ha")
        val headerSlide by animateFloatAsState(
            if (shown) 0f else -18f, tween(400, easing = FastOutSlowInEasing), label = "hs")
        val gaugeAlpha  by animateFloatAsState(
            if (shown) 1f else 0f, tween(450, delayMillis = 140), label = "ga")
        val gaugeSlide  by animateFloatAsState(
            if (shown) 0f else 24f, tween(450, easing = FastOutSlowInEasing, delayMillis = 140), label = "gs")
        val cardAlpha   by animateFloatAsState(
            if (shown) 1f else 0f, tween(400, delayMillis = 280), label = "ca")
        val tp    = textPrimary()
        val muted = textMuted()
        val ts    = textSecondary()

        // Block UI interaction while loading a log detail
        Box(Modifier.fillMaxSize()) {
            RescanPullToRefreshBox(
                isRefreshing = isRefreshing,
                onRescan     = onRescan,
                modifier     = Modifier.fillMaxSize()
            ) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
                ) {
                    Spacer(Modifier.height(52.dp))
                    Box(Modifier.fillMaxWidth().alpha(headerAlpha).offset(y = headerSlide.dp)) {
                        FilledTonalIconButton(
                            onClick  = { if (!isLoadingDetail) onShowLogs() },
                            enabled  = !isLoadingDetail,
                            modifier = Modifier.align(Alignment.CenterStart).size(40.dp),
                            colors   = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor   = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Icon(painterResource(R.drawable.arrow_back), "Browse logs",
                                modifier = Modifier.size(20.dp).rotate(-90f))
                        }
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("My Battery", color = tp,
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                            Spacer(Modifier.height(4.dp))
                            if (isLoadingDetail) {
                                // Same elements, same style — just show "…" in muted so layout
                                // is pixel-identical to the real content, no jumping.
                                Text("…", color = muted.copy(alpha = 0.5f),
                                    style = MaterialTheme.typography.labelSmall)
                                Text("…", color = muted.copy(alpha = 0.4f),
                                    style = MaterialTheme.typography.labelSmall)
                                Spacer(Modifier.height(2.dp))
                                Text("…", color = muted.copy(alpha = 0.3f),
                                    style = MaterialTheme.typography.labelSmall)
                            } else {
                                if (info.logFileName.isNotEmpty()) {
                                    val displayFileName = run {
                                        val n = info.logFileName
                                        if (n.length <= 30) n
                                        else n.take(12) + "…" + n.takeLast(14)
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment     = Alignment.CenterVertically
                                    ) {
                                        Text(displayFileName, color = muted, textAlign = TextAlign.Center,
                                            style = MaterialTheme.typography.labelSmall)
                                        if (!folderAccessible) {
                                            Spacer(Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.surfaceContainerHighest
                                            ) {
                                                Text(
                                                    "cached",
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                                    color    = muted,
                                                    style    = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        }
                                    }
                                }
                                if (info.relativeDate.isNotEmpty())
                                    Text("Log created ${info.relativeDate}", color = muted,
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.labelSmall)
                                if (info.firstUseDateFormatted.isNotEmpty()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "Manufactured ${info.firstUseDateFormatted}",
                                        color = muted.copy(alpha = 0.7f),
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                        FilledTonalIconButton(
                            onClick  = { if (!isLoadingDetail) onShowSteps() },
                            enabled  = !isLoadingDetail,
                            modifier = Modifier.align(Alignment.CenterEnd).size(40.dp),
                            colors   = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor   = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Icon(painterResource(R.drawable.info), "How to update log",
                                modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                    val isNewBattery = !isLoadingDetail && !info.healthUnsupported &&
                            (info.healthPercent ?: 0) >= 100
                    Box(Modifier.alpha(gaugeAlpha).offset(y = gaugeSlide.dp)) {
                        HealthGaugeCard(info, isLoadingDetail, arcAnimatable, gaugeAmplitude, gaugeReplayKey)
                    }
                    Spacer(Modifier.height(16.dp))
                    Box(Modifier.alpha(cardAlpha)) { CycleCountCard(info, isLoadingDetail, isNewBattery) }
                    Spacer(Modifier.height(32.dp))
                }
            }

            // Invisible interaction-blocking overlay while loading detail
            if (isLoadingDetail) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            onClick = { /* swallow all taps */ },
                            indication = null,
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        )
                )
            }

            // ── Log picker tray ───────────────────────────────────────────────
            if (showLogSheet && !isLoadingDetail) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            onClick           = onDismissSheet,
                            indication        = null,
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        )
                )
            }

            AnimatedVisibility(
                visible = showLogSheet && !isLoadingDetail,
                enter   = slideInVertically(
                    animationSpec = tween(320, easing = FastOutSlowInEasing),
                    initialOffsetY = { -it }
                ) + fadeIn(tween(220, easing = FastOutSlowInEasing)),
                exit    = slideOutVertically(
                    animationSpec = tween(240, easing = FastOutLinearInEasing),
                    targetOffsetY = { -it }
                ) + fadeOut(tween(180, easing = FastOutLinearInEasing))
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            onClick           = { },
                            indication        = null,
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        ),
                    color           = MaterialTheme.colorScheme.surfaceContainerLow,
                    shadowElevation = 8.dp,
                    shape           = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        // Header row — sits just below the status bar
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Log Files",
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${allLogEntries.size}",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 20.dp),
                            color    = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        val logsScrollState = rememberScrollState()
                        val logsScope = rememberCoroutineScope()
                        Box(Modifier.fillMaxWidth().heightIn(max = 200.dp)) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp)
                                    .verticalScroll(logsScrollState)
                                    .padding(end = 12.dp, top = 4.dp, bottom = 20.dp)
                            ) {
                                if (allLogEntries.isEmpty()) {
                                    Box(
                                        Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "No log files found",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                } else {
                                    allLogEntries.forEachIndexed { idx, entry ->
                                        val isActive   = entry.name == info.logFileName
                                        val tsFromName = extractTimestampFromFileName(entry.name)
                                        // Use lastModified as fallback if filename extraction fails
                                        val timestamp  = if (tsFromName != 0L) tsFromName else entry.lastModified
                                        val relTime    = relativeTimeFrom(timestamp)
                                        val green      = accentGreenEffective()
                                        val displayName = run {
                                            val n = entry.name
                                            if (n.length <= 32) n else n.take(14) + "…" + n.takeLast(14)
                                        }

                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(start = 20.dp, end = 20.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Active indicator
                                            Box(
                                                Modifier.size(6.dp).background(
                                                    if (isActive) green else Color.Transparent,
                                                    CircleShape
                                                )
                                            )
                                            Spacer(Modifier.width(12.dp))

                                            // Main tap area — disabled for currently active entry
                                            Surface(
                                                onClick  = { if (!isActive) onSelectEntry(entry) },
                                                enabled  = !isActive,
                                                modifier = Modifier.weight(1f),
                                                color    = Color.Transparent,
                                                shape    = RoundedCornerShape(8.dp)
                                            ) {
                                                Row(
                                                    Modifier.padding(vertical = 13.dp, horizontal = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    // Filename
                                                    Text(
                                                        displayName,
                                                        color    = if (isActive) green
                                                        else MaterialTheme.colorScheme.onSurface,
                                                        style    = MaterialTheme.typography.bodySmall.copy(
                                                            fontWeight = if (isActive) FontWeight.SemiBold
                                                            else FontWeight.Normal
                                                        ),
                                                        maxLines = 1,
                                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f, fill = false)
                                                    )
                                                    // Relative time — always visible beside filename in brackets
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(
                                                        if (relTime.isNotEmpty()) "($relTime)" else "",
                                                        color = if (isActive) green.copy(alpha = 0.75f)
                                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        maxLines = 1,
                                                        softWrap = false
                                                    )
                                                }
                                            }

                                            // Raw-view button
                                            Surface(
                                                onClick  = { onReadRaw(entry) },
                                                modifier = Modifier.size(40.dp),
                                                shape    = RoundedCornerShape(10.dp),
                                                color    = MaterialTheme.colorScheme.surfaceContainerHighest
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        painterResource(R.drawable.arrow_forward_ios),
                                                        contentDescription = "View raw",
                                                        tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                }
                                            }
                                        }

                                        if (idx < allLogEntries.lastIndex) {
                                            HorizontalDivider(
                                                color    = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                                modifier = Modifier.padding(start = 38.dp, end = 16.dp)
                                            )
                                        }
                                    }
                                }
                            } // end Column (logs list)

                            // ── Draggable scroll indicator ────────────────────────
                            if (logsScrollState.maxValue > 0) {
                                val trackHeight = 200.dp
                                val thumbMinHeight = 32.dp
                                val density = LocalDensity.current
                                val trackHeightPx = with(density) { trackHeight.toPx() }
                                val thumbMinHeightPx = with(density) { thumbMinHeight.toPx() }
                                val fraction = (logsScrollState.value.toFloat() / logsScrollState.maxValue.toFloat()).coerceIn(0f, 1f)
                                val thumbHeightPx = (trackHeightPx * 0.35f).coerceAtLeast(thumbMinHeightPx)
                                val thumbTopPx = fraction * (trackHeightPx - thumbHeightPx)
                                val isDarkTheme = isSystemInDarkTheme()
                                val thumbColor = if (isDarkTheme) Color(0xFF4A6080) else Color(0xFFB0C0D8)
                                val trackColor = if (isDarkTheme) Color(0xFF1A2740) else Color(0xFFE2E8F0)

                                Box(
                                    Modifier
                                        .align(Alignment.CenterEnd)
                                        .padding(end = 4.dp)
                                        .width(4.dp)
                                        .height(trackHeight)
                                        .background(trackColor, RoundedCornerShape(2.dp))
                                        .pointerInput(Unit) {
                                            detectDragGestures { change, dragAmount ->
                                                change.consume()
                                                val draggableRange = trackHeightPx - thumbHeightPx
                                                if (draggableRange > 0f) {
                                                    val delta = (dragAmount.y / draggableRange) * logsScrollState.maxValue.toFloat()
                                                    logsScope.launch { logsScrollState.scrollBy(delta) }
                                                }
                                            }
                                        }
                                ) {
                                    Box(
                                        Modifier
                                            .offset(y = with(density) { thumbTopPx.toDp() })
                                            .width(4.dp)
                                            .height(with(density) { thumbHeightPx.toDp() })
                                            .background(thumbColor, RoundedCornerShape(2.dp))
                                    )
                                }
                            }
                        } // end Box (scroll indicator container)
                    }
                }
            }
        }
    }

    @Composable
    fun HealthGaugeCard(
        info: BatteryInfo,
        isLoadingDetail: Boolean = false,
        arcAnimatable: Animatable<Float, AnimationVector1D>,
        amplitude: Float,
        gaugeReplayKey: Int = 0
    ) {
        val isUnsupported = info.healthUnsupported
        val targetPct = if (isUnsupported) 100f
        else (info.healthPercent ?: 0).coerceIn(0, 100).toFloat()

        LaunchedEffect(isLoadingDetail, targetPct, gaugeReplayKey) {
            if (isLoadingDetail) {
                // Animate to full circle from wherever we currently are — no snap, no wave jump
                arcAnimatable.animateTo(
                    targetValue   = 1f,
                    animationSpec = tween(400, easing = FastOutSlowInEasing)
                )
            } else {
                // Only snap to 0 on first load (when we're already at 0 or very close)
                // For subsequent log loads the arc is at 1f (full); animate directly to target
                // so the wave position is preserved with no jump.
                if (arcAnimatable.value < 0.02f) {
                    // Fresh start — already near 0, just animate up
                } else {
                    // Returning from loading-full state; snap only if target < current
                    // so the arc contracts smoothly rather than jumping backwards.
                    arcAnimatable.snapTo(0f)
                }
                arcAnimatable.animateTo(
                    targetValue   = targetPct / 100f,
                    animationSpec = tween(1400, easing = FastOutSlowInEasing)
                )
            }
        }

        val animPct = arcAnimatable.value * 100f

        // isLoadingDetail must come before isUnsupported so switching away from an
        // unsupported log immediately shows loading state rather than staying gray.
        // pillMsg is the status pill text — same conditions, one place.
        val isNewBattery = !isLoadingDetail && !isUnsupported && targetPct >= 100f
        data class GS(val color: Color, val label: String, val pill: String)
        val gs = when {
            isLoadingDetail && animPct >= 99.9f -> GS(gaugeBlue(),   "Loading",     "Scanning log…")
            isLoadingDetail                     -> GS(gaugeGreen(),  "Loading",     "Scanning log…")
            isUnsupported                       -> GS(gaugeGray(),   "Unsupported", "Device unsupported")
            animPct >= 99.9f                    -> GS(gaugeBlue(),   "New",         "The phone is as fresh as it gets")
            animPct >= 80f                      -> GS(gaugeGreen(),  "Good",        "Battery is in great condition")
            animPct >= 60f                      -> GS(gaugeOrange(), "Fair",        "Battery capacity is declining")
            else                                -> GS(gaugeRed(),    "Poor",        "Battery replacement recommended")
        }
        // Animate color so the green→blue crossover at animPct≥99.9 is a smooth transition
        // rather than an instant parameter change. An instantaneous fillColor change causes
        // CircularWavyProgressIndicator to restart its wave phase internally.
        val gaugeColor   by animateColorAsState(gs.color, tween(300), label = "gauge_color")
        val currentLabel = gs.label
        val pillMsg      = gs.pill

        val muted  = textMuted()
        val border = cardBorderColor()
        val ts     = textSecondary()

        ElevatedCard(
            modifier  = Modifier.fillMaxWidth(),
            shape     = RoundedCornerShape(24.dp),
            colors    = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                Modifier.fillMaxWidth().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "BATTERY HEALTH",
                        color = muted,
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp)
                    )
                    // BSOH pill — Box+background instead of Surface so no M3 min-height is enforced.
                    // Zero vertical padding: the text itself defines the height, matching bare labelSmall.
                    if (!isLoadingDetail && !isUnsupported && info.healthSource == "bsoh") {
                        Text(
                            "BSOH",
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .background(
                                    MaterialTheme.colorScheme.secondaryContainer,
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 5.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp)
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                Box(Modifier.size(190.dp), contentAlignment = Alignment.Center) {
                    // Single ArcGaugePulsing — progress and amplitude both change via
                    // animateFloatAsState so the underlying CircularWavyProgressIndicator
                    // is NEVER recreated, keeping waves perfectly continuous.
                    ArcGaugePulsing(
                        progress   = animPct / 100f,
                        amplitude  = amplitude,
                        trackColor = border,
                        fillColor  = gaugeColor,
                        modifier   = Modifier.fillMaxSize()
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (isLoadingDetail || isUnsupported) "—"
                            else "${animPct.toInt()}%",
                            color = gaugeColor,
                            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            currentLabel,
                            color = gaugeColor.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Status pill — unified: uses gaugeColor + pillMsg from the single when block above.
                // Always rendered (no if/else on loading) so card height is stable.
                Surface(
                    shape  = RoundedCornerShape(10.dp),
                    color  = gaugeColor.copy(alpha = if (isUnsupported) 0.0f else 0.1f),
                    border = BorderStroke(1.dp, gaugeColor.copy(alpha = if (isUnsupported) 0.4f else 0.25f))
                ) {
                    Text(
                        pillMsg,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color    = gaugeColor,
                        style    = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }

    @Composable
    fun ArcGaugePulsing(
        progress: Float = 1f,
        amplitude: Float,
        trackColor: Color,
        fillColor: Color,
        modifier: Modifier = Modifier
    ) {
        // fillColor is now kept stable (blue) throughout all loading and fill transitions,
        // so no color cross-fade is needed — pass it directly to avoid any internal reset.
        Box(modifier = modifier.scale(scaleX = -1f, scaleY = 1f), contentAlignment = Alignment.Center) {
            CircularWavyProgressIndicator(
                progress    = { progress },
                modifier    = Modifier.fillMaxSize(),
                color       = fillColor,
                trackColor  = trackColor,
                stroke      = WavyProgressIndicatorDefaults.circularIndicatorStroke,
                trackStroke = WavyProgressIndicatorDefaults.circularTrackStroke,
                amplitude   = { amplitude },
                wavelength  = WavyProgressIndicatorDefaults.CircularWavelength,
                waveSpeed   = WavyProgressIndicatorDefaults.CircularWavelength
            )
        }
    }

    @Composable
    fun CycleCountCard(info: BatteryInfo, isLoadingDetail: Boolean = false, isNewBattery: Boolean = false) {
        val cycles        = info.cycleCount ?: 0
        val isUnsupported = info.cycleCount == null
        val maxBar = 800f

        var animStarted by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { delay(200); animStarted = true }
        val animatedCycles by animateFloatAsState(
            if (animStarted && !isLoadingDetail) cycles.toFloat() else 0f,
            tween(1200, easing = FastOutSlowInEasing), label = "cycles_count"
        )
        val animatedBarFraction by animateFloatAsState(
            if (animStarted && !isLoadingDetail) (cycles.toFloat() / maxBar).coerceIn(0f, 1f) else 0f,
            tween(1200, easing = FastOutSlowInEasing), label = "cycles_bar"
        )

        val muted   = textMuted()
        val onSurf  = MaterialTheme.colorScheme.onSurface
        val onSurfV = MaterialTheme.colorScheme.onSurfaceVariant
        val ts      = textSecondary()
        val border  = cardBorderColor()

        val (statusColor, statusText) = when {
            isLoadingDetail              -> Pair(gaugeBlue(),   "Loading")
            isNewBattery                 -> Pair(gaugeBlue(),   "No wear")
            isUnsupported                -> Pair(MaterialTheme.colorScheme.outlineVariant, "Unavailable")
            cycles < 300                 -> Pair(gaugeGreen(),  "Low wear")
            cycles < 500                 -> Pair(gaugeOrange(), "Moderate wear")
            else                         -> Pair(gaugeRed(),    "High wear")
        }

        ElevatedCard(
            modifier  = Modifier.fillMaxWidth(),
            shape     = RoundedCornerShape(20.dp),
            colors    = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape    = RoundedCornerShape(12.dp),
                        color    = if (isUnsupported && !isLoadingDetail) MaterialTheme.colorScheme.surfaceVariant
                        else statusColor.copy(alpha = 0.15f),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(painterResource(R.drawable.refresh), null,
                                tint     = if (isUnsupported && !isLoadingDetail) MaterialTheme.colorScheme.outlineVariant
                                else statusColor,
                                modifier = Modifier.size(22.dp))
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("CHARGE CYCLES",
                            color = muted,
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp))
                        Text(
                            if (isUnsupported && !isLoadingDetail) "Not reported by device" else "Full charge equivalents",
                            color = onSurfV,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            if (isUnsupported && !isLoadingDetail) "—"
                            else if (isLoadingDetail) "—"
                            else animatedCycles.toInt().toString(),
                            color = if (isUnsupported && !isLoadingDetail) MaterialTheme.colorScheme.outlineVariant
                            else statusColor,
                            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            "cycles",
                            color = if (isUnsupported && !isLoadingDetail) MaterialTheme.colorScheme.outlineVariant
                            else onSurfV,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Surface(
                        shape  = RoundedCornerShape(12.dp),
                        color  = statusColor.copy(alpha = 0.12f),
                        border = BorderStroke(1.5.dp, statusColor.copy(alpha = 0.35f))
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(8.dp).background(statusColor, CircleShape))
                            Spacer(Modifier.width(8.dp))
                            Text(statusText, color = statusColor,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }

                // Bar always shown — grayed at zero when unsupported or loading
                Spacer(Modifier.height(18.dp))
                Box(
                    Modifier.fillMaxWidth().height(6.dp)
                        .background(border, RoundedCornerShape(3.dp))
                ) {
                    if (!isUnsupported) {
                        Box(
                            Modifier.fillMaxWidth(animatedBarFraction).fillMaxHeight()
                                .background(
                                    Brush.horizontalGradient(listOf(statusColor.copy(alpha = 0.7f), statusColor)),
                                    RoundedCornerShape(3.dp)
                                )
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("0", color = muted, style = MaterialTheme.typography.labelSmall)
                    Text("300", color = if (!isUnsupported && cycles >= 300) gaugeGreen() else muted,
                        style = MaterialTheme.typography.labelSmall)
                    Text("500", color = if (!isUnsupported && cycles >= 500) gaugeOrange() else muted,
                        style = MaterialTheme.typography.labelSmall)
                    Text("800+", color = if (!isUnsupported && cycles >= 800) gaugeRed() else muted,
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }

    // ─── Log raw screen ───────────────────────────────────────────────────────

    /**
     * Builds an AnnotatedString from raw log text, coloring values (after ": " or "= ")
     * more visibly than the key text.
     */
    @Composable
    private fun buildColorizedLogText(
        text: String,
        keyColor: Color,
        valueColor: Color,
        sectionColor: Color
    ): AnnotatedString = buildAnnotatedString {
        val sectionHeaderRegex = Regex("""^━━━ .+ ━━━$""")
        val kvRegex            = Regex("""^(\s*)(.+?)(\s*[:=]\s*)(.+)$""")

        text.lines().forEach { line ->
            when {
                sectionHeaderRegex.matches(line.trim()) -> {
                    withStyle(SpanStyle(color = sectionColor, fontWeight = FontWeight.SemiBold)) {
                        append(line)
                    }
                }
                line.trim().isEmpty() -> append(line)
                else -> {
                    val match = kvRegex.matchEntire(line)
                    if (match != null) {
                        // indent
                        withStyle(SpanStyle(color = keyColor)) { append(match.groupValues[1]) }
                        // key
                        withStyle(SpanStyle(color = keyColor)) { append(match.groupValues[2]) }
                        // separator (: or =)
                        withStyle(SpanStyle(color = keyColor.copy(alpha = 0.6f))) { append(match.groupValues[3]) }
                        // value — brighter / more visible
                        withStyle(SpanStyle(color = valueColor, fontWeight = FontWeight.Medium)) {
                            append(match.groupValues[4])
                        }
                    } else {
                        withStyle(SpanStyle(color = keyColor)) { append(line) }
                    }
                }
            }
            append('\n')
        }
    }

    @Composable
    fun LogRawScreen(
        fileName: String,
        text: String,
        isLoading: Boolean,
        onBack: () -> Unit
    ) {
        val tp     = textPrimary()
        val ts     = textSecondary()
        val muted  = textMuted()
        val border = cardBorderColor()
        val green  = accentGreenEffective()
        val isDark = isSystemInDarkTheme()

        // Key text: muted; Value text: clearly visible
        val keyColor     = if (isDark) Color(0xFF7A9CC0) else Color(0xFF4A6080)
        val valueColor   = if (isDark) Color(0xFFF0F4FF) else Color(0xFF0F172A)
        val sectionColor = if (isDark) Color(0xFF6BB5FF) else Color(0xFF1565C0)

        var backHandled by remember { mutableStateOf(false) }
        BackHandler(enabled = true) {
            if (!backHandled) { backHandled = true; onBack() }
        }

        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(300), label = "raw_fade")

        // Search state
        var searchQuery by remember { mutableStateOf("") }
        var searchActive by remember { mutableStateOf(false) }
        val searchFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
        LaunchedEffect(searchActive) {
            if (searchActive) searchFocusRequester.requestFocus()
        }

        // Highlight color for matches
        val highlightBg = if (isDark) Color(0xFF3A5030) else Color(0xFFD4EDD4)
        val highlightFg = if (isDark) Color(0xFF90EE90) else Color(0xFF1B5E20)

        Column(Modifier.fillMaxSize().alpha(alpha)) {
            // ── Top bar ──────────────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp)
                    .padding(top = 52.dp)
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { if (!backHandled) { backHandled = true; onBack() } }) {
                    Icon(painterResource(R.drawable.arrow_back), "Back", tint = ts)
                }
                if (searchActive) {
                    // Inline search field
                    androidx.compose.material3.TextField(
                        value         = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier      = Modifier.weight(1f).focusRequester(searchFocusRequester),
                        placeholder   = { Text("Search…", style = MaterialTheme.typography.bodyMedium) },
                        singleLine    = true,
                        colors        = TextFieldDefaults.colors(
                            focusedContainerColor   = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor   = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            color      = tp
                        )
                    )
                    // Yellow occurrence count badge
                    if (searchQuery.isNotBlank()) {
                        val matchCount = remember(searchQuery, text) {
                            if (searchQuery.isBlank()) 0
                            else {
                                var count = 0
                                var idx = 0
                                val lower = text.lowercase()
                                val lowerQ = searchQuery.lowercase()
                                while (true) {
                                    val found = lower.indexOf(lowerQ, idx)
                                    if (found < 0) break
                                    count++
                                    idx = found + lowerQ.length
                                }
                                count
                            }
                        }
                        Box(
                            Modifier
                                .background(Color(0xFFFFCC00), RoundedCornerShape(10.dp))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text  = "$matchCount",
                                color = Color(0xFF1A1200),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                    IconButton(onClick = { searchQuery = ""; searchActive = false }) {
                        Icon(painterResource(R.drawable.close), "Clear search", tint = ts)
                    }
                } else {
                    Column(Modifier.weight(1f)) {
                        Text("Battery Lines", color = tp,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                        if (fileName.isNotEmpty()) {
                            Text(fileName, color = muted,
                                style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                    IconButton(onClick = { searchActive = true }) {
                        Icon(painterResource(R.drawable.search), "Search", tint = ts)
                    }
                }
            }
            HorizontalDivider(color = border, thickness = 0.5.dp)

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LoadingIndicator(
                            modifier = Modifier.size(48.dp),
                            color    = green,
                            polygons = LoadingIndicatorDefaults.IndeterminateIndicatorPolygons
                        )
                        Spacer(Modifier.height(14.dp))
                        Text("Extracting battery lines…", color = ts,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                // Selectable, colorized log text (with optional search highlighting)
                SelectionContainer {
                    Column(
                        Modifier.fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                    ) {
                        val colorizedText = if (searchQuery.isNotBlank()) {
                            buildSearchHighlightedText(
                                base        = buildColorizedLogText(text, keyColor, valueColor, sectionColor),
                                query       = searchQuery,
                                highlightBg = highlightBg,
                                highlightFg = highlightFg
                            )
                        } else {
                            buildColorizedLogText(text, keyColor, valueColor, sectionColor)
                        }
                        Text(
                            text  = colorizedText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize   = 11.sp,
                                lineHeight = 17.sp
                            )
                        )
                        Spacer(Modifier.height(32.dp))
                    }
                }
            }
        }
    }

    /**
     * Takes a pre-colorized AnnotatedString and overlays highlight spans for all
     * case-insensitive occurrences of [query].
     */
    @Composable
    private fun buildSearchHighlightedText(
        base: AnnotatedString,
        query: String,
        highlightBg: Color,
        highlightFg: Color
    ): AnnotatedString {
        if (query.isBlank()) return base
        val rawText  = base.text
        val lowerRaw = rawText.lowercase()
        val lowerQ   = query.lowercase()
        if (!lowerRaw.contains(lowerQ)) return base

        return buildAnnotatedString {
            append(base)
            var searchStart = 0
            while (true) {
                val idx = lowerRaw.indexOf(lowerQ, searchStart)
                if (idx < 0) break
                addStyle(
                    SpanStyle(background = highlightBg, color = highlightFg),
                    start = idx,
                    end   = idx + query.length
                )
                searchStart = idx + query.length
            }
        }
    }
}