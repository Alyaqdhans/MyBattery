package com.alyaqdhan.mybattery

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

class SetupActivity : ComponentActivity() {

    companion object {
        /** Pass true when opening Setup from the Dashboard (back button visible). */
        const val EXTRA_SHOW_BACK = "show_back"
    }

    // ── Folder picker ─────────────────────────────────────────────────────────
    private val folderPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@registerForActivityResult

        val viewModel = appViewModel
        val showBack  = intent.getBooleanExtra(EXTRA_SHOW_BACK, false)

        // onFolderPicked now validates the folder and calls back with success/failure.
        // We only navigate away on success — on failure we stay here and show the
        // WrongFolder error dialog (set by the ViewModel).
        viewModel.onFolderPicked(uri) { success ->
            if (success) {
                if (showBack) {
                    // Opened from Dashboard → go back; MainActivity is still alive
                    finish()
                } else {
                    // First-run → launch Dashboard, clear back stack so user can't
                    // return to Setup with the back button
                    startActivity(
                        Intent(this, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    )
                    finish()
                }
            }
            // On failure: ViewModel has set errorDialog — the Compose UI will show it.
            // We intentionally do NOT navigate; the user must pick a correct folder.
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_setup)

        window.decorView.post {
            val isDark = resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars     = !isDark
                isAppearanceLightNavigationBars = !isDark
            }
        }

        val viewModel = appViewModel
        val showBack  = intent.getBooleanExtra(EXTRA_SHOW_BACK, false)

        // Restored in a new process: the ViewModel starts out empty, so reload the saved folder.
        if (savedInstanceState != null && viewModel.folderUri == null) viewModel.initialize()

