package com.example.yaraxsample

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.yaraxsample.databinding.ActivityFileScanBinding
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Scans files shared to the app or picked via document picker.
 * Supports regular files and APKs (scans contents when APK).
 */
class FileScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFileScanBinding
    private lateinit var fileScanEngine: FileScanEngine
    private lateinit var rulesRepo: RulesRepository
    private lateinit var resultsAdapter: ScanResultsAdapter

    private val documentPicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { launchFileScan(listOf(it)) }
    }

    private val multiDocumentPicker = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            launchFileScan(uris)
        } else {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFileScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        fileScanEngine = FileScanEngine(this)
        rulesRepo = RulesRepository(this)
        resultsAdapter = ScanResultsAdapter(packageManager) { result ->
            startActivity(Intent(this, ThreatDetailActivity::class.java).apply {
                putExtra(ThreatDetailActivity.EXTRA_SCAN_RESULT, result)
            })
        }
        binding.resultsList.layoutManager = LinearLayoutManager(this)
        binding.resultsList.adapter = resultsAdapter

        when {
            intent?.action == Intent.ACTION_SEND && intent.type != null -> {
                (intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))?.let { uri ->
                    launchFileScan(listOf(uri))
                } ?: run {
                    Toast.makeText(this, R.string.scan_file_invalid_share, Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            intent?.action == Intent.ACTION_SEND_MULTIPLE && intent.type != null -> {
                @Suppress("UNCHECKED_CAST")
                val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                if (!uris.isNullOrEmpty()) {
                    launchFileScan(uris)
                } else {
                    Toast.makeText(this, R.string.scan_file_invalid_share, Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            intent?.getParcelableArrayListExtra<Uri>(EXTRA_URIS) != null -> {
                @Suppress("UNCHECKED_CAST")
                launchFileScan(intent.getParcelableArrayListExtra(EXTRA_URIS)!!)
            }
            else -> {
                // Opened from menu without URIs - show picker
                multiDocumentPicker.launch(arrayOf("*/*"))
            }
        }
    }

    private fun launchFileScan(uris: List<Uri>) {
        if (!rulesRepo.hasAnyRules()) {
            Toast.makeText(this, R.string.rules_load_failed, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        binding.progressBar.isIndeterminate = false
        binding.progressBar.progress = 0
        binding.progressBar.isVisible = true
        binding.emptyText.isVisible = false
        resultsAdapter.submitList(emptyList())

        lifecycleScope.launch {
            fileScanEngine.scanFiles(rulesRepo, uris)
                .catch { e ->
                    runOnUiThread {
                        Toast.makeText(this@FileScanActivity, getString(R.string.scan_error, e.message), Toast.LENGTH_LONG).show()
                        binding.progressBar.isVisible = false
                    }
                    finish()
                }
                .collect { progress ->
                    if (progress.totalCount > 0) {
                        val percent = (progress.scannedCount * 100) / progress.totalCount
                        binding.statusText.text = getString(R.string.scanning, progress.scannedCount, progress.totalCount, percent)
                        binding.progressBar.progress = percent
                    } else {
                        binding.statusText.text = getString(R.string.scanning_preparing)
                    }
                    resultsAdapter.submitList(progress.results)

                    if (progress.scannedCount >= progress.totalCount) {
                        binding.progressBar.isVisible = false
                        binding.statusText.text = if (progress.results.isEmpty()) {
                            getString(R.string.no_threats)
                        } else {
                            getString(R.string.scan_complete_matches, progress.results.size)
                        }
                        binding.emptyText.isVisible = progress.results.isEmpty()
                        binding.emptyText.text = getString(R.string.no_threats)
                        if (progress.results.isNotEmpty()) {
                            Toast.makeText(this@FileScanActivity, getString(R.string.scan_complete_matches, progress.results.size), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
        }
    }

    companion object {
        const val EXTRA_URIS = "extra_uris"
    }
}
