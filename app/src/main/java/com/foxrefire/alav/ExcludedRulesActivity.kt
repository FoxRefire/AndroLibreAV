package com.foxrefire.alav

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.foxrefire.alav.databinding.ActivityExcludedRulesBinding
import org.json.JSONArray
import org.json.JSONObject

/**
 * Activity for editing rule names to exclude from detection.
 * One rule name per line. Matching these rules will not be reported as detections.
 */
class ExcludedRulesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExcludedRulesBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExcludedRulesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val current = ScanPreferences.getExcludedRuleNames(this)
        binding.excludedRulesEdit.setText(current.sorted().joinToString("\n"))

        binding.saveExcludedRulesButton.setOnClickListener {
            val text = binding.excludedRulesEdit.text.toString()
            val names = text.lines()
                .map { it.trim().removePrefix("\uFEFF") }
                .filter { it.isNotEmpty() }
                .toSet()
            // #region agent log
            try {
                val payload = JSONObject().apply {
                    put("sessionId", "e19a5f")
                    put("hypothesisId", "D")
                    put("location", "ExcludedRulesActivity.kt:save")
                    put("message", "saving excluded rules")
                    put("data", JSONObject().apply {
                        put("size", names.size)
                        put("list", JSONArray(names.toList()))
                        put("firstBytes", names.firstOrNull()?.toByteArray(Charsets.UTF_8)?.joinToString(",") { it.toString() })
                    })
                    put("timestamp", System.currentTimeMillis())
                }.toString()
                Log.d("ExcludedRulesDebug", payload)
            } catch (_: Exception) {}
            // #endregion
            ScanPreferences.setExcludedRuleNames(this, names)
            Toast.makeText(this, R.string.excluded_rules_saved, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
