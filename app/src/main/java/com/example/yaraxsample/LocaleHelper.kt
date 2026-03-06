package com.example.yaraxsample

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * Manages app locale preference. "system" = follow device, otherwise use BCP-47 tag (e.g. "ja", "zh-CN").
 */
object LocaleHelper {

    private const val PREFS_NAME = "app_prefs"
    private const val KEY_LANGUAGE = "app_language"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getLanguage(): String = prefs.getString(KEY_LANGUAGE, "system") ?: "system"

    fun setLanguage(tag: String) {
        prefs.edit().putString(KEY_LANGUAGE, tag).apply()
        applyLocale(tag)
    }

    fun applyStoredLocale() {
        applyLocale(getLanguage())
    }

    private fun applyLocale(tag: String) {
        val localeList = when {
            tag == "system" || tag.isEmpty() -> LocaleListCompat.getEmptyLocaleList()
            else -> LocaleListCompat.forLanguageTags(tag)
        }
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    val languageEntries: List<Pair<String, String>> = listOf(
        "system" to "settings_language_system",
        "en" to "settings_language_en",
        "ja" to "settings_language_ja",
        "zh-CN" to "settings_language_zh_CN",
        "zh-TW" to "settings_language_zh_TW",
        "fr" to "settings_language_fr",
        "it" to "settings_language_it",
        "ko" to "settings_language_ko",
        "es" to "settings_language_es",
        "de" to "settings_language_de"
    )
}
