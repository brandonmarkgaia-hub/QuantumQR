package com.quantumqr

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Jump straight into the scanner
        startActivity(Intent(this, com.quantumqr.scanner.ScannerActivity::class.java))
        finish()
    }
}
