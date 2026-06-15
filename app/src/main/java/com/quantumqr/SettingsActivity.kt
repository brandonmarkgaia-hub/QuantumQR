package com.quantumqr

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.quantumqr.databinding.SettingsBasicBinding

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: SettingsBasicBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SettingsBasicBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)

        binding.swTts.apply {
            isChecked = prefs.getBoolean("tts_enabled", false)
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit { putBoolean("tts_enabled", isChecked) }
            }
        }

        binding.swVibrate.apply {
            isChecked = prefs.getBoolean("vibrate_enabled", true)
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit { putBoolean("vibrate_enabled", isChecked) }
            }
        }

        binding.swCopy.apply {
            isChecked = prefs.getBoolean("copy_enabled", true)
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit { putBoolean("copy_enabled", isChecked) }
            }
        }
    }
}
