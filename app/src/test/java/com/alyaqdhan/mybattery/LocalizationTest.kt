package com.alyaqdhan.mybattery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class LocalizationTest {

    private val minute = 60_000L
    private val hour   = 60 * minute
    private val day    = 24 * hour

    // ── Digits ────────────────────────────────────────────────────────────────

    @Test
    fun `every western digit becomes its arabic form`() {
        assertEquals("٠١٢٣٤٥٦٧٨٩", "0123456789".toArabicDigits())
    }

    @Test
    fun `every arabic digit becomes its western form`() {
        assertEquals("0123456789", "٠١٢٣٤٥٦٧٨٩".toWesternDigits())
    }

    @Test
    fun `everything else is left alone`() {
        assertEquals("*#٩٩٠٠#", "*#9900#".toArabicDigits())
        assertEquals("level=٨٤", "level=84".toArabicDigits())
        assertEquals("*#9900#", "*#٩٩٠٠#".toWesternDigits())
    }

    @Test
    fun `western digits are unchanged by toWesternDigits`() {
        assertEquals("*#9900", "*#9900".toWesternDigits())
    }

    // ── Time since a log was created ──────────────────────────────────────────

    @Test
    fun `under a minute is just now`() {
        assertNull(timeAgo(0))
        assertNull(timeAgo(minute - 1))
    }

    @Test
    fun `a time in the future is just now`() {
        assertNull(timeAgo(-5 * day))
    }

    @Test
    fun `minutes run up to an hour`() {
        assertEquals(TimeAgo(R.plurals.time_minutes, 1), timeAgo(minute))
        assertEquals(TimeAgo(R.plurals.time_minutes, 59), timeAgo(hour - 1))
    }

    @Test
    fun `hours run up to a day`() {
        assertEquals(TimeAgo(R.plurals.time_hours, 1), timeAgo(hour))
        assertEquals(TimeAgo(R.plurals.time_hours, 23), timeAgo(day - 1))
    }

    @Test
    fun `days run up to thirty`() {
        assertEquals(TimeAgo(R.plurals.time_days, 1), timeAgo(day))
        assertEquals(TimeAgo(R.plurals.time_days, 29), timeAgo(30 * day - 1))
    }

    @Test
    fun `months run up to a year`() {
        assertEquals(TimeAgo(R.plurals.time_months, 1), timeAgo(30 * day))
        assertEquals(TimeAgo(R.plurals.time_months, 12), timeAgo(365 * day - 1))
    }

    @Test
    fun `years have no upper limit`() {
        assertEquals(TimeAgo(R.plurals.time_years, 1), timeAgo(365 * day))
        assertEquals(TimeAgo(R.plurals.time_years, 101), timeAgo(37_000 * day))
    }

    // ── Battery date ──────────────────────────────────────────────────────────

    private val date = SimpleDateFormat("yyyyMMdd", Locale.US).parse("20240105")!!.time

    @Test
    fun `an unknown date is empty`() {
        assertEquals("", formatBatteryDate(0, arabic = false))
        assertEquals("", formatBatteryDate(0, arabic = true))
    }

    @Test
    fun `english date is day month year`() {
        assertEquals("5/1/2024", formatBatteryDate(date, arabic = false))
    }

    @Test
    fun `arabic date uses arabic digits and reads from the right`() {
        assertEquals("٥‏/١‏/٢٠٢٤", formatBatteryDate(date, arabic = true))
    }

    // ── Arabic text ───────────────────────────────────────────────────────────

    @Test
    fun `arabic strings have no western digits`() {
        val strings = File("src/main/res/values-ar/strings.xml").readText()
            .replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""\\u[0-9a-fA-F]{4}"""), "")   // Direction marks
            .replace(Regex("""%\d+\$[ds]"""), "")          // Format arguments
            .lines()
            .filterNot { it.contains("<?xml") }
            .joinToString("\n")
            .replace(Regex("""name="[^"]*""""), "")        // Resource names
        val line = strings.lines().firstOrNull { it.contains(Regex("[0-9]")) }
        assertNull("Western digit in: $line", line)
    }
}
