package com.quantumqr

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.quantumqr.databinding.SettingsBasicBinding

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: SettingsBasicBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SettingsBasicBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // TODO: hook up settings UI (binding.swTts etc.)
    }
}
