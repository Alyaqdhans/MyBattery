package com.alyaqdhan.mybattery

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// ── Accent palette ────────────────────────────────────────────────────────────
val AccentGreen  = Color(0xFF00E5A0)
val AccentOrange = Color(0xFFFFAA44)
val AccentRed    = Color(0xFFFF5C6C)
val AccentBlue   = Color(0xFF4DA6FF)

// Darker variants for light theme gauge contrast
val AccentGreenLight  = Color(0xFF007A50)
val AccentOrangeLight = Color(0xFFB85C00)
val AccentRedLight    = Color(0xFFB52233)
val AccentBlueLight   = Color(0xFF1A5FAA)

// ── Dark surface palette ──────────────────────────────────────────────────────
val DarkBgDeep        = Color(0xFF080D18)
val DarkBgCard        = Color(0xFF111827)
val DarkBgCardBorder  = Color(0xFF1F2D42)
val DarkTextPrimary   = Color(0xFFE8EEFF)
val DarkTextSecondary = Color(0xFF7A8BA8)
val DarkTextMuted     = Color(0xFF5A7090)

// ── Light surface palette ─────────────────────────────────────────────────────
val LightBgDeep        = Color(0xFFF1F5F9)
val LightBgCard        = Color(0xFFFFFFFF)
val LightBgCardBorder  = Color(0xFFE2E8F0)
val LightTextPrimary   = Color(0xFF0F172A)
val LightTextSecondary = Color(0xFF475569)
val LightTextMuted     = Color(0xFF64748B)

