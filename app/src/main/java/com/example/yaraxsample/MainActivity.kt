package com.example.yaraxsample

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
        if (requestCode == REQUEST_QUERY_PACKAGES && grantResults.isNotEmpty()) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "全アプリのスキャンには権限が必要です", Toast.LENGTH_LONG).show()
            }
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

        binding.scanButton.isEnabled = false
        binding.updateRulesButton.isEnabled = false
        binding.progressBar.isVisible = true
        binding.progressBar.isIndeterminate = false
        binding.progressBar.progress = 0
        resultsAdapter.submitList(emptyList())

        lifecycleScope.launch {
            scanEngine.scan(rulesRepo)
                .catch { e ->
                    binding.statusText.text = "エラー: ${e.message}"
                    binding.scanButton.isEnabled = true
                    binding.updateRulesButton.isEnabled = true
                    binding.progressBar.isVisible = false
                    Toast.makeText(this@MainActivity, "スキャンエラー: ${e.message}", Toast.LENGTH_LONG).show()
                }
                .collectLatest { progress ->
                    binding.statusText.text = when {
                        progress.totalApps > 0 -> getString(R.string.scanning, progress.scannedApps, progress.totalApps)
                        progress.totalApps == 0 && progress.results.isEmpty() -> {
                            Toast.makeText(this@MainActivity, "ルールを読み込めませんでした。先にルールを更新してください。", Toast.LENGTH_LONG).show()
                            getString(R.string.rules_update_failed)
                        }
                        else -> getString(R.string.status_ready)
                    }
                    binding.progressBar.progress = if (progress.totalApps > 0) {
                        (progress.scannedApps * 100) / progress.totalApps
                    } else {
                        0
                    }
                    resultsAdapter.submitList(progress.results)

                    val scanComplete = progress.scannedApps >= progress.totalApps && progress.totalApps > 0
                    val rulesFailed = progress.totalApps == 0
                    if (scanComplete || rulesFailed) {
                        if (scanComplete) hasCompletedScan = true
                        binding.scanButton.isEnabled = true
                        binding.updateRulesButton.isEnabled = true
                        binding.progressBar.isVisible = false
                        updateEmptyViewVisibility(progress.results)
                        if (scanComplete) {
                            if (progress.results.isEmpty()) {
                                Toast.makeText(this@MainActivity, R.string.no_threats, Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(
                                    this@MainActivity,
                                    "${progress.results.size}件のマッチを検出",
                                    Toast.LENGTH_SHORT
                                ).show()
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
    }
}
