package com.alyaqdhan.mybattery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

class LogRawActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_log_raw)

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

        // The log text only lives in memory, so there's nothing to show after a restart.
        if (viewModel.selectedLogFileName.isEmpty()) {
            finish()
            return
        }

        val composeView = findViewById<androidx.compose.ui.platform.ComposeView>(R.id.compose_view)
        composeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MyBatteryTheme {
                    LogRawScreen(
                        fileName  = viewModel.selectedLogFileName,
                        text      = viewModel.rawLogText,
                        isLoading = viewModel.isLoadingRaw,
                        onBack    = { finish() }
                    )
                }
            }
        }
    }
}

// ── LogRawScreen composable ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LogRawScreen(
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
    val colors = appColors()

    var backHandled by remember { mutableStateOf(false) }
    BackHandler(enabled = true) {
        if (!backHandled) { backHandled = true; onBack() }
    }

    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(300), label = "raw_fade")

    var searchQuery  by remember { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    // The log uses Western digits, but an Arabic keyboard can type Eastern ones.
    val logQuery = searchQuery.toWesternDigits()
    LaunchedEffect(searchActive) {
        if (searchActive) searchFocusRequester.requestFocus()
    }

    Column(Modifier.fillMaxSize().alpha(alpha)) {

        // ── Top bar ───────────────────────────────────────────────────────────
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp)
                .padding(top = 52.dp)
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { if (!backHandled) { backHandled = true; onBack() } }) {
                Icon(painterResource(R.drawable.arrow_back), stringResource(R.string.action_back), tint = ts)
            }

            if (searchActive) {
                TextField(
                    value         = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier      = Modifier.weight(1f).focusRequester(searchFocusRequester),
                    placeholder   = { Text(stringResource(R.string.search_placeholder), style = MaterialTheme.typography.bodyMedium) },
                    singleLine    = true,
                    colors        = TextFieldDefaults.colors(
                        focusedContainerColor   = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor   = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    // Follows what's typed: searches are usually English, like the log itself.
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily    = FontFamily.Monospace,
                        color         = tp,
                        textDirection = TextDirection.Content
                    )
                )
                if (searchQuery.isNotBlank()) {
                    val matchCount = remember(logQuery, text) {
                        if (logQuery.isBlank()) 0
                        else {
                            var count = 0; var idx = 0
                            val lower  = text.lowercase()
                            val lowerQ = logQuery.lowercase()
                            while (true) {
                                val found = lower.indexOf(lowerQ, idx)
                                if (found < 0) break
                                count++; idx = found + lowerQ.length
                            }
                            count
                        }
                    }
                    Box(
                        Modifier
                            .background(colors.searchBadgeBg, RoundedCornerShape(10.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "$matchCount".localDigits(),
                            color = colors.searchBadgeFg,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                }
                IconButton(onClick = { searchQuery = ""; searchActive = false }) {
                    Icon(painterResource(R.drawable.close), stringResource(R.string.search_clear), tint = ts)
                }
            } else {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.raw_title), color = tp,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    if (fileName.isNotEmpty()) {
                        Text(fileName, color = muted,
                            style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
                IconButton(onClick = { searchActive = true }) {
                    Icon(painterResource(R.drawable.search), stringResource(R.string.action_search), tint = ts)
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
                    Text(stringResource(R.string.raw_extracting), color = ts,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            // Log lines are English technical text, so they stay left-to-right in Arabic too.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                SelectionContainer {
                    val lines = remember(text) { text.lines() }
                    val lineStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily    = FontFamily.Monospace,
                        fontSize      = 11.sp,
                        lineHeight    = 17.sp,
                        textDirection = TextDirection.Ltr
                    )
                    // One item per line, so a big log only lays out and colors what's on screen.
                    LazyColumn(
                        modifier       = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 48.dp)
                    ) {
                        items(lines) { line ->
                            val displayLine = remember(line, logQuery, colors) {
                                buildSearchHighlightedText(
                                    buildColorizedLogLine(line, colors.logKey, colors.logValue, colors.logSection),
                                    logQuery, colors.searchHighlightBg, colors.searchHighlightFg
                                )
                            }
                            Text(text = displayLine, modifier = Modifier.fillMaxWidth(), style = lineStyle)
                        }
                    }
                }
            }
        }
    }
}
