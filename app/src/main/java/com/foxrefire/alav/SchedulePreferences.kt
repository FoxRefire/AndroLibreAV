package com.foxrefire.alav

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.TimeUnit

/**
 * Preferences for periodic scan and auto rules update.
 */
object SchedulePreferences {

    private const val PREFS_NAME = "app_prefs"
    private const val KEY_PERIODIC_SCAN_ENABLED = "periodic_scan_enabled"
    private const val KEY_PERIODIC_SCAN_INTERVAL = "periodic_scan_interval"
    private const val KEY_AUTO_RULES_UPDATE_ENABLED = "auto_rules_update_enabled"
    private const val KEY_AUTO_RULES_UPDATE_INTERVAL = "auto_rules_update_interval"

    const val INTERVAL_DAILY = "daily"
    const val INTERVAL_3DAYS = "3days"
    const val INTERVAL_WEEKLY = "weekly"

    val scanIntervalEntries = listOf(
        INTERVAL_DAILY to "settings_interval_daily",
        INTERVAL_3DAYS to "settings_interval_3days",
        INTERVAL_WEEKLY to "settings_interval_weekly"
    )

    val rulesIntervalEntries = listOf(
        INTERVAL_DAILY to "settings_interval_daily",
        INTERVAL_WEEKLY to "settings_interval_weekly"
    )

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getPeriodicScanEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PERIODIC_SCAN_ENABLED, false)

    fun setPeriodicScanEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_PERIODIC_SCAN_ENABLED, value).apply()
    }

    fun getPeriodicScanInterval(context: Context): String =
        prefs(context).getString(KEY_PERIODIC_SCAN_INTERVAL, INTERVAL_DAILY) ?: INTERVAL_DAILY

    fun setPeriodicScanInterval(context: Context, value: String) {
        prefs(context).edit().putString(KEY_PERIODIC_SCAN_INTERVAL, value).apply()
    }

    fun getAutoRulesUpdateEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_RULES_UPDATE_ENABLED, false)

    fun setAutoRulesUpdateEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_RULES_UPDATE_ENABLED, value).apply()
    }

    fun getAutoRulesUpdateInterval(context: Context): String =
        prefs(context).getString(KEY_AUTO_RULES_UPDATE_INTERVAL, INTERVAL_DAILY) ?: INTERVAL_DAILY

    fun setAutoRulesUpdateInterval(context: Context, value: String) {
        prefs(context).edit().putString(KEY_AUTO_RULES_UPDATE_INTERVAL, value).apply()
    }

    fun scanIntervalMinutes(interval: String): Long = when (interval) {
        INTERVAL_DAILY -> TimeUnit.DAYS.toMinutes(1)
        INTERVAL_3DAYS -> TimeUnit.DAYS.toMinutes(3)
        INTERVAL_WEEKLY -> TimeUnit.DAYS.toMinutes(7)
        else -> TimeUnit.DAYS.toMinutes(1)
    }

    fun rulesIntervalMinutes(interval: String): Long = when (interval) {
        INTERVAL_DAILY -> TimeUnit.DAYS.toMinutes(1)
        INTERVAL_WEEKLY -> TimeUnit.DAYS.toMinutes(7)
        else -> TimeUnit.DAYS.toMinutes(1)
    }
}
