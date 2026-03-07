package com.foxrefire.alav

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.util.zip.ZipFile

/**
 * Scans installed apps by extracting APKs as ZIP and matching against YARA rules.
 */
class ScanEngine(private val context: Context) {

    companion object {
        private const val TAG = "ScanEngine"
        private const val MAX_FILE_SIZE_BYTES = 20 * 1024 * 1024 // 20MB per entry
        private const val IO_BUFFER_SIZE = 256 * 1024 // 256KB for faster I/O
        private const val ENSURE_ACTIVE_EVERY_ENTRIES = 16 // Check cancellation every N zip entries
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
        val maxApkBytes = ScanPreferences.getMaxApkScanSizeBytes(context)
        val scanApkEntries = ScanPreferences.getScanApkEntries(context)
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
                    for (apkPath in apkPaths) {
                        try {
                            val apkFile = File(apkPath)
                            val toRead = minOf(apkFile.length(), maxApkBytes).toInt()
                            val apkBytes = BufferedInputStream(apkFile.inputStream(), IO_BUFFER_SIZE).use { input ->
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
                                val rawMatches = rawApkMatches.filter { it !in excludedRules }
                                if (rawMatches.isNotEmpty()) {
                                    fileMatches.add(FileMatch(apkFile.name, rawMatches))
                                }
                            }
                            if (scanApkEntries) {
                                ZipFile(apkPath).use { zip ->
                                    var entryIndex = 0
                                    for (entry in zip.entries()) {
                                        if (entryIndex++ % ENSURE_ACTIVE_EVERY_ENTRIES == 0) currentCoroutineContext().ensureActive()
                                        if (entry.isDirectory) continue
                                        if (entry.size > MAX_FILE_SIZE_BYTES) continue
                                        if (entry.size <= 0) continue
                                        try {
                                            BufferedInputStream(zip.getInputStream(entry), IO_BUFFER_SIZE).use { input ->
                                                val data = input.readBytes()
                                                val rawMatches = scanner.scan(data)
                                                val matches = rawMatches.filter { it !in excludedRules }
                                                if (matches.isNotEmpty()) {
                                                    fileMatches.add(FileMatch(entry.name, matches))
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
                        results.add(ScanResult(packageName = pkg, appName = appName, matches = fileMatches))
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
                .filter { appInfo: ApplicationInfo -> appInfo.sourceDir.isNotEmpty() }
                .filter { app: ApplicationInfo ->
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
