package com.alyaqdhan.mybattery

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val context: Context = app.applicationContext
    private val prefs = context.getSharedPreferences("battery_prefs", Context.MODE_PRIVATE)

    // ── Shared app state ──────────────────────────────────────────────────────
    var folderUri        by mutableStateOf<Uri?>(null)           ; private set
    var alreadyHasPerm   by mutableStateOf(false)                ; private set
    var batteryInfo      by mutableStateOf<BatteryInfo?>(null)   ; private set
    var folderAccessible by mutableStateOf(true)                 ; private set
    var hasEverScanned   by mutableStateOf(false)                ; private set
    var isRefreshing     by mutableStateOf(false)                ; private set
    var isLoadingDetail  by mutableStateOf(false)                ; private set

    // Log sheet
    var allLogEntries  by mutableStateOf<List<DocEntry>>(emptyList()) ; private set
    var showLogSheet   by mutableStateOf(false)                       ; private set

    // Raw log screen
    var rawLogText          by mutableStateOf("")    ; private set
    var isLoadingRaw        by mutableStateOf(false) ; private set
    var selectedLogFileName by mutableStateOf("")    ; private set

    // Error overlay
    var errorDialog by mutableStateOf<ErrorDialog?>(null) ; private set

    // Gauge animation signals
    var gaugeAmplitude by mutableStateOf(1f)   ; private set
    var gaugeReplayKey by mutableIntStateOf(0) ; private set

    // ── Initialization ────────────────────────────────────────────────────────

    /**
     * Called once from [MainActivity] on creation.
     * Returns true if a saved URI exists (show Dashboard),
     * false if first-run (caller should open SetupActivity).
     */
    fun initialize(): Boolean {
        val savedUriStr = prefs.getString("folder_uri", null)
        val savedUri    = savedUriStr?.toUri()
        folderUri       = savedUri

        if (savedUri == null) return false

        // Synchronous persisted-permission check (safe on main thread — in-memory list).
        // If permission was revoked since last launch, treat it as first-run and send
        // the user back to SetupActivity so they must re-grant access.
        val hasSyncPerm = context.contentResolver.persistedUriPermissions
            .any { it.uri.toString() == savedUri.toString() && it.isReadPermission }
        if (!hasSyncPerm) return false

        viewModelScope.launch {
            if (!BatteryParser.hasPersistedPermission(context, savedUri)) {
                // Shouldn't normally reach here (handled synchronously above),
                // but guard against race conditions.
                alreadyHasPerm   = false
                folderAccessible = false
                allLogEntries    = emptyList()
                isLoadingDetail  = false
                return@launch
            }

            alreadyHasPerm  = true
            isLoadingDetail = true

            val cached = BatteryCache.loadCachedBatteryInfo(prefs)
            if (cached != null) {
                batteryInfo      = cached
                folderAccessible = true
                hasEverScanned   = true

                val latestEntry = withContext(Dispatchers.IO) {
                    BatteryParser.findLatestLogEntry(savedUri, context)
                }
                if (latestEntry == null) {
                    folderAccessible = false
                    isLoadingDetail  = false
                    gaugeReplayKey++
                    errorDialog = ErrorDialog.FolderDeleted
                    return@launch
                }
                if (latestEntry.name != cached.logFileName) {
                    val liveInfo = withContext(Dispatchers.IO) {
                        BatteryParser.parseLatestLog(savedUri, context, prefs)
                    }
                    if (liveInfo.readSuccess) batteryInfo = liveInfo
                }
                isLoadingDetail = false

            } else {
                val latestEntry = withContext(Dispatchers.IO) {
                    BatteryParser.findLatestLogEntry(savedUri, context)
                }
                if (latestEntry == null) {
                    isLoadingDetail  = false
                    folderAccessible = false
                    errorDialog = ErrorDialog.FolderDeleted
                    return@launch
                }
                val info = withContext(Dispatchers.IO) {
                    BatteryParser.smartScan(savedUri, context, prefs)
                }
                isLoadingDetail  = false
                batteryInfo      = info
                folderAccessible = true
                if (info.readSuccess) {
                    hasEverScanned = true
                } else {
                    errorDialog = ErrorDialog.WrongFolder(info.errorMessage)
                }
            }
        }
        return true
    }

    // ── Folder picker result ──────────────────────────────────────────────────

    /**
     * Persist the picked URI, then invoke [onResult] immediately once the folder is
     * validated (log files found).  The actual parse/scan continues in the background
     * while the Dashboard is shown with isLoadingDetail = true.
     *
     *  - `onResult(true)`  → folder valid, navigate to Dashboard now
     *  - `onResult(false)` → wrong/empty folder, stay on Setup (errorDialog is set)
     */
    fun onFolderPicked(uri: Uri, onResult: (success: Boolean) -> Unit) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {}

        val isNewFolder = uri.toString() != prefs.getString("folder_uri", null)
        if (isNewFolder) BatteryCache.clearBatteryInfoCache(prefs)

        folderUri        = uri
        alreadyHasPerm   = true
        folderAccessible = true
        allLogEntries    = emptyList()
        batteryInfo      = null
        isLoadingDetail  = true

        viewModelScope.launch {
            // First check the folder actually contains a dumpstate log
            val latestEntry = withContext(Dispatchers.IO) {
                BatteryParser.findLatestLogEntry(uri, context)
            }

            if (latestEntry == null) {
                // Nothing found — treat as wrong folder, do NOT persist the URI
                isLoadingDetail  = false
                folderAccessible = false
                alreadyHasPerm   = false
                folderUri        = prefs.getString("folder_uri", null)?.toUri()
                errorDialog      = ErrorDialog.WrongFolder(
                    "No dumpstate log files found in the selected folder. " +
                            "Please navigate to /Device/log/ and try again."
                )
                onResult(false)
                return@launch
            }

            // Folder looks correct — persist the URI and navigate to Dashboard immediately.
            // The scan continues in the background; the Dashboard shows isLoadingDetail.
            prefs.edit { putString("folder_uri", uri.toString()) }
            onResult(true)

            val info = withContext(Dispatchers.IO) {
                BatteryParser.smartScan(uri, context, prefs)
            }
            isLoadingDetail = false

            if (info.readSuccess) {
                batteryInfo    = info
                hasEverScanned = true
            } else {
                // Scan failed — show error on the Dashboard
                folderAccessible = false
                alreadyHasPerm   = false
                folderUri        = prefs.getString("folder_uri", null)?.toUri()
                errorDialog      = ErrorDialog.WrongFolder(info.errorMessage)
            }
        }
    }

    // ── Rescan (pull-to-refresh) ──────────────────────────────────────────────

    fun rescan() {
        val uri = folderUri ?: return
        if (isRefreshing) return

        viewModelScope.launch {
            isRefreshing = true

            if (!BatteryParser.hasPersistedPermission(context, uri)) {
                isRefreshing  = false
                showLogSheet  = false
                allLogEntries = emptyList()
                val cached = BatteryCache.loadCachedBatteryInfo(prefs)
                if (cached != null) {
                    batteryInfo = cached; hasEverScanned = true; gaugeReplayKey++
                }
                errorDialog = ErrorDialog.PermissionLost
                return@launch
            }

            val latestEntry = withContext(Dispatchers.IO) {
                BatteryParser.findLatestLogEntry(uri, context)
            }
            if (latestEntry == null) {
                isRefreshing     = false
                folderAccessible = false
                showLogSheet     = false
                val cached = BatteryCache.loadCachedBatteryInfo(prefs)
                if (cached != null) {
                    batteryInfo = cached; hasEverScanned = true; gaugeReplayKey++
                }
                errorDialog = ErrorDialog.FolderDeleted
                return@launch
            }

            // Compare cache to latest file — if they match, nothing changed on disk → just animate.
            // If they differ, a new file appeared → parse it.
            // If the user is viewing an older manually-selected file and refreshes, the cache
            // still reflects the true latest, so no unnecessary re-parse happens.
            val cached = BatteryCache.loadCachedBatteryInfo(prefs)

            if (cached?.logFileName == latestEntry.name) {
                // Files unchanged — just replay the gauge animation
                alreadyHasPerm   = true
                folderAccessible = true
                isRefreshing     = false
                gaugeReplayKey++
                return@launch
            }

            // New file appeared, or loaded file was deleted — parse latest
            isLoadingDetail = true
            val info = withContext(Dispatchers.IO) {
                BatteryParser.parseLatestLog(uri, context, prefs)
            }
            isRefreshing    = false
            isLoadingDetail = false
            if (info.readSuccess) {
                batteryInfo      = info
                alreadyHasPerm   = true
                folderAccessible = true
                hasEverScanned   = true
                gaugeReplayKey++
            } else {
                errorDialog = ErrorDialog.WrongFolder(info.errorMessage)
            }
        }
    }

    // ── Log sheet ─────────────────────────────────────────────────────────────

    fun openLogSheet() {
        val uri = folderUri
        if (uri != null && BatteryParser.hasPersistedPermission(context, uri)) {
            viewModelScope.launch {
                allLogEntries = withContext(Dispatchers.IO) { BatteryParser.listAllLogs(uri, context) }
                showLogSheet  = true
            }
        } else {
            showLogSheet = true
        }
    }

    fun dismissLogSheet() { showLogSheet = false }

    fun selectLogEntry(entry: DocEntry) {
        val uri = folderUri ?: return
        viewModelScope.launch {
            isLoadingDetail = true
            showLogSheet    = false
            startGaugePulse()
            val parsed = withContext(Dispatchers.IO) { BatteryParser.parseLogEntry(uri, entry, context) }
            isLoadingDetail = false
            if (parsed.readSuccess) {
                batteryInfo      = parsed
                folderAccessible = true
            }
        }
    }

    /**
     * Loads the raw log text for [entry].
     * Navigation to [LogRawActivity] is handled by the calling Activity.
     */
    fun openRawLog(entry: DocEntry) {
        val uri = folderUri ?: return
        viewModelScope.launch {
            rawLogText          = ""
            isLoadingRaw        = true
            showLogSheet        = false
            selectedLogFileName = entry.name
            val text = withContext(Dispatchers.IO) {
                BatteryParser.extractBatterySection(uri, entry, context)
            }
            rawLogText   = text
            isLoadingRaw = false
        }
    }

    // ── Error dialog ──────────────────────────────────────────────────────────

    fun dismissErrorDialog() { errorDialog = null }

    // ── Resume check ─────────────────────────────────────────────────────────

    fun checkOnResume() {
        val uri = folderUri ?: return
        viewModelScope.launch {
            val hasPerm = withContext(Dispatchers.IO) {
                BatteryParser.hasPersistedPermission(context, uri)
            }
            if (!hasPerm && alreadyHasPerm) {
                alreadyHasPerm   = false
                folderAccessible = false
                allLogEntries    = emptyList()
                showLogSheet     = false
                val cached = BatteryCache.loadCachedBatteryInfo(prefs)
                if (cached != null) {
                    batteryInfo = cached; hasEverScanned = true; gaugeReplayKey++
                }
                errorDialog = ErrorDialog.PermissionLost
            } else if (hasPerm) {
                val latestEntry = withContext(Dispatchers.IO) {
                    BatteryParser.findLatestLogEntry(uri, context)
                }
                if (latestEntry == null) {
                    if (folderAccessible) {
                        folderAccessible = false
                        allLogEntries    = emptyList()
                        showLogSheet     = false
                        val cached = BatteryCache.loadCachedBatteryInfo(prefs)
                        if (cached != null) {
                            batteryInfo = cached; hasEverScanned = true; gaugeReplayKey++
                        }
                        errorDialog = ErrorDialog.FolderDeleted
                    }
                } else {
                    // Folder is accessible — restore state if it was previously inaccessible
                    if (!folderAccessible) folderAccessible = true

                    // If the currently loaded file no longer exists in the folder,
                    // silently fall back to the latest without showing an error.
                    // Don't interfere if user intentionally loaded an older file that still exists.
                    val loadedName = batteryInfo?.logFileName?.takeIf { it.isNotEmpty() }
                    if (loadedName != null && loadedName != latestEntry.name) {
                        val loadedStillExists = withContext(Dispatchers.IO) {
                            BatteryParser.listAllLogs(uri, context).any { it.name == loadedName }
                        }
                        if (!loadedStillExists) {
                            val cached = BatteryCache.loadCachedBatteryInfo(prefs)
                            if (cached?.logFileName == latestEntry.name) {
                                // Cache already holds the latest — use it directly
                                batteryInfo    = cached
                                hasEverScanned = true
                                gaugeReplayKey++
                            } else {
                                // Need to parse the latest file
                                isLoadingDetail = true
                                val info = withContext(Dispatchers.IO) {
                                    BatteryParser.parseLatestLog(uri, context, prefs)
                                }
                                isLoadingDetail = false
                                if (info.readSuccess) {
                                    batteryInfo    = info
                                    hasEverScanned = true
                                    gaugeReplayKey++
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Gauge pulse ───────────────────────────────────────────────────────────

    private var pulseJob: kotlinx.coroutines.Job? = null

    fun startGaugePulse() {
        pulseJob?.cancel()
        pulseJob = viewModelScope.launch {
            while (true) {
                delay(900L)
                gaugeAmplitude = if (gaugeAmplitude < 0.5f) 1f else 0f
            }
        }
    }

    fun stopGaugePulse() {
        pulseJob?.cancel()
        pulseJob       = null
        gaugeAmplitude = 1f
    }
}