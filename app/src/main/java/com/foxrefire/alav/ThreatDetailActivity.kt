package com.foxrefire.alav

import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.core.view.isVisible
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.foxrefire.alav.databinding.ActivityThreatDetailBinding
import com.foxrefire.alav.databinding.ItemDetectionDetailBinding

class ThreatDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityThreatDetailBinding

    private val uninstallLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // User returned from uninstall UI; optionally refresh or finish
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityThreatDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_SCAN_RESULT, ScanResult::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_SCAN_RESULT)
        }
        if (result == null) {
            finish()
            return
        }

        val pkg = result.packageName
        val appName = result.appName
        val matches = result.matches

        try {
            val icon = packageManager.getApplicationIcon(pkg)
            binding.appIcon.setImageDrawable(icon)
        } catch (e: PackageManager.NameNotFoundException) {
            binding.appIcon.setImageResource(android.R.drawable.sym_def_app_icon)
        }

        binding.appNameText.text = appName
        binding.packageText.text = if (pkg.startsWith("content://") || pkg.startsWith("file://")) appName else pkg

        val isFileScan = pkg.startsWith("content://") || pkg.startsWith("file://")
        binding.uninstallButton.isVisible = !isFileScan
        binding.appInfoButton.isVisible = !isFileScan

        val adapter = DetectionDetailAdapter()
        binding.detectionsList.layoutManager = LinearLayoutManager(this)
        binding.detectionsList.adapter = adapter
        adapter.submitList(matches)

        binding.uninstallButton.setOnClickListener { openUninstall(pkg) }

        binding.appInfoButton.setOnClickListener {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$pkg")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                android.widget.Toast.makeText(this, R.string.app_info_open, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openUninstall(pkg: String) {
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.fromParts("package", pkg, null)
        }
        try {
            uninstallLauncher.launch(intent)
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, R.string.uninstall_open, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private class DetectionDetailAdapter : ListAdapter<FileMatch, DetectionDetailAdapter.ViewHolder>(DiffCallback()) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemDetectionDetailBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(getItem(position))
        }

        class ViewHolder(private val binding: ItemDetectionDetailBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(match: FileMatch) {
                binding.filePathText.text = match.entryPath
                binding.rulesText.text = match.ruleNames.joinToString(", ")
            }
        }

        private class DiffCallback : DiffUtil.ItemCallback<FileMatch>() {
            override fun areItemsTheSame(old: FileMatch, new: FileMatch) = old.entryPath == new.entryPath
            override fun areContentsTheSame(old: FileMatch, new: FileMatch) = old == new
        }
    }

    companion object {
        const val EXTRA_SCAN_RESULT = "scan_result"
    }
}
