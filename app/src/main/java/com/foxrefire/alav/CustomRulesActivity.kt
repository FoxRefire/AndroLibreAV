package com.foxrefire.alav

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.foxrefire.alav.databinding.ActivityCustomRulesBinding

/**
 * Activity for editing and saving user-defined YARA rules.
 * Rules are merged with yara-forge rules when scanning.
 */
class CustomRulesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCustomRulesBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomRulesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val rulesRepo = RulesRepository(this)
        binding.rulesEdit.setText(rulesRepo.loadCustomRules())

        binding.saveButton.setOnClickListener {
            val rules = binding.rulesEdit.text.toString()
            rulesRepo.saveCustomRules(rules)
            Toast.makeText(this, R.string.custom_rules_saved, Toast.LENGTH_SHORT).show()
        }
    }
}
