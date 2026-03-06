package com.example.yaraxsample

/**
 * Result of scanning a single app's APK.
 */
data class ScanResult(
    val packageName: String,
    val appName: String,
    val matches: List<FileMatch>
)

/**
 * YARA rule match within a specific file inside an APK.
 */
data class FileMatch(
    val entryPath: String,
    val ruleNames: List<String>
)
