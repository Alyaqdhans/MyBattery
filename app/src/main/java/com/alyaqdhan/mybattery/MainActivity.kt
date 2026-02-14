package com.alyaqdhan.mybattery

import android.content.Context
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
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

// ─── Routes ───────────────────────────────────────────────────────────────────
private object Routes {
    const val SETUP           = "setup"
    const val SCANNING        = "scanning"
    const val DASHBOARD       = "dashboard"
    const val WRONG_FOLDER    = "wrong_folder"
    const val FOLDER_DELETED  = "folder_deleted"
    const val PERMISSION_LOST = "permission_lost"
}

// ─── Accent Colors ───────────────────────────────────────────────────────────
private val AccentGreen  = Color(0xFF00E5A0)
private val AccentOrange = Color(0xFFFFAA44)
private val AccentRed    = Color(0xFFFF5C6C)

// ─── Dark palette ─────────────────────────────────────────────────────────────
private val DarkBgDeep        = Color(0xFF080D18)
private val DarkBgCard        = Color(0xFF111827)
private val DarkBgCardBorder  = Color(0xFF1F2D42)
private val DarkTextPrimary   = Color(0xFFE8EEFF)
private val DarkTextSecondary = Color(0xFF7A8BA8)
private val DarkTextMuted     = Color(0xFF334155)

