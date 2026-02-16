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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    const val SCANNING        = "scanning"
    const val DASHBOARD       = "dashboard"
    const val WRONG_FOLDER    = "wrong_folder"
    const val FOLDER_DELETED  = "folder_deleted"
    const val PERMISSION_LOST = "permission_lost"
}

private val AccentGreen  = Color(0xFF00E5A0)
private val AccentOrange = Color(0xFFFFAA44)
private val AccentRed    = Color(0xFFFF5C6C)

private val DarkBgDeep        = Color(0xFF080D18)
private val DarkBgCard        = Color(0xFF111827)
private val DarkBgCardBorder  = Color(0xFF1F2D42)
private val DarkTextPrimary   = Color(0xFFE8EEFF)
private val DarkTextSecondary = Color(0xFF7A8BA8)
private val DarkTextMuted     = Color(0xFF334155)

private val LightBgDeep        = Color(0xFFF1F5F9)
private val LightBgCard        = Color(0xFFFFFFFF)
private val LightBgCardBorder  = Color(0xFFE2E8F0)
private val LightTextPrimary   = Color(0xFF0F172A)
private val LightTextSecondary = Color(0xFF475569)
private val LightTextMuted     = Color(0xFF94A3B8)

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
        val cycleCount: Int?,
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
        // Filename is YYYYMMDDHHMMSS — lexicographic order == chronological order.
        // No parsing, no lastModified (copy-to-sdcard resets it anyway).
        return allLogs.maxByOrNull { it.name }
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
        var bsoh: Int?        = null
        var usage: Int?       = null
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
                    asoc     = extractValueOrNull(t, "mSavedBatteryAsoc:")
                }
                if (t.contains("mSavedBatteryBsoh:")) {
                    bsoh = extractValueOrNull(t, "mSavedBatteryBsoh:")
                }

                if (t.contains("mSavedBatteryUsage:")) {
                    val raw = extractValueOrNull(t, "mSavedBatteryUsage:")
                    usage = if (raw != null) raw / 1000 else null
                }

                if (firstUseDateMs == 0L) {
                    llbRegex.find(t)?.let { m ->
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
            firstUseDateMs    = firstUseDateMs,
            logFileName       = fileName,
            logTimestampMs    = logTimestampMs,
            readSuccess       = readSuccess
        )
    }

    private fun extractTimestampFromFileName(name: String): Long {
        // Matches both:  20260215_062158  /  20260215-062158  /  20260215062158
        val compact = Regex("""(\d{8})[_-](\d{6})|(\d{8})(\d{6})""").find(name) ?: return 0L
        val date = if (compact.groupValues[1].isNotEmpty()) compact.groupValues[1] else compact.groupValues[3]
        val time = if (compact.groupValues[2].isNotEmpty()) compact.groupValues[2] else compact.groupValues[4]
        val str = "${date.substring(0, 4)}-${date.substring(4, 6)}-${date.substring(6, 8)}" +
                " ${time.substring(0, 2)}:${time.substring(2, 4)}:${time.substring(4, 6)}"
        return try {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(str)?.time ?: 0L
        } catch (_: Exception) { 0L }
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

    @Composable
    fun MyBatteryApp() {
        val context = LocalContext.current
        val prefs   = context.getSharedPreferences("battery_prefs", Context.MODE_PRIVATE)

        val savedUriStr    = prefs.getString("folder_uri", null)
        val savedUri       = savedUriStr?.toUri()
        var alreadyHasPerm by remember { mutableStateOf(savedUri != null && hasPersistedPermission(context, savedUri)) }

        var folderUri                  by remember { mutableStateOf(savedUri) }
        var batteryInfo                by remember { mutableStateOf<BatteryInfo?>(null) }
        // false = folder gone / permission lost → show "cached" badge in dashboard
        var folderAccessible           by remember { mutableStateOf(true) }
        var hasEverScannedSuccessfully by remember { mutableStateOf(false) }
        var isRefreshing               by remember { mutableStateOf(false) }

        val navController = rememberNavController()
        val scope         = rememberCoroutineScope()

        val startDestination = Routes.SCANNING

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
                folderUri      = uri
                alreadyHasPerm = true
                batteryInfo    = null
                navController.navigate(Routes.SCANNING) {
                    popUpTo(0) { inclusive = true }; launchSingleTop = true
                }
            }
        }

        fun rescan() {
            if (folderUri == null || isRefreshing) return
            scope.launch {
                isRefreshing = true
                val uri = folderUri!!

                if (!hasPersistedPermission(context, uri)) {
                    isRefreshing = false
                    navController.navigate(Routes.PERMISSION_LOST) { launchSingleTop = true }
                    return@launch
                }

                val latestEntry = withContext(Dispatchers.IO) { findLatestLogEntry(uri, context) }

                if (latestEntry == null) {
                    isRefreshing = false
                    navController.navigate(Routes.FOLDER_DELETED) { launchSingleTop = true }
                    return@launch
                }

                val cached = loadCachedBatteryInfo(prefs)
                if (cached != null && cached.logFileName == latestEntry.name) {
                    isRefreshing = false
                    return@launch
                }

                val info = withContext(Dispatchers.IO) { parseLatestLog(uri, context, prefs) }
                isRefreshing = false
                if (info.readSuccess) {
                    batteryInfo = info
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
                    navController    = navController,
                    startDestination = startDestination,
                    enterTransition  = { fadeIn(tween(350)) },
                    exitTransition   = { fadeOut(tween(250)) }
                ) {
                    composable(Routes.SCANNING) {
                        LaunchedEffect(Unit) {
                            val cached = loadCachedBatteryInfo(prefs)
                            if (cached != null) {
                                val uri           = folderUri
                                val hasPerm       = uri != null && hasPersistedPermission(context, uri)
                                // Only list directory — no heavy file I/O yet
                                val latestEntry   = if (hasPerm)
                                    withContext(Dispatchers.IO) { findLatestLogEntry(uri!!, context) }
                                else null
                                // Compare purely by filename to decide if there's a newer log
                                val hasNewer      = latestEntry != null && latestEntry.name != cached.logFileName
                                val liveInfo: BatteryInfo? = if (hasNewer)
                                    withContext(Dispatchers.IO) { parseLatestLog(uri!!, context, prefs) }
                                else null
                                val usedLive      = liveInfo != null && liveInfo.readSuccess
                                batteryInfo       = if (usedLive) liveInfo else cached
                                // "cached" badge: only when folder is completely inaccessible.
                                // If folder is reachable (even same file), badge is hidden.
                                folderAccessible  = hasPerm && latestEntry != null
                                hasEverScannedSuccessfully = true
                                navController.navigate(Routes.DASHBOARD) {
                                    popUpTo(Routes.SCANNING) { inclusive = true }
                                }
                                return@LaunchedEffect
                            }

                            val uri = folderUri
                            if (uri == null) {
                                navController.navigate(Routes.SETUP) {
                                    popUpTo(Routes.SCANNING) { inclusive = true }
                                }
                                return@LaunchedEffect
                            }
                            if (!hasPersistedPermission(context, uri)) {
                                navController.navigate(Routes.PERMISSION_LOST) {
                                    popUpTo(Routes.SCANNING) { inclusive = true }
                                }
                                return@LaunchedEffect
                            }
                            val info = withContext(Dispatchers.IO) { smartScan(uri, context, prefs) }
                            batteryInfo      = info
                            folderAccessible = true
                            if (info.readSuccess) {
                                hasEverScannedSuccessfully = true
                                navController.navigate(Routes.DASHBOARD) {
                                    popUpTo(Routes.SCANNING) { inclusive = true }
                                }
                            } else {
                                navController.navigate(Routes.WRONG_FOLDER) {
                                    popUpTo(Routes.SCANNING) { inclusive = true }
                                }
                            }
                        }
                        ScanningScreen()
                    }

                    composable(Routes.SETUP) {
                        // Guard against rapid double-tap: once a pop is in flight, swallow
                        // any further back gestures until the destination has settled.
                        var backHandled by remember { mutableStateOf(false) }
                        BackHandler(enabled = true) {
                            if (!backHandled && navController.previousBackStackEntry != null) {
                                backHandled = true
                                navController.popBackStack()
                            }
                            // else: swallow silently — no back stack to pop or already popping
                        }
                        val showBackButton = navController.previousBackStackEntry != null
                        SetupScreen(
                            hasLivePermission = alreadyHasPerm,
                            onPickFolder   = { folderPicker.launch(null) },
                            showBackButton = showBackButton,
                            onBack         = {
                                if (!backHandled) {
                                    backHandled = true
                                    navController.popBackStack()
                                }
                            },
                            isRefreshing   = isRefreshing,
                            onRescan       = { rescan() }
                        )
                    }

                    composable(Routes.DASHBOARD) {
                        val info = batteryInfo
                        if (info != null) {
                            BatteryDashboard(
                                info             = info,
                                folderAccessible = folderAccessible,
                                isRefreshing     = isRefreshing,
                                onRescan         = { rescan() },
                                onShowSteps      = { navController.navigate(Routes.SETUP) }
                            )
                        } else {
                            LaunchedEffect(Unit) {
                                navController.navigate(Routes.SCANNING) {
                                    popUpTo(Routes.DASHBOARD) { inclusive = true }
                                }
                            }
                        }
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
                            folderAccessible = false
                            navController.popBackStack()
                        })
                    }

                    composable(Routes.PERMISSION_LOST) {
                        PermissionLostScreen(
                            onDismiss = {
                                alreadyHasPerm   = false
                                folderAccessible = false
                                navController.popBackStack()
                            }
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
        val green = accentGreenEffective()
        val state = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh    = onRescan,
            state        = state,
            modifier     = modifier,
            indicator    = {
                PullToRefreshDefaults.Indicator(
                    state          = state,
                    isRefreshing   = isRefreshing,
                    modifier       = Modifier.align(Alignment.TopCenter),
                    color          = green,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            },
            content = content
        )
    }

    @Composable
    fun ScanningScreen() {
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(350), label = "scan_fade")
        val green = accentGreenEffective(); val tp = textPrimary(); val ts = textSecondary()
        Box(Modifier.fillMaxSize().alpha(alpha), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LoadingIndicator(
                    modifier = Modifier.size(56.dp), color = green,
                    polygons = LoadingIndicatorDefaults.IndeterminateIndicatorPolygons
                )
                Spacer(Modifier.height(28.dp))
                Text("Scanning log files", color = tp, fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text("Looking for dumpState_*.log", color = ts, fontSize = 13.sp,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
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
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                disabledContentColor   = ts
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
                        Spacer(Modifier.height(16.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Icon(painterResource(R.drawable.refresh), null,
                                tint = ts.copy(alpha = 0.6f), modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Pull down anywhere to rescan",
                                color = ts.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.labelSmall)
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

    @Composable
    fun BatteryDashboard(
        info: BatteryInfo,
        folderAccessible: Boolean,
        isRefreshing: Boolean,
        onRescan: () -> Unit,
        onShowSteps: () -> Unit
    ) {
        var shown by remember { mutableStateOf(false) }
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

        RescanPullToRefreshBox(isRefreshing = isRefreshing, onRescan = onRescan, modifier = Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
            ) {
                Spacer(Modifier.height(52.dp))
                Box(Modifier.fillMaxWidth().alpha(headerAlpha).offset(y = headerSlide.dp)) {
                    Column(
                        Modifier.fillMaxWidth().padding(end = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("My Battery", color = tp,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                        if (info.logFileName.isNotEmpty() || info.relativeDate.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            if (info.logFileName.isNotEmpty()) {
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment     = Alignment.CenterVertically
                                ) {
                                    Text(info.logFileName, color = muted, textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.labelSmall)
                                    if (!folderAccessible) {
                                        Spacer(Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant
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
                        }
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
                    FilledTonalIconButton(
                        onClick  = onShowSteps,
                        modifier = Modifier.align(Alignment.CenterEnd).size(40.dp),
                        colors   = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor   = ts
                        )
                    ) {
                        Icon(painterResource(R.drawable.info), "How to update log",
                            modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.height(28.dp))
                Box(Modifier.alpha(gaugeAlpha).offset(y = gaugeSlide.dp)) { HealthGaugeCard(info) }
                Spacer(Modifier.height(16.dp))
                Box(Modifier.alpha(cardAlpha)) { CycleCountCard(info) }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    @Composable
    fun HealthGaugeCard(info: BatteryInfo) {
        val isUnsupported = info.healthUnsupported
        val targetPct = if (isUnsupported) 100f else (info.healthPercent ?: 0).coerceIn(0, 100).toFloat()

        var animStarted by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { delay(100); animStarted = true }
        val animPct by animateFloatAsState(
            targetValue   = if (animStarted) targetPct else 100f,
            animationSpec = tween(1400, easing = FastOutSlowInEasing),
            label         = "health_arc"
        )

        val currentColor = when {
            isUnsupported -> MaterialTheme.colorScheme.outlineVariant
            animPct >= 80 -> AccentGreen
            animPct >= 60 -> AccentOrange
            else          -> AccentRed
        }
        val currentLabel = when {
            isUnsupported -> "Unsupported"
            animPct >= 80 -> "Good"
            animPct >= 60 -> "Fair"
            else          -> "Poor"
        }

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "BATTERY HEALTH",
                        color = muted,
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp)
                    )
                    if (!isUnsupported && info.healthSource == "bsoh") {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                "BSOH",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color    = MaterialTheme.colorScheme.onSecondaryContainer,
                                style    = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                Box(Modifier.size(190.dp), contentAlignment = Alignment.Center) {
                    ArcGauge(
                        percent    = animPct / 100f,
                        trackColor = border,
                        fillColor  = currentColor,
                        modifier   = Modifier.fillMaxSize()
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (isUnsupported) "—" else "${animPct.toInt()}%",
                            color = currentColor,
                            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            currentLabel,
                            color = currentColor.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (isUnsupported) {
                    Surface(
                        shape  = RoundedCornerShape(10.dp),
                        color  = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(painterResource(R.drawable.info), null,
                                tint = ts, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Device unsupported",
                                color = ts,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                } else {
                    val (descBg, descText, descMsg) = when {
                        animPct >= 80 -> Triple(AccentGreen.copy(alpha  = 0.1f), AccentGreen,  "Battery is in great condition")
                        animPct >= 60 -> Triple(AccentOrange.copy(alpha = 0.1f), AccentOrange, "Battery capacity is declining")
                        else          -> Triple(AccentRed.copy(alpha    = 0.1f), AccentRed,    "Battery replacement recommended")
                    }
                    Surface(
                        shape  = RoundedCornerShape(10.dp),
                        color  = descBg,
                        border = BorderStroke(1.dp, descText.copy(alpha = 0.25f))
                    ) {
                        Text(
                            descMsg,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            color    = descText,
                            style    = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun ArcGauge(percent: Float, trackColor: Color, fillColor: Color, modifier: Modifier = Modifier) {
        Box(modifier = modifier.scale(scaleX = -1f, scaleY = 1f), contentAlignment = Alignment.Center) {
            CircularWavyProgressIndicator(
                progress    = { percent },
                modifier    = Modifier.fillMaxSize(),
                color       = fillColor,
                trackColor  = trackColor,
                stroke      = WavyProgressIndicatorDefaults.circularIndicatorStroke,
                trackStroke = WavyProgressIndicatorDefaults.circularTrackStroke,
                gapSize     = WavyProgressIndicatorDefaults.CircularIndicatorTrackGapSize,
                amplitude   = { 1.0f },
                wavelength  = WavyProgressIndicatorDefaults.CircularWavelength,
                waveSpeed   = WavyProgressIndicatorDefaults.CircularWavelength
            )
        }
    }

    @Composable
    fun CycleCountCard(info: BatteryInfo) {
        val cycles        = info.cycleCount ?: 0
        val isUnsupported = info.cycleCount == null
        val maxBar = 800f

        var animStarted by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { delay(200); animStarted = true }
        val animatedCycles by animateFloatAsState(
            if (animStarted) cycles.toFloat() else 0f,
            tween(1200, easing = FastOutSlowInEasing), label = "cycles_count"
        )
        val animatedBarFraction by animateFloatAsState(
            if (animStarted) (cycles.toFloat() / maxBar).coerceIn(0f, 1f) else 0f,
            tween(1200, easing = FastOutSlowInEasing), label = "cycles_bar"
        )

        val muted   = textMuted()
        val onSurf  = MaterialTheme.colorScheme.onSurface
        val onSurfV = MaterialTheme.colorScheme.onSurfaceVariant
        val ts      = textSecondary()
        val border  = cardBorderColor()

        val (statusColor, statusText) = when {
            cycles < 300 -> Pair(AccentGreen,  "Low wear")
            cycles < 500 -> Pair(AccentOrange, "Moderate wear")
            else         -> Pair(AccentRed,    "High wear")
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
                        color    = if (isUnsupported) MaterialTheme.colorScheme.surfaceVariant
                        else statusColor.copy(alpha = 0.15f),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(painterResource(R.drawable.refresh), null,
                                tint     = if (isUnsupported) MaterialTheme.colorScheme.outlineVariant
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
                            if (isUnsupported) "Not reported by device" else "Full charge equivalents",
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
                            if (isUnsupported) "—" else animatedCycles.toInt().toString(),
                            color = if (isUnsupported) MaterialTheme.colorScheme.outlineVariant
                            else statusColor,
                            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            "cycles",
                            color = if (isUnsupported) MaterialTheme.colorScheme.outlineVariant
                            else onSurfV,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    if (!isUnsupported) {
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
                    } else {
                        Surface(
                            shape  = RoundedCornerShape(12.dp),
                            color  = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        ) {
                            Row(
                                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.size(8.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant, CircleShape))
                                Spacer(Modifier.width(8.dp))
                                Text("Unsupported", color = ts,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }

                if (!isUnsupported) {
                    Spacer(Modifier.height(18.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .background(border, RoundedCornerShape(3.dp))
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(animatedBarFraction)
                                .fillMaxHeight()
                                .background(
                                    Brush.horizontalGradient(listOf(statusColor.copy(alpha = 0.7f), statusColor)),
                                    RoundedCornerShape(3.dp)
                                )
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("0", color = muted, style = MaterialTheme.typography.labelSmall)
                        Text("300", color = if (cycles < 300) muted else AccentGreen,
                            style = MaterialTheme.typography.labelSmall)
                        Text("500", color = if (cycles < 500) muted else AccentOrange,
                            style = MaterialTheme.typography.labelSmall)
                        Text("800+", color = if (cycles < 800) muted else AccentRed,
                            style = MaterialTheme.typography.labelSmall)
                    }

                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.info), null,
                            tint     = onSurfV,
                            modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "1 cycle is charging from 0% to 100%",
                            color = onSurfV,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}