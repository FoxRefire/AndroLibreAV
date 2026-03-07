package com.foxrefire.alav

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared state for scan progress. Updated by ScanForegroundService, observed by MainActivity.
 */
object ScanProgressHolder {

    data class State(
        val isScanning: Boolean = false,
        val scannedApps: Int = 0,
        val totalApps: Int = 0,
        val currentScanningPackage: String? = null,
        val currentScanningAppName: String? = null,
        val results: List<ScanResult> = emptyList(),
        val error: String? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun update(
        scanning: Boolean,
        scanned: Int,
        total: Int,
        currentPackage: String? = null,
        currentAppName: String? = null,
        results: List<ScanResult>
    ) {
        _state.value = State(
            isScanning = scanning,
            scannedApps = scanned,
            totalApps = total,
            currentScanningPackage = currentPackage,
            currentScanningAppName = currentAppName,
            results = results
        )
    }

    fun setError(message: String) {
        _state.value = _state.value.copy(isScanning = false, error = message)
    }

    /** Stops scanning state while keeping current results (e.g. after user cancels). */
    fun setScanningStopped() {
        _state.value = _state.value.copy(isScanning = false)
    }

    fun clear() {
        _state.value = State()
    }
}
