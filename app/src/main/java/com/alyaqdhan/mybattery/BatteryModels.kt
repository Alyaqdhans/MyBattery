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
    val firstUseDateMs: Long = 0L,
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
            return when {
                mins  < 1  -> "just now"
                mins  < 60 -> "$mins min${if (mins == 1L) "" else "s"} ago"
                hours < 24 -> "$hours hour${if (hours == 1L) "" else "s"} ago"
                days  < 7  -> "$days day${if (days == 1L) "" else "s"} ago"
                else       -> SimpleDateFormat("MMM dd, yyyy", Locale.US)
                    .format(java.util.Date(logTimestampMs))
            }
        }

    val firstUseDateFormatted: String
        get() {
            if (firstUseDateMs == 0L) return ""
            return SimpleDateFormat("d/M/yyyy", Locale.US)
                .format(java.util.Date(firstUseDateMs))
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