        val composeView = findViewById<androidx.compose.ui.platform.ComposeView>(R.id.compose_view)
        composeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MyBatteryTheme {
                    SetupScreen(
                        viewModel      = viewModel,
                        showBackButton = showBack,
                        onPickFolder   = { folderPicker.launch(null) },
                        onOpenSettings = { openAppSettings() },
                        onRemoveAccess = {
                            viewModel.removeFolderAccess()
                            // The Dashboard has nothing left to show, so start over from first run.
                            startActivity(
                                Intent(this@SetupActivity, SetupActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            )
                            finish()
                        },
                        onBack         = { finish() },
                        onDialCode     = { openDialer(viewModel.dialCode) }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Access can be revoked in Settings while this screen is open. Skip while a folder
        // pick is being scanned: its result arrives just before this and handles errors itself.
        val viewModel = appViewModel
        if (!viewModel.isLoadingDetail) viewModel.checkOnResume()
    }

    /** Not every device has a phone app, a Wi-Fi tablet for example. */
    private fun openDialer(code: String) {
        try {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", code, null)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_dialer, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Android gives third-party apps no way to open their Permissions page directly,
     * so this opens App info, which is one tap away from it.
     */
    private fun openAppSettings() {
        val appInfo = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        )
        try {
            startActivity(appInfo)
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }
}

// ── SetupScreen composable ────────────────────────────────────────────────────

@Composable
private fun SetupScreen(
    viewModel: AppViewModel,
    showBackButton: Boolean,
    onPickFolder: () -> Unit,
    onOpenSettings: () -> Unit,
    onRemoveAccess: () -> Unit,
    onBack: () -> Unit,
    onDialCode: () -> Unit
) {
    var showEditDialog   by remember { mutableStateOf(false) }
    var editCodeDraft    by remember { mutableStateOf("") }
    var showRemoveDialog by remember { mutableStateOf(false) }

    val dialCodeLabel = viewModel.dialCode.localDigits()

    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val heroAlpha by animateFloatAsState(if (shown) 1f else 0f, tween(350), label = "setup_hero")
    val bodyAlpha by animateFloatAsState(
        if (shown) 1f else 0f, tween(400, delayMillis = 140), label = "setup_body")

    // isLoadingDetail is true while the ViewModel is scanning after folder pick
    val isScanning        = viewModel.isLoadingDetail
    val hasLivePermission = viewModel.alreadyHasPerm

    val steps = listOf(
        SetupStep(
            icon         = R.drawable.ic_sym_call,
            title        = stringResource(R.string.step_dial_title),
            desc         = stringResource(R.string.step_dial_desc),
            actionLabel  = dialCodeLabel,
            onAction     = onDialCode,
            onEditAction = {
                editCodeDraft = dialCodeLabel
                showEditDialog = true
            }
        ),
        SetupStep(R.drawable.ic_sym_play_arrow, stringResource(R.string.step_run_title), stringResource(R.string.step_run_desc)),
        SetupStep(R.drawable.ic_sym_sd_card, stringResource(R.string.step_copy_title), stringResource(R.string.step_copy_desc)),
        SetupStep(
            icon    = R.drawable.ic_sym_folder,
            title   = stringResource(R.string.step_grant_title),
            desc    = stringResource(R.string.step_grant_desc),
            content = if (hasLivePermission) {
                {
                    FolderAccess(
                        isScanning     = isScanning,
                        onOpenSettings = onOpenSettings,
                        onChangeFolder = onPickFolder,
                        onRemoveAccess = { showRemoveDialog = true }
                    )
                }
            } else {
                null
            }
        )
    )
    val green             = accentGreenEffective()
    val page              = appColors().page
    val tp                = textPrimary()
    val ts                = textSecondary()
    val muted             = MaterialTheme.colorScheme.onSurfaceVariant
    val error             = MaterialTheme.colorScheme.error
    val cardActionStyle   = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)

    // Show error dialogs (wrong folder, etc.)
    ErrorDialogHost(
        errorDialog = viewModel.errorDialog,
        onDismiss   = { viewModel.dismissErrorDialog() }
    )

    val scroll = rememberScrollState()

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .background(Brush.verticalGradient(listOf(green.copy(alpha = 0.10f), Color.Transparent)))
            )

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(horizontal = 20.dp)
            ) {
                // ── Header ────────────────────────────────────────────────────
                Row(
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = heroAlpha } // Modifier.alpha would clip the logo's glow
                        .padding(top = ScreenContentTop - 9.dp, bottom = 20.dp), // Level with the Dashboard's title
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
                ) {
                    AppLogo()
                    Column(Modifier.weight(1f, fill = false)) {
                        Text(
                            stringResource(R.string.app_name), color = tp,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            stringResource(R.string.setup_subtitle), color = ts,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Column(Modifier.alpha(bodyAlpha)) {
                    // ── Steps ─────────────────────────────────────────────────
                    SetupCard {
                        Column(Modifier.padding(20.dp)) {
                            Text(
                                stringResource(R.string.setup_how_to), color = tp,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Spacer(Modifier.height(18.dp))
                            steps.forEachIndexed { i, step ->
                                SetupStepRow(
                                    number = i + 1,
                                    step   = step,
                                    isLast = i == steps.lastIndex,
                                    isDone = i == steps.lastIndex && hasLivePermission
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }

            // Keeps scrolled content off the status bar icons, without covering the top at rest
            Column(Modifier.graphicsLayer { alpha = (scroll.value / 48.dp.toPx()).coerceIn(0f, 1f) }) {
                Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(page))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .background(Brush.verticalGradient(listOf(page, Color.Transparent)))
                )
            }
        }

        // ── Bottom actions ────────────────────────────────────────────────────
        Column(
            Modifier
                .fillMaxWidth()
                .background(page)
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!hasLivePermission) {
                Button(
                    onClick   = onPickFolder,
                    enabled   = !isScanning,
                    modifier  = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape     = RoundedCornerShape(14.dp),
                    colors    = ButtonDefaults.buttonColors(
                        containerColor         = green,
                        contentColor           = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        disabledContentColor   = muted
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(20.dp),
                            color       = muted,
                            strokeWidth = 2.dp
                        )
                    } else {
                        MatchedIcon(R.drawable.ic_sym_folder, cardActionStyle, Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(
                            if (isScanning) R.string.setup_scanning else R.string.setup_grant_permission
                        ),
                        style = cardActionStyle
                    )
                }
            }

            if (showBackButton) {
                OutlinedButton(
                    onClick  = onBack,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    shape    = RoundedCornerShape(12.dp),
                    border   = BorderStroke(1.dp, cardBorderColor()),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = tp)
                ) {
                    MatchedIcon(R.drawable.ic_sym_arrow_back, cardActionStyle, Modifier.size(18.dp), ts)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.setup_back_to_dashboard), style = cardActionStyle)
                }
            }
        }
    }

    // ── Remove access dialog ──────────────────────────────────────────────────
    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = { Text(stringResource(R.string.remove_access_title)) },
            text  = { Text(stringResource(R.string.remove_access_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showRemoveDialog = false
                    onRemoveAccess()
                }) {
                    Text(stringResource(R.string.remove_access_confirm), color = error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    // ── Edit dial code dialog ─────────────────────────────────────────────────
    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text(stringResource(R.string.dial_edit_title)) },
            text  = {
                // A dial code has no letters to set its direction, and it reads left to right.
                val codeStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr)
                OutlinedTextField(
                    value         = editCodeDraft,
                    onValueChange = { editCodeDraft = it },
                    singleLine    = true,
                    textStyle     = codeStyle,
                    label         = { Text(stringResource(R.string.dial_edit_label)) },
                    placeholder   = { Text(stringResource(R.string.dial_edit_placeholder), style = codeStyle) }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.saveDialCode(editCodeDraft)
                    showEditDialog = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}
