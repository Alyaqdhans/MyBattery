package com.alyaqdhan.mybattery

import androidx.annotation.PluralsRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val ARABIC_ZERO = '٠'
private const val ARABIC_NINE = '٩'

fun String.toArabicDigits(): String =
    buildString(length) {
        for (c in this@toArabicDigits) append(if (c in '0'..'9') ARABIC_ZERO + (c - '0') else c)
    }

fun String.toWesternDigits(): String =
    buildString(length) {
        for (c in this@toWesternDigits) append(if (c in ARABIC_ZERO..ARABIC_NINE) '0' + (c - ARABIC_ZERO) else c)
    }

@Composable
fun isArabicUi(): Boolean = LocalConfiguration.current.locales[0].language == "ar"

/**
 * Digits the app writes itself. Arabic always gets Eastern Arabic numerals, even in regions whose
 * locale defaults to Western ones (UAE, Morocco…), so don't rely on the locale's own formatting.
 *
 * Leave file names and log text alone: those are literal data the user may need to match.
 */
@Composable
fun String.localDigits(): String = if (isArabicUi()) toArabicDigits() else this

internal data class TimeAgo(@param:PluralsRes val plural: Int, val count: Int)

/** Returns null when the difference is under a minute ("just now"). */
internal fun timeAgo(diffMs: Long): TimeAgo? {
    val mins   = diffMs / 60_000
    val hours  = diffMs / 3_600_000
    val days   = diffMs / 86_400_000
    val months = days / 30
    val years  = days / 365
    return when {
        mins  < 1  -> null
        mins  < 60 -> TimeAgo(R.plurals.time_minutes, mins.toInt())
        hours < 24 -> TimeAgo(R.plurals.time_hours, hours.toInt())
        days  < 30 -> TimeAgo(R.plurals.time_days, days.toInt())
        years < 1  -> TimeAgo(R.plurals.time_months, months.toInt())
        else       -> TimeAgo(R.plurals.time_years, years.toInt())
    }
}

/** "3 days ago", or an empty string when [timestampMs] is unknown (0). */
@Composable
fun relativeTimeText(timestampMs: Long): String {
    if (timestampMs == 0L) return ""
    val ago = timeAgo(System.currentTimeMillis() - timestampMs)
        ?: return stringResource(R.string.time_just_now)
    return pluralStringResource(ago.plural, ago.count, ago.count).localDigits()
}

internal fun formatBatteryDate(dateMs: Long, arabic: Boolean): String {
    if (dateMs == 0L) return ""
    // The right-to-left marks stop the digits and slashes merging into one left-to-right number,
    // so the day still comes first for someone reading from the right.
    val pattern = if (arabic) "d‏/M‏/yyyy" else "d/M/yyyy"
    val date    = SimpleDateFormat(pattern, Locale.US).format(Date(dateMs))
    return if (arabic) date.toArabicDigits() else date
}

/** The battery install date, or an empty string when the log didn't have one (0). */
@Composable
fun batteryDateText(dateMs: Long): String = formatBatteryDate(dateMs, isArabicUi())