// ── Classic schemes (Android 11 and below) ────────────────────────────────────
private val ClassicDarkScheme = darkColorScheme(
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

private val ClassicLightScheme = lightColorScheme(
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

// ── App colors ────────────────────────────────────────────────────────────────

/**
 * Colors the screens need that can't be read straight from the [ColorScheme], so that the
 * classic palette and the wallpaper-based one can each supply the right value.
 *
 * The classic schemes use `outline` for hairlines and `outlineVariant` for muted text, the
 * opposite of how Material's dynamic schemes define them. Screens must read those through
 * here, never from the scheme.
 */
@Immutable
data class AppColors(
    val page: Color,
    val card: Color,
    val cardBorder: Color,
    val cornerButton: Color,
    val textMuted: Color,
    val gaugeGreen: Color,
    val gaugeOrange: Color,
    val gaugeRed: Color,
    val gaugeBlue: Color,
    val gaugeGray: Color,
    val warning: Color,
    val refreshIndicator: Color,
    val logKey: Color,
    val logValue: Color,
    val logSection: Color,
    val searchHighlightBg: Color,
    val searchHighlightFg: Color,
    val searchBadgeBg: Color,
    val searchBadgeFg: Color,
    val scrollbarThumb: Color,
    val scrollbarTrack: Color,
)

private val ClassicDarkColors = AppColors(
    page              = Color(0xFF0A0E1A), // Matches values-night/themes.xml
    card              = DarkBgCard,
    cardBorder        = DarkBgCardBorder,
    cornerButton      = ClassicDarkScheme.surfaceContainerHighest,
    textMuted         = DarkTextMuted,
    gaugeGreen        = AccentGreen,
    gaugeOrange       = AccentOrange,
    gaugeRed          = AccentRed,
    gaugeBlue         = AccentBlue,
    gaugeGray         = DarkTextMuted,
    warning           = AccentOrange,
    refreshIndicator  = AccentBlue,
    logKey            = Color(0xFF7A9CC0),
    logValue          = Color(0xFFF0F4FF),
    logSection        = Color(0xFF6BB5FF),
    searchHighlightBg = Color(0xFF3A5030),
    searchHighlightFg = Color(0xFF90EE90),
    searchBadgeBg     = Color(0xFFFFCC00),
    searchBadgeFg     = Color(0xFF1A1200),
    scrollbarThumb    = Color(0xFF4A6080),
    scrollbarTrack    = Color(0xFF1A2740),
)

private val ClassicLightColors = AppColors(
    page              = Color(0xFFFAFAFA), // Matches the Theme.Material.Light window
    card              = LightBgCard,
    cardBorder        = LightBgCardBorder,
    cornerButton      = ClassicLightScheme.surfaceContainerHighest,
    textMuted         = LightTextMuted,
    gaugeGreen        = AccentGreenLight,
    gaugeOrange       = AccentOrangeLight,
    gaugeRed          = AccentRedLight,
    gaugeBlue         = AccentBlueLight,
    gaugeGray         = LightTextMuted,
    warning           = Color(0xFF8B5000),
    refreshIndicator  = AccentBlueLight,
    logKey            = Color(0xFF4A6080),
    logValue          = Color(0xFF0F172A),
    logSection        = Color(0xFF1565C0),
    searchHighlightBg = Color(0xFFD4EDD4),
    searchHighlightFg = Color(0xFF1B5E20),
    searchBadgeBg     = Color(0xFFFFCC00),
    searchBadgeFg     = Color(0xFF1A1200),
    scrollbarThumb    = Color(0xFFB0C0D8),
    scrollbarTrack    = Color(0xFFE2E8F0),
)

/**
 * Android 12+: everything follows the wallpaper except the gauge and warning colors, which
 * stay fixed so that a red wallpaper can't make a healthy battery look like a failing one.
 *
 * Cards and the corner buttons sit well apart from the page so they read clearly: in dark,
 * near-black cards on a lighter page; in light, white cards on a gray page.
 */
private fun dynamicAppColors(scheme: ColorScheme, darkTheme: Boolean) =
    (if (darkTheme) ClassicDarkColors else ClassicLightColors).copy(
        page              = if (darkTheme) scheme.surfaceContainerHighest else scheme.surfaceContainerHigh,
        card              = scheme.surfaceContainerLowest,
        cornerButton      = if (darkTheme) scheme.surfaceContainer else scheme.surfaceContainerLowest,
        cardBorder        = scheme.outlineVariant,
        textMuted         = scheme.outline,
        gaugeGray         = scheme.outline,
        refreshIndicator  = scheme.primary,
        logKey            = scheme.onSurfaceVariant,
        logValue          = scheme.onSurface,
        logSection        = scheme.primary,
        searchHighlightBg = scheme.primaryContainer,
        searchHighlightFg = scheme.onPrimaryContainer,
        searchBadgeBg     = scheme.tertiaryContainer,
        searchBadgeFg     = scheme.onTertiaryContainer,
        scrollbarThumb    = scheme.outline,
        scrollbarTrack    = scheme.surfaceContainerHighest,
    )

private val LocalAppColors = staticCompositionLocalOf<AppColors> {
    error("No AppColors provided: wrap the content in MyBatteryTheme")
}

// ── Theme composable ──────────────────────────────────────────────────────────
@Composable
fun MyBatteryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme: ColorScheme
    val appColors: AppColors

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        colorScheme = systemColorScheme(context, darkTheme)
        appColors   = dynamicAppColors(colorScheme, darkTheme)
    } else {
        colorScheme = if (darkTheme) ClassicDarkScheme else ClassicLightScheme
        appColors   = if (darkTheme) ClassicDarkColors else ClassicLightColors
    }

    MaterialTheme(colorScheme = colorScheme) {
        CompositionLocalProvider(LocalAppColors provides appColors) {
            // No screen paints its own background, so this is the page color app-wide.
            Box(Modifier.fillMaxSize().background(appColors.page)) { content() }
        }
    }
}

// ── Theme-aware color helpers (call inside Composition) ───────────────────────
@Composable fun appColors()             = LocalAppColors.current
@Composable fun cardColor()             = appColors().card
@Composable fun cardBorderColor()       = appColors().cardBorder
@Composable fun cornerButtonColor()     = appColors().cornerButton
@Composable fun textPrimary()           = MaterialTheme.colorScheme.onBackground
@Composable fun textSecondary()         = MaterialTheme.colorScheme.onSurfaceVariant
@Composable fun textMuted()             = appColors().textMuted
@Composable fun accentGreenEffective()  = MaterialTheme.colorScheme.primary
@Composable fun warningColor()          = appColors().warning
@Composable fun refreshIndicatorColor() = appColors().refreshIndicator

@Composable fun gaugeGreen()  = appColors().gaugeGreen
@Composable fun gaugeOrange() = appColors().gaugeOrange
@Composable fun gaugeRed()    = appColors().gaugeRed
@Composable fun gaugeBlue()   = appColors().gaugeBlue
@Composable fun gaugeGray()   = appColors().gaugeGray
