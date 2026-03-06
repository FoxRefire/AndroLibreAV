package com.foxrefire.alav

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.foxrefire.alav.databinding.ActivityAboutBinding

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

        binding.linkApp.setOnClickListener { openUrl("https://github.com/FoxRefire/android-antimalware-app") }
        binding.linkYaraX.setOnClickListener { openUrl("https://github.com/VirusTotal/yara-x") }
        binding.linkYaraForge.setOnClickListener { openUrl("https://github.com/YARAHQ/yara-forge") }
    }

    private fun openUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
