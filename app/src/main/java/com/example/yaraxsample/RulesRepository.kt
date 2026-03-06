package com.example.yaraxsample

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
 * Manages downloading and extracting YARA rules from yara-forge.
 */
class RulesRepository(private val context: Context) {

    companion object {
        private const val TAG = "RulesRepository"
        const val RULES_URL = "https://github.com/YARAHQ/yara-forge/releases/latest/download/yara-forge-rules-full.zip"
        private const val RULES_DIR_NAME = "yara_rules"
    }

    /**
     * Directory where extracted .yar files are stored.
     */
    val rulesDir: File
        get() = File(context.filesDir, RULES_DIR_NAME)

    /**
     * Check if rules have been downloaded.
     */
    fun hasRules(): Boolean {
        val dir = rulesDir
        if (!dir.exists()) return false
        return dir.walkTopDown().any { it.isFile && it.extension == "yar" }
    }

    /**
     * Download the rules ZIP and extract to rulesDir.
     * Preserves directory structure for include resolution.
     * @return true on success, false on failure
     */
    suspend fun updateRules(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            Log.d(TAG, "Downloading rules from $RULES_URL")
            val zipFile = downloadZip()
            extractZip(zipFile)
            zipFile.delete()
            Log.d(TAG, "Rules updated successfully")
            Unit
        }.onFailure { e ->
            Log.e(TAG, "Failed to update rules", e)
        }
    }

    private fun downloadZip(): File {
        val url = URL(RULES_URL)
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

            val tempFile = File(context.cacheDir, "yara-forge-rules.zip")
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
}
