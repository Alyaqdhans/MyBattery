package com.alyaqdhan.mybattery

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemColorsTest {

    // Android 14's stock primary role, which One UI leaves in place.
    private val stockBlue = Color(0xFF415F91)

    @Test
    fun `a color matches itself`() {
        assertTrue(sameHue(stockBlue, stockBlue))
    }

    @Test
    fun `lighter and darker tones of one hue match`() {
        assertTrue(sameHue(Color(0xFF8B4A62), Color(0xFFFFB0CB)))
    }

    @Test
    fun `stock blue doesn't match a red or green palette`() {
        assertFalse(sameHue(stockBlue, Color(0xFF904A43)))
        assertFalse(sameHue(stockBlue, Color(0xFF406836)))
    }

    @Test
    fun `stock blue doesn't match a vivid purple palette`() {
        // Android 17's Vibrant style from a purple seed, 30° from stock blue
        assertFalse(sameHue(stockBlue, Color(0xFF6C39DC)))
    }

    @Test
    fun `roles a few degrees off the palette still match`() {
        // The widest gap on stock Android 17: Expressive style, dark, from a yellow seed
        assertTrue(sameHue(Color(0xFFFFF0C5), Color(0xFFDEC56D)))
    }

    @Test
    fun `hues either side of zero degrees match`() {
        assertTrue(sameHue(oklch(hue = 355f), oklch(hue = 5f)))
        assertFalse(sameHue(oklch(hue = 340f), oklch(hue = 10f)))
    }

    @Test
    fun `two grays match`() {
        assertTrue(sameHue(Color(0xFF5E5E5E), Color(0xFF606062)))
    }

    @Test
    fun `stock blue doesn't match a gray palette`() {
        assertFalse(sameHue(stockBlue, Color(0xFF5E5E5E)))
    }

    @Test
    fun `provided tones come back unchanged`() {
        val range = grayRange()
        TonalRange.TONES.forEach { tone ->
            assertEquals(lightness(gray(tone)), lightness(range.tone(tone)), 0.01f)
        }
    }

    @Test
    fun `a missing tone gets its exact lightness`() {
        val range = tintedRange()
        listOf(98, 96, 94, 92, 87, 24, 22, 17, 12, 6, 4).forEach { tone ->
            assertEquals("tone $tone", tone.toFloat(), lightness(range.tone(tone)), 0.6f)
        }
    }

    @Test
    fun `a missing tone keeps the palette's tint`() {
        val lab = tintedRange().tone(94).convert(ColorSpaces.CieLab)
        assertEquals(0f, lab.green, 0.5f)
        assertEquals(-4f, lab.blue, 0.5f)
    }

    @Test
    fun `the scheme takes the same tones as Android 12`() {
        val p = tintedRange()
        val light = paletteColorScheme(p, p, p, p, darkTheme = false)
        val dark = paletteColorScheme(p, p, p, p, darkTheme = true)
        assertEquals(p.tone(40), light.primary)
        assertEquals(p.tone(80), dark.primary)
        assertEquals(98f, lightness(light.background), 0.6f)
        assertEquals(6f, lightness(dark.background), 0.6f)
        assertEquals(94f, lightness(light.surfaceContainer), 0.6f)
        assertEquals(12f, lightness(dark.surfaceContainer), 0.6f)
    }

    private fun lightness(color: Color) = color.convert(ColorSpaces.CieLab).red

    private fun oklch(hue: Float, chroma: Float = 0.1f): Color {
        val radians = Math.toRadians(hue.toDouble())
        val a = (chroma * Math.cos(radians)).toFloat()
        val b = (chroma * Math.sin(radians)).toFloat()
        return Color(0.6f, a, b, 1f, ColorSpaces.Oklab).convert(ColorSpaces.Srgb)
    }

    private fun gray(tone: Int) = Color(tone.toFloat(), 0f, 0f, 1f, ColorSpaces.CieLab).convert(ColorSpaces.Srgb)

    private fun grayRange() = TonalRange(TonalRange.TONES.map(::gray))

    // A low-chroma blue, like Android's neutral variant palette.
    private fun tintedRange() = TonalRange(
        TonalRange.TONES.map { tone ->
            val chroma = if (tone == 0 || tone == 100) 0f else 4f
            Color(tone.toFloat(), 0f, -chroma, 1f, ColorSpaces.CieLab).convert(ColorSpaces.Srgb)
        }
    )
}
