package com.alyaqdhan.mybattery

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import android.R.color as Sys

/**
 * The wallpaper-based scheme for Android 12+.
 *
 * From Android 14, Material builds its scheme from the system's color roles
 * (`system_primary_light` and the rest). One UI fills the wallpaper palettes but has been
 * reported to leave those roles at Android's stock blue, so on Samsung phones the app ignored
 * the user's color palette. When the roles' primary doesn't share the palette's hue, the
 * scheme is built from the palettes instead, the way Material does on Android 12 and 13.
 */
@RequiresApi(Build.VERSION_CODES.S)
internal fun systemColorScheme(context: Context, darkTheme: Boolean): ColorScheme =
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || rolesFollowPalette(context, darkTheme)) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        paletteColorScheme(
            primary        = context.palette(Sys.system_accent1_0, Sys.system_accent1_10, Sys.system_accent1_50, Sys.system_accent1_100, Sys.system_accent1_200, Sys.system_accent1_300, Sys.system_accent1_400, Sys.system_accent1_500, Sys.system_accent1_600, Sys.system_accent1_700, Sys.system_accent1_800, Sys.system_accent1_900, Sys.system_accent1_1000),
            secondary      = context.palette(Sys.system_accent2_0, Sys.system_accent2_10, Sys.system_accent2_50, Sys.system_accent2_100, Sys.system_accent2_200, Sys.system_accent2_300, Sys.system_accent2_400, Sys.system_accent2_500, Sys.system_accent2_600, Sys.system_accent2_700, Sys.system_accent2_800, Sys.system_accent2_900, Sys.system_accent2_1000),
            tertiary       = context.palette(Sys.system_accent3_0, Sys.system_accent3_10, Sys.system_accent3_50, Sys.system_accent3_100, Sys.system_accent3_200, Sys.system_accent3_300, Sys.system_accent3_400, Sys.system_accent3_500, Sys.system_accent3_600, Sys.system_accent3_700, Sys.system_accent3_800, Sys.system_accent3_900, Sys.system_accent3_1000),
            neutralVariant = context.palette(Sys.system_neutral2_0, Sys.system_neutral2_10, Sys.system_neutral2_50, Sys.system_neutral2_100, Sys.system_neutral2_200, Sys.system_neutral2_300, Sys.system_neutral2_400, Sys.system_neutral2_500, Sys.system_neutral2_600, Sys.system_neutral2_700, Sys.system_neutral2_800, Sys.system_neutral2_900, Sys.system_neutral2_1000),
            darkTheme      = darkTheme,
        )
    }

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
private fun rolesFollowPalette(context: Context, darkTheme: Boolean): Boolean {
    val role = if (darkTheme) Sys.system_primary_dark else Sys.system_primary_light
    // The palette tone Material's Android 12 scheme uses for primary: 80 in dark, 40 in light.
    val palette = if (darkTheme) Sys.system_accent1_200 else Sys.system_accent1_600
    return sameHue(Color(context.getColor(role)), Color(context.getColor(palette)))
}

private fun Context.palette(vararg ids: Int) = TonalRange(ids.map { Color(getColor(it)) })

/** Below this Oklab chroma a color reads as gray, so its hue means nothing. */
private const val GRAY_CHROMA = 0.02f

/**
 * On stock Android 17 the roles stay within 3° of the palette across every palette style, so 15°
 * leaves room while still catching a purple palette next to stock blue.
 */
private const val MAX_HUE_DIFFERENCE = 15f

/** Whether two colors share a hue, in Oklab. Two grays match; a gray and a clear color don't. */
internal fun sameHue(a: Color, b: Color): Boolean {
    val (chromaA, hueA) = a.chromaAndHue()
    val (chromaB, hueB) = b.chromaAndHue()
    // A gray matches only something close to gray; the margin keeps a near-gray pair from
    // failing because one of them landed just past the line.
    if (minOf(chromaA, chromaB) < GRAY_CHROMA) return maxOf(chromaA, chromaB) < 2 * GRAY_CHROMA
    val difference = abs(hueA - hueB) % 360f
    return minOf(difference, 360f - difference) <= MAX_HUE_DIFFERENCE
}

private fun Color.chromaAndHue(): Pair<Float, Float> {
    val lab = convert(ColorSpaces.Oklab)
    val hue = Math.toDegrees(atan2(lab.blue, lab.green).toDouble()).toFloat()
    return hypot(lab.green, lab.blue) to hue
}

