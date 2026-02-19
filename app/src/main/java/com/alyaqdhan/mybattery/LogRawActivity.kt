package com.alyaqdhan.mybattery

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

class LogRawActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
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

        val composeView = findViewById<androidx.compose.ui.platform.ComposeView>(R.id.compose_view)
        composeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MyBatteryTheme {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
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
    val isDark = isSystemInDarkTheme()

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

    var searchQuery  by remember { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    LaunchedEffect(searchActive) {
        if (searchActive) searchFocusRequester.requestFocus()
    }

    val highlightBg = if (isDark) Color(0xFF3A5030) else Color(0xFFD4EDD4)
    val highlightFg = if (isDark) Color(0xFF90EE90) else Color(0xFF1B5E20)

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
                Icon(painterResource(R.drawable.arrow_back), "Back", tint = ts)
            }

            if (searchActive) {
                TextField(
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
                if (searchQuery.isNotBlank()) {
                    val matchCount = remember(searchQuery, text) {
                        if (searchQuery.isBlank()) 0
                        else {
                            var count = 0; var idx = 0
                            val lower  = text.lowercase()
                            val lowerQ = searchQuery.lowercase()
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
                            .background(Color(0xFFFFCC00), RoundedCornerShape(10.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "$matchCount",
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
                    Text(
                        "Battery Lines", color = tp,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
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
            SelectionContainer {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    val colorizedText = buildColorizedLogText(text, keyColor, valueColor, sectionColor)
                    val displayText = if (searchQuery.isNotBlank()) {
                        buildSearchHighlightedText(colorizedText, searchQuery, highlightBg, highlightFg)
                    } else {
                        colorizedText
                    }
                    Text(
                        text  = displayText,
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
