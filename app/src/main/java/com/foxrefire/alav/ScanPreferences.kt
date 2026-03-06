package com.foxrefire.alav

import android.content.Context
import android.content.SharedPreferences

/**
 * Scan-related preferences. Uses same prefs file as LocaleHelper.
 */
object ScanPreferences {

    private const val PREFS_NAME = "app_prefs"
    private const val KEY_SKIP_SYSTEM_APPS = "skip_system_apps"
    private const val KEY_MAX_APK_SCAN_SIZE_MB = "max_apk_scan_size_mb"

    /** Default max size (MB) for scanning the APK file itself before extraction. */
    const val DEFAULT_MAX_APK_SCAN_SIZE_MB = 80

    /** Options for max APK scan size: (size in MB, string resource name for label). */
    val maxApkScanSizeEntries = listOf(
        20 to "settings_apk_scan_20mb",
        40 to "settings_apk_scan_40mb",
        80 to "settings_apk_scan_80mb",
        120 to "settings_apk_scan_120mb",
        200 to "settings_apk_scan_200mb"
    )

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSkipSystemApps(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SKIP_SYSTEM_APPS, false)

    fun setSkipSystemApps(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_SKIP_SYSTEM_APPS, value).apply()
    }

    fun getMaxApkScanSizeMb(context: Context): Int {
        val value = prefs(context).getInt(KEY_MAX_APK_SCAN_SIZE_MB, DEFAULT_MAX_APK_SCAN_SIZE_MB)
        return maxApkScanSizeEntries.map { it.first }.let { valid ->
            if (value in valid) value else DEFAULT_MAX_APK_SCAN_SIZE_MB
        }
    }

    fun setMaxApkScanSizeMb(context: Context, valueMb: Int) {
        prefs(context).edit().putInt(KEY_MAX_APK_SCAN_SIZE_MB, valueMb).apply()
    }

    fun getMaxApkScanSizeBytes(context: Context): Long =
        getMaxApkScanSizeMb(context).toLong() * 1024L * 1024L
}
