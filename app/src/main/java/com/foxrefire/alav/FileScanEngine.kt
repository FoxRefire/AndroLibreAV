package com.foxrefire.alav

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.zip.ZipInputStream

/**
 * Scans arbitrary files and APKs. For APK (ZIP) files, expands and scans each entry.
 */
class FileScanEngine(private val context: Context) {

    companion object {
        private const val TAG = "FileScanEngine"
        private const val MAX_FILE_SIZE_BYTES = 20 * 1024 * 1024 // 20MB per entry
    }

    data class FileScanProgress(
        val scannedCount: Int,
        val totalCount: Int,
        val currentName: String?,
        val results: List<ScanResult>
    )

    /**
     * Scan one or more URIs. Each URI can be a regular file or an APK (ZIP).
     * For APK: scans each entry inside the archive.
     */
    fun scanFiles(rulesRepo: RulesRepository, uris: List<Uri>): Flow<FileScanProgress> = flow {
        val rules = loadRules(rulesRepo) ?: run {
            emit(FileScanProgress(0, 0, null, emptyList()))
            return@flow
        }

        val results = mutableListOf<ScanResult>()
        var scanned = 0
        val total = uris.size
        val excludedRules = ScanPreferences.getExcludedRuleNames(context)

        rules.use { yaraRules ->
            val scanner = yaraRules.createScanner()
            scanner.use {
                for (uri in uris) {
                    currentCoroutineContext().ensureActive()
                    val displayName = getDisplayName(uri)
                    emit(FileScanProgress(scanned, total, displayName, results.toList()))

                    val fileMatches = scanUri(context, uri, scanner, excludedRules)
                    if (fileMatches.isNotEmpty()) {
                        results.add(
                            ScanResult(
                                packageName = uri.toString(),
                                appName = displayName,
                                matches = fileMatches
                            )
                        )
                    }

                    scanned++
                    emit(FileScanProgress(scanned, total, displayName, results.toList()))
                }
            }
        }

        emit(FileScanProgress(total, total, null, results))
    }.flowOn(Dispatchers.IO)

    private fun scanUri(context: Context, uri: Uri, scanner: YaraScanner, excludedRules: Set<String>): List<FileMatch> {
        val fileMatches = mutableListOf<FileMatch>()
        val name = getDisplayName(uri)
        val isApk = name.lowercase().endsWith(".apk") || isZipLike(context, uri)

        if (isApk) {
            // Scan the APK file itself (before extraction)
            val maxApkBytes = ScanPreferences.getMaxApkScanSizeBytes(context).toInt().coerceAtLeast(1)
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(maxApkBytes)
                var total = 0
                while (total < maxApkBytes) {
                    val n = input.read(buffer, total, maxApkBytes - total)
                    if (n <= 0) break
                    total += n
                }
                val apkHead = buffer.copyOf(total)
                if (apkHead.isNotEmpty()) {
                    val rawMatches = scanner.scan(apkHead).filter { it !in excludedRules }
                    if (rawMatches.isNotEmpty()) {
                        fileMatches.add(FileMatch(name, rawMatches))
                    }
                }
            }
            if (ScanPreferences.getScanApkEntries(context)) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    ZipInputStream(input.buffered()).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory && entry.size != 0L && (entry.size < 0 || entry.size <= MAX_FILE_SIZE_BYTES)) {
                                try {
                                    val data = zis.readBytes()
                                    val matches = scanner.scan(data).filter { it !in excludedRules }
                                    if (matches.isNotEmpty()) {
                                        fileMatches.add(FileMatch(entry.name, matches))
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed to scan entry ${entry.name} in $name", e)
                                }
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                } ?: Log.w(TAG, "Cannot open URI: $uri")
            }
        } else {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val data = input.readBytes()
                    if (data.size <= MAX_FILE_SIZE_BYTES) {
                        val matches = scanner.scan(data).filter { it !in excludedRules }
                        if (matches.isNotEmpty()) {
                            fileMatches.add(FileMatch(name, matches))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to scan file $name", e)
            }
        }

        return fileMatches
    }

    private fun isZipLike(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val header = ByteArray(4)
                input.read(header) == 4 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    private fun getDisplayName(uri: Uri): String {
        val name = uri.lastPathSegment ?: return uri.toString()
        return name.substringAfterLast('/')
    }

    private suspend fun loadRules(rulesRepo: RulesRepository): YaraRules? = withContext(Dispatchers.IO) {
        val paths = mutableListOf<String>()
        val rulesDir = rulesRepo.rulesDir
        if (rulesDir.exists()) {
            paths.addAll(
                rulesDir.walkTopDown()
                    .filter { it.isFile && it.extension.equals("yar", ignoreCase = true) }
                    .map { it.absolutePath }
            )
        }
        val customFile = rulesRepo.customRulesFile
        if (customFile.exists() && customFile.readText().isNotBlank()) {
            paths.add(customFile.absolutePath)
        }
        if (paths.isEmpty()) {
            Log.e(TAG, "No rule files found")
            return@withContext null
        }
        val includeDir = if (rulesDir.exists()) rulesDir.absolutePath else customFile.parentFile!!.absolutePath
        YaraX.compileFromPaths(paths, includeDir)
    }
}
