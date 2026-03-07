package com.foxrefire.alav

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Manages downloading and extracting YARA rules from yara-forge and additional sources.
 */
class RulesRepository(private val context: Context) {

    companion object {
        private const val TAG = "RulesRepository"
        const val RULES_URL = "https://github.com/YARAHQ/yara-forge/releases/latest/download/yara-forge-rules-full.zip"
        /** Additional rules (e.g. alav-additional-rules); extracted into rulesDir after yara-forge. */
        private const val ADDITIONAL_RULES_URL = "https://github.com/FoxRefire/alav-additional-rules/archive/refs/heads/main.zip"
        private const val RULES_DIR_NAME = "yara_rules"
        private const val CUSTOM_RULES_FILE = "custom_rules.yar"
    }

    /**
     * File where user-defined YARA rules are stored (persists across yara-forge updates).
     */
    val customRulesFile: File
        get() = File(context.filesDir, CUSTOM_RULES_FILE)

    /**
     * Directory where extracted .yar files are stored.
     */
    val rulesDir: File
        get() = File(context.filesDir, RULES_DIR_NAME)

    /**
     * Check if yara-forge rules have been downloaded.
     */
    fun hasRules(): Boolean {
        val dir = rulesDir
        if (!dir.exists()) return false
        return dir.walkTopDown().any { it.isFile && it.extension == "yar" }
    }

    /**
     * Check if any rules are available for scanning (yara-forge or custom).
     */
    fun hasAnyRules(): Boolean = hasRules() || hasCustomRules()

    /**
     * Download the rules ZIP and extract to rulesDir.
     * Fetches both yara-forge and alav-additional-rules. Preserves directory structure for include resolution.
     * @return success or failure
     */
    suspend fun updateRules(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            Log.d(TAG, "Downloading rules from $RULES_URL")
            val zipFile = downloadZip(RULES_URL, "yara-forge-rules.zip")
            extractZip(zipFile)
            zipFile.delete()

            Log.d(TAG, "Downloading additional rules from $ADDITIONAL_RULES_URL")
            val additionalZip = downloadZip(ADDITIONAL_RULES_URL, "alav-additional-rules.zip")
            extractZipIntoExisting(additionalZip, rulesDir)
            additionalZip.delete()

            Log.d(TAG, "Rules updated successfully")
            Unit
        }.onFailure { e ->
            Log.e(TAG, "Failed to update rules", e)
        }
    }

    private fun downloadZip(urlString: String, tempFileName: String): File {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 30_000
            connection.readTimeout = 60_000
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Android-YaraScanner/1.0")
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw RuntimeException("HTTP ${connection.responseCode}: ${connection.responseMessage}")
            }

            val tempFile = File(context.cacheDir, tempFileName)
            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            return tempFile
        } finally {
            connection.disconnect()
        }
    }

    private fun extractZip(zipFile: File) {
        val destDir = rulesDir
        destDir.mkdirs()
        destDir.listFiles()?.forEach { it.deleteRecursively() }
        extractZipIntoExisting(zipFile, destDir)
    }

    /**
     * Extracts a ZIP into an existing directory without clearing it (for additional rules).
     */
    private fun extractZipIntoExisting(zipFile: File, destDir: File) {
        destDir.mkdirs()
        val destCanonical = destDir.canonicalPath

        ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val file = File(destDir, entry.name)
                if (!file.canonicalPath.startsWith(destCanonical)) {
                    zis.closeEntry()
                    entry = zis.nextEntry
                    continue
                }
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    BufferedOutputStream(FileOutputStream(file)).use { out ->
                        zis.copyTo(out)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    /**
     * Collect all .yar file paths under rulesDir (for JNI compiler).
     */
    fun collectYarPaths(): List<String> {
        val dir = rulesDir
        if (!dir.exists()) return emptyList()
        return dir.walkTopDown()
            .filter { it.isFile && it.extension.equals("yar", ignoreCase = true) }
            .map { it.absolutePath }
            .toList()
    }

    /**
     * Save user-defined YARA rules. Stored separately from yara-forge (not wiped on update).
     */
    fun saveCustomRules(rulesText: String) {
        customRulesFile.writeText(rulesText, Charsets.UTF_8)
    }

    /**
     * Load user-defined YARA rules.
     */
    fun loadCustomRules(): String {
        return if (customRulesFile.exists()) customRulesFile.readText(Charsets.UTF_8) else ""
    }

    /**
     * Check if user has defined custom rules.
     */
    fun hasCustomRules(): Boolean = customRulesFile.exists() && customRulesFile.readText().isNotBlank()
}
