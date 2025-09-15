package com.quantumqr

import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.createBitmap
import com.quantumqr.databinding.ActivityQrGeneratorBinding

class QRGeneratorActivity : AppCompatActivity() {
    private lateinit var binding: ActivityQrGeneratorBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQrGeneratorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Compile-safe placeholder: a blank bitmap
        val bmp = createBitmap(512, 512)
        bmp.eraseColor(Color.WHITE)
        binding.qrImage.setImageBitmap(bmp)
    }
}
