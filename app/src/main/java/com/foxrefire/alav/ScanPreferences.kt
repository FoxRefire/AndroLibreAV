package com.foxrefire.alav

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Scan-related preferences. Uses same prefs file as LocaleHelper.
 */
object ScanPreferences {

    private const val PREFS_NAME = "app_prefs"
    private const val KEY_SKIP_SYSTEM_APPS = "skip_system_apps"
    private const val KEY_MAX_APK_SCAN_SIZE_MB = "max_apk_scan_size_mb"
    private const val KEY_SCAN_APK_ENTRIES = "scan_apk_entries"
    private const val KEY_EXCLUDED_RULE_NAMES = "excluded_rule_names"

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

    /** When true, APK is also scanned by extracting and matching each file inside. Default on. */
    fun getScanApkEntries(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SCAN_APK_ENTRIES, true)

    fun setScanApkEntries(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_SCAN_APK_ENTRIES, value).apply()
    }

    /** Default rule names excluded from detection when the user has not set any. */
    val DEFAULT_EXCLUDED_RULE_NAMES: Set<String> = setOf(
        "TELEKOM_SECURITY_Android_Flubot",
        "DELIVRTO_SUSP_ZIP_Smuggling_Jun01"
    )

    /** Rule names to exclude from detection. Matches for these rules are not reported. */
    fun getExcludedRuleNames(context: Context): Set<String> {
        val raw = prefs(context).getStringSet(KEY_EXCLUDED_RULE_NAMES, null)
        val set = when {
            raw == null -> DEFAULT_EXCLUDED_RULE_NAMES
            else -> raw.map { it.trim().removePrefix("\uFEFF") }.filter { it.isNotEmpty() }.toSet()
        }
        // #region agent log
        try {
            val payload = JSONObject().apply {
                put("sessionId", "e19a5f")
                put("hypothesisId", "A")
                put("location", "ScanPreferences.kt:getExcludedRuleNames")
                put("message", "getExcludedRuleNames result")
                put("data", JSONObject().apply {
                    put("rawNull", raw == null)
                    put("size", set.size)
                    put("list", JSONArray(set.toList()))
                    put("firstBytes", set.firstOrNull()?.toByteArray(Charsets.UTF_8)?.joinToString(",") { it.toString() })
                })
                put("timestamp", System.currentTimeMillis())
            }.toString()
            Log.d("ExcludedRulesDebug", payload)
        } catch (_: Exception) {}
        // #endregion
        return set
    }

    fun setExcludedRuleNames(context: Context, names: Set<String>) {
        prefs(context).edit().putStringSet(KEY_EXCLUDED_RULE_NAMES, names).apply()
    }
}