/** A tonal palette as Android provides it, at tones 100, 99, 95, 90, 80, 70 … 10 and 0. */
internal class TonalRange(private val colors: List<Color>) {
    init {
        require(colors.size == TONES.size) { "Expected ${TONES.size} tones, got ${colors.size}" }
    }

    private val lab by lazy { colors.map { it.convert(ColorSpaces.CieLab) } }

    /**
     * The color at [tone]. Android leaves out some tones Material uses, such as 98 and 12, so
     * those keep the exact lightness and blend the color of the two nearest tones.
     */
    fun tone(tone: Int): Color {
        val exact = TONES.indexOf(tone)
        if (exact >= 0) return colors[exact]
        val lighter = TONES.indexOfLast { it > tone }
        val f = (tone - TONES[lighter + 1]).toFloat() / (TONES[lighter] - TONES[lighter + 1])
        val a = lab[lighter + 1].green + (lab[lighter].green - lab[lighter + 1].green) * f
        val b = lab[lighter + 1].blue + (lab[lighter].blue - lab[lighter + 1].blue) * f
        return Color(tone.toFloat(), a, b, 1f, ColorSpaces.CieLab).convert(ColorSpaces.Srgb)
    }

    companion object {
        val TONES = listOf(100, 99, 95, 90, 80, 70, 60, 50, 40, 30, 20, 10, 0)
    }
}

/** Material's Android 12 and 13 dynamic scheme, built from the system palettes. */
internal fun paletteColorScheme(
    primary: TonalRange,
    secondary: TonalRange,
    tertiary: TonalRange,
    neutralVariant: TonalRange,
    darkTheme: Boolean,
): ColorScheme {
    val p = primary::tone
    val s = secondary::tone
    val t = tertiary::tone
    val n = neutralVariant::tone
    return if (darkTheme) darkColorScheme(
        primary = p(80), onPrimary = p(20), primaryContainer = p(30), onPrimaryContainer = p(90),
        inversePrimary = p(40),
        secondary = s(80), onSecondary = s(20), secondaryContainer = s(30), onSecondaryContainer = s(90),
        tertiary = t(80), onTertiary = t(20), tertiaryContainer = t(30), onTertiaryContainer = t(90),
        background = n(6), onBackground = n(90), surface = n(6), onSurface = n(90),
        surfaceVariant = n(30), onSurfaceVariant = n(80),
        inverseSurface = n(90), inverseOnSurface = n(20),
        outline = n(60), outlineVariant = n(30), scrim = n(0),
        surfaceBright = n(24), surfaceDim = n(6),
        surfaceContainer = n(12), surfaceContainerHigh = n(17), surfaceContainerHighest = n(22),
        surfaceContainerLow = n(10), surfaceContainerLowest = n(4),
        surfaceTint = p(80),
        primaryFixed = p(90), primaryFixedDim = p(80), onPrimaryFixed = p(10), onPrimaryFixedVariant = p(30),
        secondaryFixed = s(90), secondaryFixedDim = s(80), onSecondaryFixed = s(10), onSecondaryFixedVariant = s(30),
        tertiaryFixed = t(90), tertiaryFixedDim = t(80), onTertiaryFixed = t(10), onTertiaryFixedVariant = t(30),
    ) else lightColorScheme(
        primary = p(40), onPrimary = p(100), primaryContainer = p(90), onPrimaryContainer = p(10),
        inversePrimary = p(80),
        secondary = s(40), onSecondary = s(100), secondaryContainer = s(90), onSecondaryContainer = s(10),
        tertiary = t(40), onTertiary = t(100), tertiaryContainer = t(90), onTertiaryContainer = t(10),
        background = n(98), onBackground = n(10), surface = n(98), onSurface = n(10),
        surfaceVariant = n(90), onSurfaceVariant = n(30),
        inverseSurface = n(20), inverseOnSurface = n(95),
        outline = n(50), outlineVariant = n(80), scrim = n(0),
        surfaceBright = n(98), surfaceDim = n(87),
        surfaceContainer = n(94), surfaceContainerHigh = n(92), surfaceContainerHighest = n(90),
        surfaceContainerLow = n(96), surfaceContainerLowest = n(100),
        surfaceTint = p(40),
        primaryFixed = p(90), primaryFixedDim = p(80), onPrimaryFixed = p(10), onPrimaryFixedVariant = p(30),
        secondaryFixed = s(90), secondaryFixedDim = s(80), onSecondaryFixed = s(10), onSecondaryFixedVariant = s(30),
        tertiaryFixed = t(90), tertiaryFixedDim = t(80), onTertiaryFixed = t(10), onTertiaryFixedVariant = t(30),
    )
}
