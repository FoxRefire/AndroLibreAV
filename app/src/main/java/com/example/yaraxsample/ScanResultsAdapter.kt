package com.example.yaraxsample

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.yaraxsample.databinding.ItemScanResultBinding

class ScanResultsAdapter : ListAdapter<ScanResult, ScanResultsAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemScanResultBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(private val binding: ItemScanResultBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(result: ScanResult) {
            binding.appNameText.text = result.appName
            binding.packageText.text = result.packageName

            val matchLines = result.matches.flatMap { fm ->
                fm.ruleNames.map { rule -> "${fm.entryPath}: $rule" }
            }
            binding.matchesText.text = matchLines.take(10).joinToString("\n").let { text ->
                if (matchLines.size > 10) {
                    "$text\n... (${matchLines.size} matches total)"
                } else {
                    text
                }
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
