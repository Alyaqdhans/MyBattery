package com.alyaqdhan.mybattery

import android.content.SharedPreferences
import androidx.core.content.edit

object BatteryCache {

    fun cacheBatteryInfo(prefs: SharedPreferences, info: BatteryInfo) {
        if (!info.readSuccess) return
        prefs.edit {
            putString ("cache_log_file_name",      info.logFileName)
            putLong   ("cache_log_timestamp_ms",   info.logTimestampMs)
            putInt    ("cache_health_percent",      info.healthPercent ?: -1)
            putString ("cache_health_source",      info.healthSource)
            putBoolean("cache_health_unsupported", info.healthUnsupported)
            putInt    ("cache_cycle_count",         info.cycleCount ?: -1)
            putLong   ("cache_battery_date_ms",  info.batteryDateMs)
        }
    }

    fun loadCachedBatteryInfo(prefs: SharedPreferences): BatteryInfo? {
        val fileName = prefs.getString("cache_log_file_name", null) ?: return null
        if (fileName.isBlank()) return null
        return BatteryInfo(
            healthPercent     = prefs.getInt("cache_health_percent", -1).takeIf { it >= 0 },
            healthSource      = prefs.getString("cache_health_source", "") ?: "",
            healthUnsupported = prefs.getBoolean("cache_health_unsupported", false),
            cycleCount        = prefs.getInt("cache_cycle_count", -1).takeIf { it >= 0 },
            batteryDateMs    = prefs.getLong("cache_battery_date_ms", 0L),
            logFileName       = fileName,
            logTimestampMs    = prefs.getLong("cache_log_timestamp_ms", 0L),
            readSuccess       = true
        )
    }

    fun clearBatteryInfoCache(prefs: SharedPreferences) {
        prefs.edit {
            remove("cache_log_file_name")
            remove("cache_log_timestamp_ms")
            remove("cache_health_percent")
            remove("cache_health_source")
            remove("cache_health_unsupported")
            remove("cache_cycle_count")
            remove("cache_battery_date_ms")
        }
    }
}