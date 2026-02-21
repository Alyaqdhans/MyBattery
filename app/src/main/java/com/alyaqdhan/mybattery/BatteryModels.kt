package com.alyaqdhan.mybattery

import java.text.SimpleDateFormat
import java.util.Locale

data class BatteryInfo(
    val healthPercent: Int?,
    val healthSource: String = "",
    val healthUnsupported: Boolean = false,
    val cycleCount: Int?,
    val asocRaw: String = "",
    val bsohRaw: String = "",
    val usageRaw: String = "",
    val llbType: String = "",
    val batteryDateMs: Long = 0L,
    val logFileName: String = "",
    val logTimestampMs: Long = 0L,
    val readSuccess: Boolean = false,
    val errorMessage: String = ""
) {
    val relativeDate: String
        get() {
            if (logTimestampMs == 0L) return ""
            val diffMs = System.currentTimeMillis() - logTimestampMs
            val mins   = diffMs / 60_000
            val hours  = diffMs / 3_600_000
            val days   = diffMs / 86_400_000
            val months = days / 30
            val years  = days / 365
            return when {
                mins  < 1   -> "just now"
                mins  < 60  -> "$mins min${if (mins == 1L) "" else "s"} ago"
                hours < 24  -> "$hours hour${if (hours == 1L) "" else "s"} ago"
                days  < 30  -> "$days day${if (days == 1L) "" else "s"} ago"
                years < 1   -> "$months month${if (months == 1L) "" else "s"} ago"
                else        -> "$years year${if (years == 1L) "" else "s"} ago"
            }
        }

    val batteryDateFormatted: String
        get() {
            if (batteryDateMs == 0L) return ""
            return SimpleDateFormat("d/M/yyyy", Locale.US)
                .format(java.util.Date(batteryDateMs))
        }
}

data class DocEntry(
    val id: String,
    val name: String,
    val mime: String,
    val lastModified: Long = 0L
)

/** Overlay error dialogs — replaces the three full-screen error routes. */
sealed class ErrorDialog {
    data class WrongFolder(val errorMessage: String) : ErrorDialog()
    object FolderDeleted  : ErrorDialog()
    object PermissionLost : ErrorDialog()
}