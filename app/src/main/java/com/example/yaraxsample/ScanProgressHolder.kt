package com.example.yaraxsample

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
        val results: List<ScanResult> = emptyList(),
        val error: String? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun update(scanning: Boolean, scanned: Int, total: Int, results: List<ScanResult>) {
        _state.value = State(isScanning = scanning, scannedApps = scanned, totalApps = total, results = results)
    }

    fun setError(message: String) {
        _state.value = _state.value.copy(isScanning = false, error = message)
    }

    fun clear() {
        _state.value = State()
    }
}
