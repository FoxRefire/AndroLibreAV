package com.example.yaraxsample

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.yaraxsample.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var rulesRepo: RulesRepository
    private lateinit var scanEngine: ScanEngine
    private lateinit var resultsAdapter: ScanResultsAdapter
    private var hasCompletedScan = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        rulesRepo = RulesRepository(this)
        scanEngine = ScanEngine(this)

        resultsAdapter = ScanResultsAdapter()
        binding.resultsList.layoutManager = LinearLayoutManager(this)
        binding.resultsList.adapter = resultsAdapter
        binding.resultsList.itemAnimator = null

        updateStatusFromRules()

        binding.updateRulesButton.setOnClickListener { updateRules() }
        binding.scanButton.setOnClickListener { startScan() }
        binding.customRulesButton.setOnClickListener {
            startActivity(Intent(this, CustomRulesActivity::class.java))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            requestQueryAllPackagesIfNeeded()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission()
        }
    }

    private fun requestNotificationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }

    private fun requestQueryAllPackagesIfNeeded() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            "android.permission.QUERY_ALL_PACKAGES"
        } else {
            return
        }
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(permission), REQUEST_QUERY_PACKAGES)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_QUERY_PACKAGES -> if (grantResults.isNotEmpty() && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "全アプリのスキャンには権限が必要です", Toast.LENGTH_LONG).show()
            }
            REQUEST_NOTIFICATIONS -> { /* Optional: notify user about notification permission */ }
        }
    }

    private fun updateStatusFromRules() {
        binding.statusText.text = if (rulesRepo.hasAnyRules()) {
            getString(R.string.status_ready).replace("ルールを更新してから", "スキャンを")
        } else {
            getString(R.string.status_ready)
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatusFromRules()
        syncUiFromScanState(ScanProgressHolder.state.value)
    }

    private fun syncUiFromScanState(state: ScanProgressHolder.State) {
        if (state.totalApps > 0) {
            val percent = (state.scannedApps * 100) / state.totalApps
            binding.statusText.text = if (state.isScanning) {
                getString(R.string.scanning, state.scannedApps, state.totalApps, percent)
            } else {
                getString(R.string.status_ready)
            }
            binding.progressBar.progress = percent
            binding.progressBar.isVisible = state.isScanning
            binding.scanButton.isEnabled = !state.isScanning
            binding.updateRulesButton.isEnabled = !state.isScanning
            resultsAdapter.submitList(state.results)
            if (!state.isScanning) {
                hasCompletedScan = true
                updateEmptyViewVisibility(state.results)
            }
        } else if (state.error != null) {
            binding.scanButton.isEnabled = true
            binding.updateRulesButton.isEnabled = true
            binding.progressBar.isVisible = false
        }
    }

    private fun updateRules() {
        binding.updateRulesButton.isEnabled = false
        binding.progressBar.isVisible = true
        binding.progressBar.isIndeterminate = true
        binding.statusText.text = getString(R.string.downloading_rules)

        lifecycleScope.launch {
            rulesRepo.updateRules()
                .onSuccess {
                    binding.statusText.text = getString(R.string.rules_updated)
                    updateStatusFromRules()
                    Toast.makeText(this@MainActivity, R.string.rules_updated, Toast.LENGTH_SHORT).show()
                }
                .onFailure {
                    binding.statusText.text = getString(R.string.rules_update_failed)
                    Toast.makeText(this@MainActivity, R.string.rules_update_failed, Toast.LENGTH_LONG).show()
                }

            binding.updateRulesButton.isEnabled = true
            binding.progressBar.isVisible = false
        }
    }

    private fun startScan() {
        if (!rulesRepo.hasAnyRules()) {
            Toast.makeText(this, getString(R.string.status_ready), Toast.LENGTH_LONG).show()
            return
        }

        ScanProgressHolder.clear()
        binding.scanButton.isEnabled = false
        binding.updateRulesButton.isEnabled = false
        binding.progressBar.isVisible = true
        binding.progressBar.isIndeterminate = false
        binding.progressBar.progress = 0
        resultsAdapter.submitList(emptyList())

        val intent = Intent(this, ScanForegroundService::class.java).apply {
            action = ScanForegroundService.ACTION_START_SCAN
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ScanProgressHolder.state.collect { state ->
                val percent = if (state.totalApps > 0) (state.scannedApps * 100) / state.totalApps else 0
                binding.statusText.text = when {
                    state.error != null -> state.error ?: ""
                    state.totalApps > 0 -> getString(R.string.scanning, state.scannedApps, state.totalApps, percent)
                    state.isScanning -> getString(R.string.scanning_preparing)
                    else -> getString(R.string.status_ready)
                }
                binding.progressBar.progress = percent
                resultsAdapter.submitList(state.results)

                val scanComplete = state.totalApps > 0 && !state.isScanning
                val scanFailed = state.error != null
                if (scanComplete || scanFailed) {
                    hasCompletedScan = scanComplete
                    binding.scanButton.isEnabled = true
                    binding.updateRulesButton.isEnabled = true
                    binding.progressBar.isVisible = false
                    updateEmptyViewVisibility(state.results)
                    if (scanComplete) {
                        if (state.results.isEmpty()) {
                            Toast.makeText(this@MainActivity, R.string.no_threats, Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(
                                this@MainActivity,
                                getString(R.string.scan_complete_matches, state.results.size),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else if (scanFailed) {
                        Toast.makeText(this@MainActivity, "スキャンエラー: ${state.error}", Toast.LENGTH_LONG).show()
                    }
                }
                }
            }
        }
    }

    private fun updateEmptyViewVisibility(results: List<ScanResult>) {
        binding.emptyResultsText.isVisible = hasCompletedScan && results.isEmpty()
        binding.emptyResultsText.text = getString(R.string.no_threats)
    }

    companion object {
        private const val REQUEST_QUERY_PACKAGES = 1001
        private const val REQUEST_NOTIFICATIONS = 1002
    }
}
