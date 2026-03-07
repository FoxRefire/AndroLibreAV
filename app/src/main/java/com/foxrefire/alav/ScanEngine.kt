package com.foxrefire.alav

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

/**
 * Scans installed apps by extracting APKs as ZIP and matching against YARA rules.
 */
class ScanEngine(private val context: Context) {

    companion object {
        private const val TAG = "ScanEngine"
        private const val MAX_FILE_SIZE_BYTES = 20 * 1024 * 1024 // 20MB per entry
    }

    /**
     * Progress update during scan.
     */
    data class ScanProgress(
        val scannedApps: Int,
        val totalApps: Int,
        val currentPackage: String?,
        val currentAppName: String?,
        val results: List<ScanResult>
    )

    /**
     * Run full scan. Emits progress updates and final results.
     * Uses both yara-forge rules and user-defined custom rules from the repository.
     */
    fun scan(rulesRepo: RulesRepository): Flow<ScanProgress> = flow {
        val rules = loadRules(rulesRepo) ?: run {
            emit(ScanProgress(0, 0, null, null, emptyList()))
            return@flow
        }

        val skipSystemApps = ScanPreferences.getSkipSystemApps(context)
        val apps = getInstalledApps(skipSystemApps)
        val total = apps.size
        val results = mutableListOf<ScanResult>()
        var scanned = 0

        val excludedRules = ScanPreferences.getExcludedRuleNames(context)
        // #region agent log
        try {
            val payload = JSONObject().apply {
                put("sessionId", "e19a5f")
                put("hypothesisId", "A")
                put("location", "ScanEngine.kt:excludedRules")
                put("message", "excluded rules at scan start")
                put("data", JSONObject().apply {
                    put("size", excludedRules.size)
                    put("list", JSONArray(excludedRules.toList()))
                })
                put("timestamp", System.currentTimeMillis())
            }.toString()
            Log.d("ExcludedRulesDebug", payload)
        } catch (_: Exception) {}
        // #endregion
        rules.use { yaraRules ->
            val scanner = yaraRules.createScanner()
            scanner.use {
                for (appInfo in apps) {
                    currentCoroutineContext().ensureActive()
                    val pkg = appInfo.packageName
                    val appName = getAppLabel(appInfo)
                    emit(ScanProgress(scanned, total, pkg, appName, results.toList()))

                    val apkPaths = getApkPaths(appInfo)
                    val fileMatches = mutableListOf<FileMatch>()
                    val maxApkBytes = ScanPreferences.getMaxApkScanSizeBytes(context)
                    for (apkPath in apkPaths) {
                        try {
                            // Scan the APK file itself (before extraction)
                            val apkFile = File(apkPath)
                            val toRead = minOf(apkFile.length(), maxApkBytes).toInt()
                            val apkBytes = apkFile.inputStream().use { input ->
                                val buffer = ByteArray(toRead)
                                var bytesRead = 0
                                while (bytesRead < toRead) {
                                    val n = input.read(buffer, bytesRead, toRead - bytesRead)
                                    if (n <= 0) break
                                    bytesRead += n
                                }
                                buffer.copyOf(bytesRead)
                            }
                            if (apkBytes.isNotEmpty()) {
                                val rawApkMatches = scanner.scan(apkBytes)
                                // #region agent log
                                if (rawApkMatches.isNotEmpty()) {
                                    try {
                                        val filteredApk = rawApkMatches.filter { it !in excludedRules }
                                        val payload = JSONObject().apply {
                                            put("sessionId", "e19a5f")
                                            put("hypothesisId", "B")
                                            put("location", "ScanEngine.kt:apkFileScan")
                                            put("message", "APK file raw vs filtered")
                                            put("data", JSONObject().apply {
                                                put("apkName", apkFile.name)
                                                put("rawMatches", JSONArray(rawApkMatches))
                                                put("filteredSize", filteredApk.size)
                                                put("excludedContainsFirst", rawApkMatches.firstOrNull()?.let { it in excludedRules })
                                            })
                                            put("timestamp", System.currentTimeMillis())
                                        }.toString()
                                        Log.d("ExcludedRulesDebug", payload)
                                    } catch (_: Exception) {}
                                }
                                // #endregion
                                val rawMatches = rawApkMatches.filter { it !in excludedRules }
                                if (rawMatches.isNotEmpty()) {
                                    fileMatches.add(FileMatch(apkFile.name, rawMatches))
                                }
                            }
                            if (ScanPreferences.getScanApkEntries(context)) {
                                ZipFile(apkPath).use { zip ->
                                    for (entry in zip.entries()) {
                                        currentCoroutineContext().ensureActive()
                                        if (entry.isDirectory) continue
                                        if (entry.size > MAX_FILE_SIZE_BYTES) continue
                                        if (entry.size <= 0) continue

                                        try {
                                            zip.getInputStream(entry).use { input ->
                                                val data = input.readBytes()
                                                val rawMatches = scanner.scan(data)
                                                // #region agent log
                                                if (rawMatches.isNotEmpty()) {
                                                    try {
                                                        val filtered = rawMatches.filter { it !in excludedRules }
                                                        val payload = JSONObject().apply {
                                                            put("sessionId", "e19a5f")
                                                            put("hypothesisId", "B")
                                                            put("location", "ScanEngine.kt:entryFilter")
                                                            put("message", "raw vs filtered match names")
                                                            put("data", JSONObject().apply {
                                                                put("entry", entry.name)
                                                                put("rawMatches", JSONArray(rawMatches))
                                                                put("filteredSize", filtered.size)
                                                                put("filtered", JSONArray(filtered))
                                                                put("excludedContainsFirst", rawMatches.firstOrNull()?.let { it in excludedRules })
                                                            })
                                                            put("timestamp", System.currentTimeMillis())
                                                        }.toString()
                                                        Log.d("ExcludedRulesDebug", payload)
                                                    } catch (_: Exception) {}
                                                }
                                                // #endregion
                                                val matches = rawMatches.filter { it !in excludedRules }
                                                if (matches.isNotEmpty()) {
                                                    fileMatches.add(
                                                        FileMatch(entry.name, matches)
                                                    )
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Log.w(TAG, "Failed to scan entry ${entry.name} in $pkg", e)
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to open APK for $pkg: ${e.message}")
                        }
                    }

                    if (fileMatches.isNotEmpty()) {
                        results.add(
                            ScanResult(
                                packageName = pkg,
                                appName = appName,
                                matches = fileMatches
                            )
                        )
                    }

                    scanned++
                    emit(ScanProgress(scanned, total, pkg, appName, results.toList()))
                }
            }
        }

        emit(ScanProgress(total, total, null, null, results))
    }.flowOn(Dispatchers.IO)

    private suspend fun loadRules(rulesRepo: RulesRepository): YaraRules? = withContext(Dispatchers.IO) {
        val paths = mutableListOf<String>()

        // Yara-forge rules from downloaded zip
        val rulesDir = rulesRepo.rulesDir
        if (rulesDir.exists()) {
            paths.addAll(
                rulesDir.walkTopDown()
                    .filter { it.isFile && it.extension.equals("yar", ignoreCase = true) }
                    .map { it.absolutePath }
            )
        }

        // User-defined custom rules (stored separately, not wiped on yara-forge update)
        val customFile = rulesRepo.customRulesFile
        if (customFile.exists() && customFile.readText().isNotBlank()) {
            paths.add(customFile.absolutePath)
        }

        if (paths.isEmpty()) {
            Log.e(TAG, "No rule files found (yara-forge or custom)")
            return@withContext null
        }

        val includeDir = if (rulesDir.exists()) rulesDir.absolutePath else rulesRepo.customRulesFile.parentFile!!.absolutePath
        Log.d(TAG, "Compiling ${paths.size} rule files (include: $includeDir)")
        YaraX.compileFromPaths(paths, includeDir)
    }

    private fun getInstalledApps(skipSystemApps: Boolean): List<ApplicationInfo> {
        val pm = context.packageManager
        return try {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { it.sourceDir.isNotEmpty() }
                .filter { app ->
                    !skipSystemApps || (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get installed apps", e)
            emptyList()
        }
    }

    private fun getApkPaths(info: ApplicationInfo): List<String> {
        val paths = mutableListOf<String>()
        info.sourceDir.let { if (it.isNotEmpty()) paths.add(it) }
        (info.splitSourceDirs ?: emptyArray()).forEach { path ->
            if (path.isNotEmpty()) paths.add(path)
        }
        return paths
    }

    private fun getAppLabel(info: ApplicationInfo): String {
        return try {
            context.packageManager.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            info.packageName
        }
    }
}