// ─── Light palette ────────────────────────────────────────────────────────────
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
            // Force LTR layout direction for all RTL locales / devices
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                MyBatteryApp()
            }
        }
    }

    // ─── Data model ──────────────────────────────────────────────────────────

    /**
     * Health resolution priority:
     *   1. mSavedBatteryAsoc  (preferred)
     *   2. mSavedBatteryBsoh  (fallback)
     *   3. null               → healthUnsupported = true
     *
     * "unsupported" text or [-1] in the log → treated as absent.
     * readSuccess = we found the log file and can display at least something.
     */
    data class BatteryInfo(
        val healthPercent: Int?,          // resolved health %; null = device unsupported
        val healthSource: String = "",    // "asoc" | "bsoh" | ""
        val healthUnsupported: Boolean = false,

        val cycleCount: Int?,             // null = not reported / unsupported

        val firstUseDateMs: Long = 0L,    // "battery FirstUseDate" parsed; 0 = absent

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

        /** Formatted assembly month, e.g. "Apr 2023". Empty when absent. */
        val firstUseDateFormatted: String
            get() {
                if (firstUseDateMs == 0L) return ""
                return SimpleDateFormat("MMM yyyy", Locale.US)
                    .format(java.util.Date(firstUseDateMs))
            }
    }

    // ─── File reading ─────────────────────────────────────────────────────────

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

    private fun parseLatestLog(folderUri: Uri, context: Context): BatteryInfo {
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
        if (allLogs.isEmpty()) return BatteryInfo(
            healthPercent = null, cycleCount = null, errorMessage = "NO_LOG_FOUND"
        )

        val logEntry = allLogs.maxWithOrNull(
            compareBy<DocEntry> { extractTimestampFromFileName(it.name) }.thenBy { it.lastModified }
        ) ?: return BatteryInfo(healthPercent = null, cycleCount = null, errorMessage = "NO_LOG_FOUND")

        val fileUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, logEntry.id)
        return try {
            context.contentResolver.openInputStream(fileUri)?.use { stream ->
                parseInputStream(stream, logEntry.name)
            } ?: BatteryInfo(healthPercent = null, cycleCount = null,
                logFileName = logEntry.name, errorMessage = "Could not open ${logEntry.name}")
        } catch (e: Exception) {
            BatteryInfo(healthPercent = null, cycleCount = null,
                logFileName = logEntry.name, errorMessage = "Read error: ${e.localizedMessage}")
        }
    }

    /**
     * Parse a value that may appear in these Samsung log formats:
     *   key: 96
     *   key: [96]
     *   key: [-1]          → null  (device reports -1 = unsupported)
     *   key: unsupported   → null
     * Returns null for anything non-numeric or < 0.
     */
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

    /** True when the field is present but explicitly "unsupported" or -1 (not just absent). */
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
        var asocSeen          = false   // key was present in log (even if "unsupported")
        var bsoh: Int?        = null
        var usage: Int?       = null
        var firstUseDateMs    = 0L
        var logTimestampMs    = 0L

        val fullDateTimeRegex    = Regex("""(?:==\s*dumpstate:|dumpstate:|Build time:)\s*(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})""")
        val bracketDateTimeRegex = Regex("""^\[(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})]""")
        val dateTimeFmt          = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        // "LLB CAL: 20230207" or "LLB MAN: 20230207" — assembly/manufacture date
        val llbRegex       = Regex("""^LLB\s+(CAL|MAN):\s*(\d{8})\s*$""", RegexOption.IGNORE_CASE)
        val compactDateFmt = SimpleDateFormat("yyyyMMdd", Locale.US)

        BufferedReader(InputStreamReader(stream)).use { reader ->
            reader.forEachLine { line ->
                val t = line.trim()

                // ── Health ──────────────────────────────────────────────────
                if (t.contains("mSavedBatteryAsoc:")) {
                    asocSeen = true
                    asoc     = extractValueOrNull(t, "mSavedBatteryAsoc:")
                }
                if (t.contains("mSavedBatteryBsoh:")) {
                    bsoh = extractValueOrNull(t, "mSavedBatteryBsoh:")
                }

                // ── Cycles ──────────────────────────────────────────────────
                if (t.contains("mSavedBatteryUsage:")) {
                    // Raw value is cycles * 1000 (e.g. 157000 = 157 cycles)
                    val raw = extractValueOrNull(t, "mSavedBatteryUsage:")
                    usage = if (raw != null) raw / 1000 else null
                }

                // ── Assembly date: LLB CAL preferred, LLB MAN fallback ─────
                if (firstUseDateMs == 0L) {
                    llbRegex.find(t)?.let { m ->
                        firstUseDateMs = try {
                            compactDateFmt.parse(m.groupValues[2])?.time ?: 0L
                        } catch (_: Exception) { 0L }
                    }
                }

                // ── Log timestamp ───────────────────────────────────────────
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

        // ── Resolve health ──────────────────────────────────────────────────
        val (resolvedHealth, healthSource, healthUnsupported) = when {
            asoc != null -> Triple(asoc, "asoc", false)
            bsoh != null -> Triple(bsoh, "bsoh", false)
            else         -> Triple(null, "", true)
        }

        // readSuccess = the log file was found & we can show SOMETHING meaningful.
        // Even "unsupported" asoc means the user selected the correct folder.
        val readSuccess = resolvedHealth != null ||
                bsoh != null ||
                usage != null ||
                firstUseDateMs != 0L ||
                asocSeen  // key was present, even if value was "unsupported"

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
        val compact = Regex("""(\d{8})[_-](\d{6})""").find(name) ?: return 0L
        val (date, time) = compact.destructured
        val str = "${date.substring(0, 4)}-${date.substring(4, 6)}-${date.substring(6, 8)}" +
                " ${time.substring(0, 2)}:${time.substring(2, 4)}:${time.substring(4, 6)}"
        return try {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(str)?.time ?: 0L
        } catch (_: Exception) { 0L }
    }

    private fun hasPersistedPermission(context: Context, uri: Uri): Boolean =
        context.contentResolver.persistedUriPermissions
            .any { it.uri == uri && it.isReadPermission }

    private fun folderStillExists(context: Context, uri: Uri): Boolean {
        return try {
            val treeDocId = DocumentsContract.getTreeDocumentId(uri)
            val docUri    = DocumentsContract.buildDocumentUriUsingTree(uri, treeDocId)
            context.contentResolver.query(
                docUri,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                null, null, null
            )?.use { it.count > 0 } ?: false
        } catch (_: Exception) { false }
    }

    // ─── Theme ────────────────────────────────────────────────────────────────

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

    // ─── Root composable ─────────────────────────────────────────────────────

    @Composable
    fun MyBatteryApp() {
        val context = LocalContext.current
        val prefs   = context.getSharedPreferences("battery_prefs", Context.MODE_PRIVATE)

        val savedUriStr    = prefs.getString("folder_uri", null)
        val savedUri       = savedUriStr?.toUri()
        val alreadyHasPerm = savedUri != null && hasPersistedPermission(context, savedUri)

        var folderUri                  by remember { mutableStateOf(if (alreadyHasPerm) savedUri else null) }
        var batteryInfo                by remember { mutableStateOf<BatteryInfo?>(null) }
        var hasEverScannedSuccessfully by remember { mutableStateOf(false) }
        var isRefreshing               by remember { mutableStateOf(false) }

        val navController = rememberNavController()
        val scope         = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            if (savedUri != null && !hasPersistedPermission(context, savedUri) && hasEverScannedSuccessfully) {
                navController.navigate(Routes.PERMISSION_LOST) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }

        val startDestination = if (alreadyHasPerm) Routes.SCANNING else Routes.SETUP

        val folderPicker = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocumentTree()
        ) { uri ->
            if (uri != null) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) {}
                prefs.edit { putString("folder_uri", uri.toString()) }
                folderUri   = uri
                batteryInfo = null
                navController.navigate(Routes.SCANNING) {
                    popUpTo(0) { inclusive = true }; launchSingleTop = true
                }
            }
        }

        fun rescan() {
            if (folderUri == null || isRefreshing) return
            scope.launch {
                isRefreshing = true
                if (!hasPersistedPermission(context, folderUri!!)) {
                    isRefreshing = false
                    navController.navigate(Routes.PERMISSION_LOST) {
                        popUpTo(0) { inclusive = true }; launchSingleTop = true
                    }
                    return@launch
                }
                if (!folderStillExists(context, folderUri!!)) {
                    isRefreshing = false
                    navController.navigate(Routes.FOLDER_DELETED) {
                        popUpTo(0) { inclusive = true }; launchSingleTop = true
                    }
                    return@launch
                }
                val info = withContext(Dispatchers.IO) { parseLatestLog(folderUri!!, context) }
                delay(400)
                batteryInfo  = info
                isRefreshing = false
                if (info.readSuccess) {
                    hasEverScannedSuccessfully = true
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(0) { inclusive = true }; launchSingleTop = true
                    }
                } else {
                    navController.navigate(Routes.WRONG_FOLDER) {
                        popUpTo(0) { inclusive = true }; launchSingleTop = true
                    }
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
                            if (!folderStillExists(context, uri)) {
                                navController.navigate(Routes.FOLDER_DELETED) {
                                    popUpTo(Routes.SCANNING) { inclusive = true }
                                }
                                return@LaunchedEffect
                            }
                            val info = withContext(Dispatchers.IO) { parseLatestLog(uri, context) }
                            batteryInfo = info
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
                        val canGoBack = navController.previousBackStackEntry != null
                        SetupScreen(
                            hasEverScannedSuccessfully = hasEverScannedSuccessfully,
                            onPickFolder   = { folderPicker.launch(null) },
                            showBackButton = canGoBack,
                            onBack         = { navController.popBackStack() },
                            isRefreshing   = isRefreshing,
                            onRescan       = { rescan() }
                        )
                    }

                    composable(Routes.DASHBOARD) {
                        val info = batteryInfo
                        if (info != null) {
                            BatteryDashboard(
                                info         = info,
                                isRefreshing = isRefreshing,
                                onRescan     = { rescan() },
                                onShowSteps  = { navController.navigate(Routes.SETUP) }
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
                        WrongFolderScreen(
                            errorMessage = batteryInfo?.errorMessage ?: "",
                            onGoToSteps  = {
                                navController.navigate(Routes.SETUP) {
                                    popUpTo(0) { inclusive = true }; launchSingleTop = true
                                }
                            },
                            isRefreshing = isRefreshing,
                            onRescan     = { rescan() }
                        )
                    }

                    composable(Routes.FOLDER_DELETED) {
                        FolderDeletedScreen(onGoToSteps = {
                            navController.navigate(Routes.SETUP) {
                                popUpTo(0) { inclusive = true }; launchSingleTop = true
                            }
                        })
                    }

                    composable(Routes.PERMISSION_LOST) {
                        PermissionLostScreen(onGoToSteps = {
                            hasEverScannedSuccessfully = false
                            folderUri   = null
                            batteryInfo = null
                            prefs.edit { remove("folder_uri") }
                            navController.navigate(Routes.SETUP) {
                                popUpTo(0) { inclusive = true }; launchSingleTop = true
                            }
                        })
                    }
                }
            }
        }
    }

    // ─── Pull-to-refresh wrapper ──────────────────────────────────────────────

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

    // ─── Scanning ─────────────────────────────────────────────────────────────

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

    // ─── Wrong folder ─────────────────────────────────────────────────────────

    @Composable
    fun WrongFolderScreen(
        errorMessage: String,
        onGoToSteps: () -> Unit,
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
                    onClick   = onGoToSteps,
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

    // ─── Folder deleted ───────────────────────────────────────────────────────

    @Composable
    fun FolderDeletedScreen(onGoToSteps: () -> Unit) {
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
                onClick   = onGoToSteps,
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

    // ─── Permission lost ──────────────────────────────────────────────────────

    @Composable
    fun PermissionLostScreen(onGoToSteps: () -> Unit) {
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
                onClick   = onGoToSteps,
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

    // ─── Setup ────────────────────────────────────────────────────────────────

    data class SetupStep(val icon: Painter, val title: String, val desc: String)

    @Composable
    fun SetupScreen(
        hasEverScannedSuccessfully: Boolean,
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
                            enabled   = !hasEverScannedSuccessfully,
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
                                if (hasEverScannedSuccessfully) painterResource(R.drawable.check_circle)
                                else painterResource(R.drawable.search),
                                null, modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                if (hasEverScannedSuccessfully) "Permission Already Granted"
                                else "Grant Permission to Log Folder",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        if (showBackButton) {
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

    // ─── Dashboard ────────────────────────────────────────────────────────────

    @Composable
    fun BatteryDashboard(
        info: BatteryInfo,
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

                // ── Header ──────────────────────────────────────────────────
                Box(Modifier.fillMaxWidth().alpha(headerAlpha).offset(y = headerSlide.dp)) {
                    Column(
                        Modifier.fillMaxWidth().padding(end = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("My Battery", color = tp,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                        if (info.logFileName.isNotEmpty() || info.relativeDate.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            if (info.logFileName.isNotEmpty())
                                Text(info.logFileName, color = muted, textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelSmall)
                            if (info.relativeDate.isNotEmpty())
                                Text("Log created ${info.relativeDate}", color = muted,
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelSmall)
                        }
                        // Assembly / first-use date
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

    // ─── Health gauge card ────────────────────────────────────────────────────

    @Composable
    fun HealthGaugeCard(info: BatteryInfo) {
        val isUnsupported = info.healthUnsupported
        // When unsupported: draw arc at 100% filled in grey (no animation needed)
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
                // Title + source badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "BATTERY HEALTH",
                        color = muted,
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp)
                    )
                    // Show "BSOH" badge only when we fell back to bsoh
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

                // Gauge ring
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

                // Status banner
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
                // Always show wave animation regardless of fill level
                amplitude   = { 1.0f },
                wavelength  = WavyProgressIndicatorDefaults.CircularWavelength,
                waveSpeed   = WavyProgressIndicatorDefaults.CircularWavelength
            )
        }
    }

    // ─── Cycle count card ─────────────────────────────────────────────────────

    @Composable
    fun CycleCountCard(info: BatteryInfo) {
        val cycles        = info.cycleCount ?: 0
        val isUnsupported = info.cycleCount == null

        // Wear thresholds (after /1000 division):
        //   < 300  → Low wear    (green)
        //   < 500  → Moderate    (orange)
        //   ≥ 500  → High wear   (red)
        val maxBar = 800f  // bar fills to 100% at this cycle count

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

                // ── Card header ─────────────────────────────────────────────
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

                // ── Value + status pill ─────────────────────────────────────
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

                // ── Progress bar ────────────────────────────────────────────
                if (!isUnsupported) {
                    Spacer(Modifier.height(18.dp))
                    // Track
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .background(border, RoundedCornerShape(3.dp))
                    ) {
                        // Fill
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
                    // Scale labels
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
                    // Hint — visible and clear
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