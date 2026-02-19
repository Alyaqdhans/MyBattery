package com.alyaqdhan.mybattery

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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

// ── Theme composable ──────────────────────────────────────────────────────────
@Composable
fun MyBatteryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
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

// ── Theme-aware color helpers (call inside Composition) ───────────────────────
@Composable fun cardBorderColor()      = MaterialTheme.colorScheme.outline
@Composable fun textPrimary()          = MaterialTheme.colorScheme.onBackground
@Composable fun textSecondary()        = MaterialTheme.colorScheme.onSurfaceVariant
@Composable fun textMuted()            = MaterialTheme.colorScheme.outlineVariant
@Composable fun accentGreenEffective() = MaterialTheme.colorScheme.primary

@Composable fun gaugeGreen()  = if (isSystemInDarkTheme()) AccentGreen  else AccentGreenLight
@Composable fun gaugeOrange() = if (isSystemInDarkTheme()) AccentOrange else AccentOrangeLight
@Composable fun gaugeRed()    = if (isSystemInDarkTheme()) AccentRed    else AccentRedLight
@Composable fun gaugeBlue()   = if (isSystemInDarkTheme()) AccentBlue   else AccentBlueLight
@Composable fun gaugeGray()   = MaterialTheme.colorScheme.outlineVariant
