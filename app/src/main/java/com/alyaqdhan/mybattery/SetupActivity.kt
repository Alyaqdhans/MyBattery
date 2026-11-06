package com.alyaqdhan.mybattery

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
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
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
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
                        onDialCode     = {
                            startActivity(
                                Intent(Intent.ACTION_DIAL,
                                    Uri.fromParts("tel", viewModel.dialCode, null))
                            )
                        }
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

    val steps = listOf(
        SetupStep(painterResource(R.drawable.phone), stringResource(R.string.step_dial_title),
            stringResource(R.string.step_dial_desc),
            actionLabel  = dialCodeLabel,
            onAction     = onDialCode,
            onLongAction = {
                editCodeDraft = dialCodeLabel
                showEditDialog = true
            }),
        SetupStep(painterResource(R.drawable.play_arrow), stringResource(R.string.step_run_title),
            stringResource(R.string.step_run_desc)),
        SetupStep(painterResource(R.drawable.check),      stringResource(R.string.step_copy_title),
            stringResource(R.string.step_copy_desc)),
        SetupStep(painterResource(R.drawable.search),     stringResource(R.string.step_grant_title),
            stringResource(R.string.step_grant_desc))
    )

    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(350), label = "setup_fade")

    // isLoadingDetail is true while the ViewModel is scanning after folder pick
    val isScanning        = viewModel.isLoadingDetail
    val hasLivePermission = viewModel.alreadyHasPerm
    val green             = accentGreenEffective()
    val border            = cardBorderColor()
    val tp                = textPrimary()
    val ts                = textSecondary()
    val onPrimary         = MaterialTheme.colorScheme.onPrimary
    val grantedContainer  = MaterialTheme.colorScheme.surfaceContainerHighest
    val grantedContent    = MaterialTheme.colorScheme.onSurfaceVariant

    // Show error dialogs (wrong folder, etc.)
    ErrorDialogHost(
        errorDialog = viewModel.errorDialog,
        onDismiss   = { viewModel.dismissErrorDialog() }
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(56.dp))

            // ── App header ────────────────────────────────────────────────────
            Box(Modifier.alpha(alpha)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .background(
                                Brush.radialGradient(listOf(green.copy(alpha = 0.28f), Color.Transparent)),
                                CircleShape
                            )
                            .border(1.5.dp, green.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(painterResource(R.drawable.battery), null,
                            tint = green, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(stringResource(R.string.app_name), color = tp,
                            style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.setup_subtitle), color = ts,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(Modifier.height(40.dp))

            // ── Steps ─────────────────────────────────────────────────────────
            Box(Modifier.alpha(alpha)) {
                Column {
                    Text(stringResource(R.string.setup_how_to), color = tp,
                        style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(20.dp))
                    steps.forEachIndexed { i, step ->
                        SetupStepRow(number = i + 1, step = step)
                        if (i < steps.lastIndex) Spacer(Modifier.height(12.dp))
                    }
                }
            }

            Spacer(Modifier.height(36.dp))

            // ── Action buttons ────────────────────────────────────────────────
            Box(Modifier.alpha(alpha)) {
                Column {
                    Button(
                        onClick   = { if (hasLivePermission) onOpenSettings() else onPickFolder() },
                        enabled   = !isScanning,
                        modifier  = Modifier.fillMaxWidth().height(52.dp),
                        shape     = RoundedCornerShape(14.dp),
                        colors    = ButtonDefaults.buttonColors(
                            containerColor         = if (hasLivePermission) grantedContainer else green,
                            contentColor           = if (hasLivePermission) grantedContent else onPrimary,
                            disabledContainerColor = grantedContainer,
                            disabledContentColor   = grantedContent
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier  = Modifier.size(20.dp),
                                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                stringResource(R.string.setup_scanning),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        } else {
                            Icon(
                                if (hasLivePermission) painterResource(R.drawable.check_circle)
                                else painterResource(R.drawable.search),
                                null, modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                stringResource(
                                    if (hasLivePermission) R.string.setup_permission_granted
                                    else R.string.setup_grant_permission
                                ),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            if (hasLivePermission) {
                                Spacer(Modifier.width(10.dp))
                                Icon(
                                    painterResource(R.drawable.arrow_forward_ios),
                                    stringResource(R.string.setup_open_app_settings),
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = hasLivePermission && !isScanning,
                        enter   = fadeIn(tween(250)),
                        exit    = fadeOut(tween(200))
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick  = onPickFolder,
                                modifier = Modifier.weight(1f).height(52.dp),
                                shape    = RoundedCornerShape(14.dp),
                                border   = BorderStroke(1.dp, border),
                                colors   = ButtonDefaults.outlinedButtonColors(contentColor = tp)
                            ) {
                                Text(
                                    stringResource(R.string.setup_change_folder),
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            OutlinedButton(
                                onClick  = { showRemoveDialog = true },
                                modifier = Modifier.weight(1f).height(52.dp),
                                shape    = RoundedCornerShape(14.dp),
                                border   = BorderStroke(1.dp, border),
                                colors   = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text(
                                    stringResource(R.string.setup_remove_access),
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
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
                                Text(
                                    stringResource(R.string.setup_back_to_dashboard),
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(36.dp))
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
                    Text(
                        stringResource(R.string.remove_access_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
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