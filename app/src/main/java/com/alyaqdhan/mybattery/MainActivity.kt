package com.alyaqdhan.mybattery

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        // Status-bar / nav-bar appearance tracks theme
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

        // First-run: no folder URI saved → open Setup, finish this Activity
        if (savedInstanceState == null && !viewModel.initialize()) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }

        // Attach Compose content to the ComposeView declared in activity_main.xml
        val composeView = findViewById<androidx.compose.ui.platform.ComposeView>(R.id.compose_view)
        composeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MyBatteryTheme {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        BatteryDashboard(
                            viewModel       = viewModel,
                            onNavigateSetup = {
                                startActivity(Intent(this@MainActivity, SetupActivity::class.java)
                                    .putExtra(SetupActivity.EXTRA_SHOW_BACK, true))
                            },
                            onNavigateRaw   = {
                                startActivity(Intent(this@MainActivity, LogRawActivity::class.java))
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        appViewModel.checkOnResume()
    }
}

// ── BatteryDashboard composable ───────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BatteryDashboard(
    viewModel: AppViewModel,
    onNavigateSetup: () -> Unit,
    onNavigateRaw: () -> Unit
) {
    val info             = viewModel.batteryInfo
    val isLoadingDetail  = viewModel.isLoadingDetail || info == null
    val isRefreshing     = viewModel.isRefreshing
    val folderAccessible = viewModel.alreadyHasPerm && viewModel.folderAccessible
    val showLogSheet     = viewModel.showLogSheet
    val allLogEntries    = viewModel.allLogEntries
    val gaugeAmplitude   = viewModel.gaugeAmplitude
    val gaugeReplayKey   = viewModel.gaugeReplayKey

    val arcAnimatable = remember { Animatable(0f) }

    LaunchedEffect(isLoadingDetail) {
        if (isLoadingDetail) viewModel.startGaugePulse()
        else viewModel.stopGaugePulse()
    }

    val alreadyHasData = remember { info?.readSuccess == true }
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

    val effectiveInfo = info ?: BatteryInfo(
        healthPercent = null, cycleCount = null, healthUnsupported = false, readSuccess = false
    )

    val tp    = textPrimary()
    val muted = textMuted()

    ErrorDialogHost(
        errorDialog = viewModel.errorDialog,
        onDismiss   = { viewModel.dismissErrorDialog() }
    )

    Box(Modifier.fillMaxSize()) {
        RescanPullToRefreshBox(
            isRefreshing = isRefreshing,
            onRescan     = { viewModel.rescan() },
            modifier     = Modifier.fillMaxSize()
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(Modifier.height(52.dp))

                // ── Header row ────────────────────────────────────────────────
                Box(
                    Modifier
                        .fillMaxWidth()
                        .alpha(headerAlpha)
                        .offset(y = headerSlide.dp)
                ) {
                    FilledTonalIconButton(
                        onClick  = { if (!isLoadingDetail) viewModel.openLogSheet() },
                        enabled  = !isLoadingDetail,
                        modifier = Modifier.align(Alignment.CenterStart).size(40.dp),
                        colors   = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor   = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            painterResource(R.drawable.arrow_back), "Browse logs",
                            modifier = Modifier.size(20.dp).rotate(-90f)
                        )
                    }

                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "My Battery", color = tp,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(Modifier.height(4.dp))
                        if (isLoadingDetail) {
                            Text("…", color = muted.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.labelSmall)
                            Text("…", color = muted.copy(alpha = 0.4f),
                                style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.height(2.dp))
                            Text("…", color = muted.copy(alpha = 0.3f),
                                style = MaterialTheme.typography.labelSmall)
                        } else {
                            if (effectiveInfo.logFileName.isNotEmpty()) {
                                val displayName = BatteryParser.truncateLogDisplayName(effectiveInfo.logFileName)
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment     = Alignment.CenterVertically
                                ) {
                                    Text(
                                        displayName, color = muted, textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    if (!folderAccessible) {
                                        Spacer(Modifier.width(4.dp))
                                        Surface(
                                            shape = RoundedCornerShape(3.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                                        ) {
                                            Text(
                                                "cached",
                                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp),
                                                color    = muted.copy(alpha = 0.7f),
                                                style    = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 8.sp,
                                                    letterSpacing = 0.sp
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                            if (effectiveInfo.relativeDate.isNotEmpty())
                                Text(
                                    "Log created ${effectiveInfo.relativeDate}",
                                    color = muted, textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            if (effectiveInfo.batteryDateFormatted.isNotEmpty()) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "Installed on ${effectiveInfo.batteryDateFormatted}",
                                    color = muted.copy(alpha = 0.7f), textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }

                    FilledTonalIconButton(
                        onClick  = { if (!isLoadingDetail) onNavigateSetup() },
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

                val isNewBattery = !isLoadingDetail && !effectiveInfo.healthUnsupported &&
                        (effectiveInfo.healthPercent ?: 0) >= 100

                Box(Modifier.alpha(gaugeAlpha).offset(y = gaugeSlide.dp)) {
                    HealthGaugeCard(effectiveInfo, isLoadingDetail, arcAnimatable, gaugeAmplitude, gaugeReplayKey)
                }
                Spacer(Modifier.height(16.dp))
                Box(Modifier.alpha(cardAlpha)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        CycleCountCard(effectiveInfo, isLoadingDetail, isNewBattery, gaugeReplayKey)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "v${BuildConfig.VERSION_NAME}",
                            color = muted.copy(alpha = 0.45f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }

        // Interaction-blocking overlay while loading detail
        if (isLoadingDetail) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        onClick           = {},
                        indication        = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    )
            )
        }

        // Log sheet scrim
        if (showLogSheet && !isLoadingDetail) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        onClick           = { viewModel.dismissLogSheet() },
                        indication        = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    )
            )
        }

        // ── Log sheet drop-down ───────────────────────────────────────────────
        AnimatedVisibility(
            visible = showLogSheet && !isLoadingDetail,
            enter   = slideInVertically(tween(320, easing = FastOutSlowInEasing)) { -it } +
                    fadeIn(tween(220, easing = FastOutSlowInEasing)),
            exit    = slideOutVertically(tween(240, easing = FastOutLinearInEasing)) { -it } +
                    fadeOut(tween(180, easing = FastOutLinearInEasing))
        ) {
            LogSheet(
                allLogEntries   = allLogEntries,
                activeFileName  = effectiveInfo.logFileName,
                onDismiss       = { viewModel.dismissLogSheet() },
                onSelectEntry   = { viewModel.selectLogEntry(it) },
                onReadRaw       = { entry ->
                    viewModel.openRawLog(entry)
                    onNavigateRaw()
                },
                extractTs       = { BatteryParser.extractTimestampFromFileName(it) }
            )
        }
    }
}

// ── Log sheet ─────────────────────────────────────────────────────────────────

@Composable
private fun LogSheet(
    allLogEntries: List<DocEntry>,
    activeFileName: String,
    onDismiss: () -> Unit,
    onSelectEntry: (DocEntry) -> Unit,
    onReadRaw: (DocEntry) -> Unit,
    extractTs: (String) -> Long
) {
    Surface(
        modifier        = Modifier
            .fillMaxWidth()
            .clickable(onClick = {}, indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }),
        color           = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 8.dp,
        shape           = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
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
            val logsScope       = rememberCoroutineScope()

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
                            LogEntry(
                                entry         = entry,
                                isActive      = entry.name == activeFileName,
                                relTime       = relativeTimeFrom(
                                    extractTs(entry.name).takeIf { it != 0L } ?: entry.lastModified
                                ),
                                onSelectEntry = onSelectEntry,
                                onReadRaw     = onReadRaw
                            )
                            if (idx < allLogEntries.lastIndex) {
                                HorizontalDivider(
                                    color    = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                    modifier = Modifier.padding(start = 38.dp, end = 16.dp)
                                )
                            }
                        }
                    }
                }

                if (logsScrollState.maxValue > 0) {
                    val trackHeight    = 200.dp
                    val thumbMinHeight = 32.dp
                    val density        = LocalDensity.current
                    val trackPx        = with(density) { trackHeight.toPx() }
                    val thumbMinPx     = with(density) { thumbMinHeight.toPx() }
                    val fraction       = (logsScrollState.value.toFloat() / logsScrollState.maxValue.toFloat()).coerceIn(0f, 1f)
                    val thumbPx        = (trackPx * 0.35f).coerceAtLeast(thumbMinPx)
                    val thumbTopPx     = fraction * (trackPx - thumbPx)
                    val isDark         = isSystemInDarkTheme()
                    val thumbColor     = if (isDark) Color(0xFF4A6080) else Color(0xFFB0C0D8)
                    val trackColor     = if (isDark) Color(0xFF1A2740) else Color(0xFFE2E8F0)

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
                                    val draggableRange = trackPx - thumbPx
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
                                .height(with(density) { thumbPx.toDp() })
                                .background(thumbColor, RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LogEntry(
    entry: DocEntry,
    isActive: Boolean,
    relTime: String,
    onSelectEntry: (DocEntry) -> Unit,
    onReadRaw: (DocEntry) -> Unit
) {
    val green       = accentGreenEffective()
    val displayName = BatteryParser.truncateLogDisplayName(entry.name)

    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(6.dp).background(if (isActive) green else Color.Transparent, CircleShape))
        Spacer(Modifier.width(12.dp))

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
                Text(
                    displayName,
                    color    = if (isActive) green else MaterialTheme.colorScheme.onSurface,
                    style    = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (relTime.isNotEmpty()) "($relTime)" else "",
                    color    = if (isActive) green.copy(alpha = 0.75f)
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    style    = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

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
}

// ── Relative time helper ──────────────────────────────────────────────────────

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
        else       -> java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.US)
            .format(java.util.Date(tsMs))
    }
}