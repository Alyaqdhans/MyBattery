package com.alyaqdhan.mybattery

import org.junit.Assert.assertEquals
import org.junit.Test

class ScrollbarTest {

    @Test
    fun `content that fits has no scroll range`() {
        assertEquals(0f, scrollbarFraction(value = 0, maxValue = 0), 0f)
    }

    @Test
    fun `a range that shrank to nothing stays at the start`() {
        assertEquals(0f, scrollbarFraction(value = 120, maxValue = 0), 0f)
    }

    @Test
    fun `the thumb follows the scroll position`() {
        assertEquals(0f, scrollbarFraction(value = 0, maxValue = 400), 0f)
        assertEquals(0.5f, scrollbarFraction(value = 200, maxValue = 400), 0f)
        assertEquals(1f, scrollbarFraction(value = 400, maxValue = 400), 0f)
    }

    @Test
    fun `an unmeasured range doesn't overflow`() {
        assertEquals(0f, scrollbarFraction(value = 0, maxValue = Int.MAX_VALUE), 0f)
    }

    @Test
    fun `a position past the end stops at the end of the track`() {
        assertEquals(1f, scrollbarFraction(value = 500, maxValue = 400), 0f)
    }
}
