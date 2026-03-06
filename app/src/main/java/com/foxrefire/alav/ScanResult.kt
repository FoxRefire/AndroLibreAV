package com.foxrefire.alav

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Result of scanning a single app's APK.
 */
@Parcelize
data class ScanResult(
    val packageName: String,
    val appName: String,
    val matches: List<FileMatch>
) : Parcelable

/**
 * YARA rule match within a specific file inside an APK.
 */
@Parcelize
data class FileMatch(
    val entryPath: String,
    val ruleNames: List<String>
) : Parcelable
