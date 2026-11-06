package com.alyaqdhan.mybattery

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
)

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