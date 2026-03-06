package com.foxrefire.alav

import android.content.Context
import android.content.SharedPreferences

/**
 * Scan-related preferences. Uses same prefs file as LocaleHelper.
 */
object ScanPreferences {

    private const val PREFS_NAME = "app_prefs"
    private const val KEY_SKIP_SYSTEM_APPS = "skip_system_apps"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSkipSystemApps(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SKIP_SYSTEM_APPS, false)

    fun setSkipSystemApps(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_SKIP_SYSTEM_APPS, value).apply()
    }
}
