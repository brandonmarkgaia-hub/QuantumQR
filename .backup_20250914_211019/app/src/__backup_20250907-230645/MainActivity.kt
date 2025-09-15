package com.quantumqr.main.java.com.quantumqr

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.quantumqr.util.BarcodeAnalyzer
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var tts: TextToSpeech
    private val chime by lazy { ToneGenerator(AudioManager.STREAM_MUSIC, 55) }
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    @Volatile private var handled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        previewView = findViewById(R.id.previewView)

        // open generator screen
        findViewById<ImageButton>(R.id.btnGenerate)?.setOnClickListener {
            startActivity(Intent(this, QRGeneratorActivity::class.java))
        }

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) tts.language = Locale.UK
        }

        ensureCameraPermission()
    }

    private fun ensureCameraPermission() {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) startCamera() else requestPermissions(arrayOf(Manifest.permission.CAMERA), 1001)
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (code == 1001 && results.isNotEmpty() && results[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()

            val preview = Preview.Builder().build().apply {
                setSurfaceProvider(previewView.surfaceProvider)
            }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build().also { ia ->
                    ia.setAnalyzer(cameraExecutor, BarcodeAnalyzer { text ->
                        if (!handled) {
                            handled = true
                            onQrDetected(text ?: "")
                        }
                    })
                }

            try {
                provider.unbindAll()
                provider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis
                )
            } catch (t: Throwable) {
                t.printStackTrace()
                Toast.makeText(this, "Camera init failed: ", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun onQrDetected(payload: String) {
        // chime + voice line
        chime.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
        tts.speak("QR code safe, opening now", TextToSpeech.QUEUE_FLUSH, null, "scan-ok")

        // open/handle content
        val uri = runCatching { Uri.parse(payload) }.getOrNull()
        if (uri != null && (uri.scheme?.startsWith("http") == true)) {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } else {
            Toast.makeText(this, payload, Toast.LENGTH_LONG).show()
        }

        // re-arm after a short delay so rapid detections donâ€™t spam
        previewView.postDelayed({ handled = false }, 1200)
    }

    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.stop(); tts.shutdown()
        }
        chime.release()
        cameraExecutor.shutdown()
        super.onDestroy()
    }
}
