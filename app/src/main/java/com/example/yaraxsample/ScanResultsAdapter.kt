package com.example.yaraxsample

import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.yaraxsample.databinding.ItemScanResultBinding

class ScanResultsAdapter(
    private val packageManager: PackageManager,
    private val onItemClick: (ScanResult) -> Unit
) : ListAdapter<ScanResult, ScanResultsAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemScanResultBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, packageManager, onItemClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemScanResultBinding,
        private val packageManager: PackageManager,
        private val onItemClick: (ScanResult) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(result: ScanResult) {
            binding.root.setOnClickListener { onItemClick(result) }

            try {
                binding.appIcon.setImageDrawable(packageManager.getApplicationIcon(result.packageName))
            } catch (e: PackageManager.NameNotFoundException) {
                binding.appIcon.setImageResource(android.R.drawable.sym_def_app_icon)
            }

            binding.appNameText.text = result.appName
            binding.packageText.text = result.packageName

            val firstDetection = result.matches.firstOrNull()?.ruleNames?.firstOrNull()
            if (firstDetection != null) {
                binding.firstDetectionText.text = firstDetection
                binding.firstDetectionText.visibility = android.view.View.VISIBLE
            } else {
                binding.firstDetectionText.visibility = android.view.View.GONE
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<ScanResult>() {
        override fun areItemsTheSame(old: ScanResult, new: ScanResult) =
            old.packageName == new.packageName

        override fun areContentsTheSame(old: ScanResult, new: ScanResult) =
            old == new
    }
}
