package com.foxrefire.alav

import android.content.Intent
import android.os.Bundle
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.foxrefire.alav.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings_title)

        val labels = LocaleHelper.languageEntries.map { getString(resources.getIdentifier(it.second, "string", packageName)) }
        binding.languageSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        val currentLang = LocaleHelper.getLanguage()
        val currentIndex = LocaleHelper.languageEntries.indexOfFirst { it.first == currentLang }.let {
            if (it >= 0) it else 0
        }
        binding.languageSpinner.setSelection(currentIndex)

        binding.languageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val tag = LocaleHelper.languageEntries[position].first
                if (tag != LocaleHelper.getLanguage()) {
                    LocaleHelper.setLanguage(tag)
                    recreate()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.skipSystemAppsSwitch.isChecked = ScanPreferences.getSkipSystemApps(this)
        binding.skipSystemAppsSwitch.setOnCheckedChangeListener { _, isChecked ->
            ScanPreferences.setSkipSystemApps(this, isChecked)
        }

        binding.scanApkEntriesSwitch.isChecked = ScanPreferences.getScanApkEntries(this)
        binding.scanApkEntriesSwitch.setOnCheckedChangeListener { _, isChecked ->
            ScanPreferences.setScanApkEntries(this, isChecked)
        }

        // Max APK scan size (before extraction)
        binding.apkScanMaxSizeSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item,
            ScanPreferences.maxApkScanSizeEntries.map { getString(resources.getIdentifier(it.second, "string", packageName)) }
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        binding.apkScanMaxSizeSpinner.setSelection(
            ScanPreferences.maxApkScanSizeEntries.indexOfFirst { it.first == ScanPreferences.getMaxApkScanSizeMb(this) }.coerceAtLeast(0)
        )
        binding.apkScanMaxSizeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, pos: Int, id: Long) {
                ScanPreferences.setMaxApkScanSizeMb(this@SettingsActivity, ScanPreferences.maxApkScanSizeEntries[pos].first)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Periodic scan
        binding.periodicScanSwitch.isChecked = SchedulePreferences.getPeriodicScanEnabled(this)
        binding.scanIntervalSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item,
            SchedulePreferences.scanIntervalEntries.map { getString(resources.getIdentifier(it.second, "string", packageName)) }
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        binding.scanIntervalSpinner.setSelection(
            SchedulePreferences.scanIntervalEntries.indexOfFirst { it.first == SchedulePreferences.getPeriodicScanInterval(this) }.coerceAtLeast(0)
        )
        binding.scanIntervalSpinner.isEnabled = binding.periodicScanSwitch.isChecked
        binding.periodicScanSwitch.setOnCheckedChangeListener { _, isChecked ->
            SchedulePreferences.setPeriodicScanEnabled(this, isChecked)
            binding.scanIntervalSpinner.isEnabled = isChecked
            ScheduleManager.applySchedule(this)
        }
        binding.scanIntervalSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, pos: Int, id: Long) {
                SchedulePreferences.setPeriodicScanInterval(this@SettingsActivity, SchedulePreferences.scanIntervalEntries[pos].first)
                ScheduleManager.applySchedule(this@SettingsActivity)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Auto rules update
        binding.autoRulesUpdateSwitch.isChecked = SchedulePreferences.getAutoRulesUpdateEnabled(this)
        binding.rulesUpdateIntervalSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item,
            SchedulePreferences.rulesIntervalEntries.map { getString(resources.getIdentifier(it.second, "string", packageName)) }
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        binding.rulesUpdateIntervalSpinner.setSelection(
            SchedulePreferences.rulesIntervalEntries.indexOfFirst { it.first == SchedulePreferences.getAutoRulesUpdateInterval(this) }.coerceAtLeast(0)
        )
        binding.rulesUpdateIntervalSpinner.isEnabled = binding.autoRulesUpdateSwitch.isChecked
        binding.autoRulesUpdateSwitch.setOnCheckedChangeListener { _, isChecked ->
            SchedulePreferences.setAutoRulesUpdateEnabled(this, isChecked)
            binding.rulesUpdateIntervalSpinner.isEnabled = isChecked
            ScheduleManager.applySchedule(this)
        }
        binding.rulesUpdateIntervalSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, pos: Int, id: Long) {
                SchedulePreferences.setAutoRulesUpdateInterval(this@SettingsActivity, SchedulePreferences.rulesIntervalEntries[pos].first)
                ScheduleManager.applySchedule(this@SettingsActivity)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.excludedRulesButton.setOnClickListener {
            startActivity(Intent(this, ExcludedRulesActivity::class.java))
        }

        binding.customRulesButton.setOnClickListener {
            startActivity(Intent(this, CustomRulesActivity::class.java))
        }

        binding.aboutButton.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
