package com.example.yaraxsample

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.yaraxsample.databinding.ActivityAboutBinding

class AboutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.about_title)

        val versionName = packageManager.getPackageInfo(packageName, 0).versionName
        binding.versionText.text = getString(R.string.about_version, versionName)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
