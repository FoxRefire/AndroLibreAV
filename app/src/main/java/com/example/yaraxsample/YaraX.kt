package com.example.yaraxsample

import java.io.Closeable

/**
 * Kotlin wrapper for YARA-X native library.
 * Compiles YARA rules and scans data for pattern matches.
 */
object YaraX {

    init {
        System.loadLibrary("yara_x_capi")  // Load first (dependency)
        System.loadLibrary("yara_jni")      // Our JNI bridge
    }

    /**
     * Compile YARA rules from source string.
     * @return Rules handle, or 0 on failure (check lastError)
     */
    external fun nativeCompileRules(rulesSrc: String): Long

    /**
     * Compile YARA rules from multiple .yar files with include directory support.
     * @param yarPaths Array of absolute paths to .yar files
     * @param includeDir Directory for resolving include statements
     * @return Rules handle, or 0 on failure
     */
    external fun nativeCompileRulesFromPaths(yarPaths: Array<String>, includeDir: String): Long

    /**
     * Create a scanner from compiled rules.
     * @return Scanner handle, or 0 on failure
     */
    external fun nativeCreateScanner(rulesHandle: Long): Long

    /**
     * Scan byte array with the scanner.
     * @return List of matching rule identifiers, or null on error
     */
    external fun nativeScanBytes(scannerHandle: Long, data: ByteArray?): List<String>?

    /**
     * Destroy scanner and free resources.
     */
    external fun nativeDestroyScanner(scannerHandle: Long)

    /**
     * Destroy rules and free resources.
     */
    external fun nativeDestroyRules(rulesHandle: Long)

    /**
     * Get the last error message from the native library.
     */
    external fun nativeLastError(): String?

    /**
     * Compile rules and return a [YaraRules] instance.
     * @throws YaraException on compile error
     */
    fun compile(rulesSrc: String): YaraRules {
        val handle = nativeCompileRules(rulesSrc)
        if (handle == 0L) {
            throw YaraException(nativeLastError() ?: "Failed to compile rules")
        }
        return YaraRules(handle)
    }

    /**
     * Compile rules from multiple .yar files in a directory.
     * @param yarPaths List of absolute paths to .yar files
     * @param includeDir Absolute path to directory for include resolution
     * @return YaraRules instance, or null if no rules could be compiled
     */
    fun compileFromPaths(yarPaths: List<String>, includeDir: String): YaraRules? {
        if (yarPaths.isEmpty()) return null
        val handle = nativeCompileRulesFromPaths(yarPaths.toTypedArray(), includeDir)
        if (handle == 0L) return null
        return YaraRules(handle)
    }

    /**
     * Get last error message.
     */
    fun lastError(): String? = nativeLastError()
}

class YaraException(message: String) : Exception(message)

/**
 * Compiled YARA rules. Must call [close] when done.
 */
class YaraRules(private val rulesHandle: Long) : Closeable {

    init {
        if (rulesHandle == 0L) throw IllegalArgumentException("Invalid rules handle")
    }

    /**
     * Create a scanner for scanning data.
     */
    fun createScanner(): YaraScanner {
        val handle = YaraX.nativeCreateScanner(rulesHandle)
        if (handle == 0L) {
            throw YaraException(YaraX.nativeLastError() ?: "Failed to create scanner")
        }
        return YaraScanner(handle)
    }

    override fun close() {
        if (rulesHandle != 0L) {
            YaraX.nativeDestroyRules(rulesHandle)
        }
    }
}

/**
 * Scanner for matching data against rules. Must call [close] when done.
 */
class YaraScanner(private val scannerHandle: Long) : Closeable {

    init {
        if (scannerHandle == 0L) throw IllegalArgumentException("Invalid scanner handle")
    }

    /**
     * Scan byte array.
     * @return List of matching rule identifiers
     */
    fun scan(data: ByteArray): List<String> {
        val result = YaraX.nativeScanBytes(scannerHandle, data)
        return result ?: emptyList()
    }

    /**
     * Scan string (UTF-8 encoded).
     */
    fun scanString(text: String): List<String> = scan(text.toByteArray(Charsets.UTF_8))

    override fun close() {
        if (scannerHandle != 0L) {
            YaraX.nativeDestroyScanner(scannerHandle)
        }
    }
}
