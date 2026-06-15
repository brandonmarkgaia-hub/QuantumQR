package com.quantumqr

import android.content.Context
import android.os.Bundle
import android.widget.Toast
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

        binding.rgTheme.apply {
            val savedTheme = prefs.getString("app_theme", "NEON")
            check(when(savedTheme) {
                "PURPLE" -> R.id.rbPurple
                "MATRIX" -> R.id.rbMatrix
                else -> R.id.rbNeon
            })

            setOnCheckedChangeListener { _, checkedId ->
                val theme = when(checkedId) {
                    R.id.rbPurple -> "PURPLE"
                    R.id.rbMatrix -> "MATRIX"
                    else -> "NEON"
                }
                prefs.edit { putString("app_theme", theme) }
                Toast.makeText(this@SettingsActivity, "Theme applied! Restart app for full effect.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
