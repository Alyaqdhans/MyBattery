package com.alyaqdhan.mybattery

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.net.toUri

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

        val composeView = findViewById<androidx.compose.ui.platform.ComposeView>(R.id.compose_view)
        composeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MyBatteryTheme {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        SetupScreen(
                            viewModel      = viewModel,
                            showBackButton = showBack,
                            onPickFolder   = { folderPicker.launch(null) },
                            onBack         = { finish() },
                            onDialCode     = {
                                startActivity(
                                    Intent(Intent.ACTION_DIAL,
                                        "tel:*%239900".toUri())
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

// ── SetupScreen composable ────────────────────────────────────────────────────

@Composable
private fun SetupScreen(
    viewModel: AppViewModel,
    showBackButton: Boolean,
    onPickFolder: () -> Unit,
    onBack: () -> Unit,
    onDialCode: () -> Unit
) {
    val steps = listOf(
        SetupStep(painterResource(R.drawable.phone), "Open Samsung SysDump",
            "Tap the button on the right and type # to open the menu.",
            actionLabel = "*#9900#",
            onAction    = onDialCode),
        SetupStep(painterResource(R.drawable.play_arrow), "Run dumpstate/logcat",
            "Tap \"Run dumpstate/logcat\" and wait a couple of minutes to finish."),
        SetupStep(painterResource(R.drawable.check),      "Copy to sdcard",
            "Tap \"Copy to sdcard(include CP Ramdump)\". Logs will be saved to Internal Storage."),
        SetupStep(painterResource(R.drawable.search),     "Grant folder permission",
            "Tap the button below, navigate to folder /log/, and allow reading the folder.")
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
                        Text("My Battery", color = tp,
                            style = MaterialTheme.typography.titleLarge)
                        Text("Samsung log reader", color = ts,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(Modifier.height(40.dp))

            // ── Steps ─────────────────────────────────────────────────────────
            Box(Modifier.alpha(alpha)) {
                Column {
                    Text("How to get your log", color = tp,
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
                        onClick   = { if (!isScanning) onPickFolder() },
                        enabled   = !hasLivePermission && !isScanning,
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
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier  = Modifier.size(20.dp),
                                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Scanning folder…",
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
                                if (hasLivePermission) "Permission Already Granted"
                                else "Grant Permission to Log Folder",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
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
                                    "Back to Dashboard",
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
}