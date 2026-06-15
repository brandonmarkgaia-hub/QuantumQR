package com.quantumqr.scanner

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.quantumqr.R
import com.quantumqr.data.ScanRepository
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@OptIn(ExperimentalGetImage::class)
class ScannerActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var btnFlash: ImageButton
    private lateinit var btnHistory: ImageButton
    private lateinit var btnSettings: ImageButton
    private lateinit var btnGoToGenerate: ImageButton
    private lateinit var btnGallery: ImageButton
    private lateinit var btnCopy: Button
    private lateinit var btnShare: Button
    private lateinit var btnOpen: Button
    private lateinit var txtResult: TextView
    private lateinit var resultCard: MaterialCardView

    private var camera: Camera? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    @Volatile private var busy = false
    private var lastText: String? = null
    private var torchOn = false

    private val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)

    private val askPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera() else Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { decodeFromUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scanner)

        previewView = findViewById(R.id.previewView)
        btnFlash = findViewById(R.id.btnFlash)
        btnHistory = findViewById(R.id.btnHistory)
        btnSettings = findViewById(R.id.btnSettings)
        btnGoToGenerate = findViewById(R.id.btnGoToGenerate)
        btnGallery = findViewById(R.id.btnGallery)
        btnCopy = findViewById(R.id.btnCopy)
        btnShare = findViewById(R.id.btnShare)
        btnOpen = findViewById(R.id.btnOpen)
        txtResult = findViewById(R.id.txtResult)
        resultCard = findViewById(R.id.resultCard)

        applyThemeUI()

        btnFlash.setOnClickListener {
            camera?.let { c ->
                val on = c.cameraInfo.torchState.value == TorchState.ON
                c.cameraControl.enableTorch(!on)
            }
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(this, com.quantumqr.SettingsActivity::class.java))
        }

        btnGoToGenerate.setOnClickListener {
            startActivity(Intent(this, com.quantumqr.QRGeneratorActivity::class.java))
        }

        btnHistory.setOnClickListener {
            startActivity(Intent(this, com.quantumqr.ui.HistoryActivity::class.java))
        }

        btnGallery.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        btnCopy.setOnClickListener {
            lastText?.let {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("QR", it))
                Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
            }
        }

        btnShare.setOnClickListener {
            lastText?.let {
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, it)
                }, "Share"))
            }
        }

        btnOpen.setOnClickListener {
            lastText?.let {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it)))
                } catch (_: Exception) {
                    Toast.makeText(this, "Invalid link", Toast.LENGTH_SHORT).show()
                }
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            askPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun applyThemeUI() {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val theme = prefs.getString("app_theme", "NEON")
        val mainColor = when(theme) {
            "MATRIX" -> ContextCompat.getColor(this, R.color.matrix_green)
            "PURPLE" -> ContextCompat.getColor(this, R.color.cyber_purple)
            "VAPORWAVE" -> ContextCompat.getColor(this, R.color.vaporwave_pink)
            "LUXURY" -> ContextCompat.getColor(this, R.color.luxury_gold)
            "MIDNIGHT" -> ContextCompat.getColor(this, R.color.midnight_ruby)
            "NORDIC" -> ContextCompat.getColor(this, R.color.nordic_ice)
            "SYNTHWAVE" -> ContextCompat.getColor(this, R.color.synthwave_orange)
            "GLITCH" -> ContextCompat.getColor(this, R.color.glitch_yellow)
            "SAKURA" -> ContextCompat.getColor(this, R.color.sakura_pink)
            else -> ContextCompat.getColor(this, R.color.neon_blue)
        }
        
        val secondaryColor = if (theme == "VAPORWAVE") ContextCompat.getColor(this, R.color.vaporwave_blue) else mainColor

        val tintList = android.content.res.ColorStateList.valueOf(mainColor)
        val secondaryTintList = android.content.res.ColorStateList.valueOf(secondaryColor)

        btnHistory.imageTintList = tintList
        btnSettings.imageTintList = tintList
        btnGallery.imageTintList = tintList
        btnFlash.imageTintList = tintList
        btnGoToGenerate.imageTintList = secondaryTintList
        
        resultCard.strokeColor = mainColor
        btnCopy.setTextColor(mainColor)
        btnShare.setTextColor(mainColor)
        btnOpen.setTextColor(secondaryColor)
    }

    override fun onResume() {
        super.onResume()
        applyThemeUI()
        findViewById<ViewfinderOverlay>(R.id.viewfinderOverlay).apply {
            onAttachedToWindow()
            invalidate()
        }
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()

            val preview = Preview.Builder()
                .setTargetRotation(previewView.display?.rotation ?: android.view.Surface.ROTATION_0)
                .build().also { it.setSurfaceProvider(previewView.surfaceProvider) }

            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build()
            val scanner = BarcodeScanning.getClient(options)

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build().also { ia ->
                    ia.setAnalyzer(cameraExecutor) { proxy ->
                        val media = proxy.image ?: run { proxy.close(); return@setAnalyzer }
                        if (busy) { proxy.close(); return@setAnalyzer }
                        busy = true
                        
                        val img = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                        scanner.process(img)
                            .addOnSuccessListener { list ->
                                val text = list.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                                if (!text.isNullOrBlank() && text != lastText) {
                                    lastText = text
                                    runOnUiThread {
                                        showResult(text)
                                    }
                                }
                            }
                            .addOnCompleteListener { 
                                proxy.close()
                                busy = false 
                            }
                    }
                }

            try {
                provider.unbindAll()
                val cam = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                this.camera = cam
                cam.cameraInfo.torchState.observe(this) { state ->
                    updateTorchUi(state == TorchState.ON)
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Camera error", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun decodeFromUri(uri: Uri) {
        try {
            val img = InputImage.fromFilePath(this, uri)
            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build()
            BarcodeScanning.getClient(options).process(img)
                .addOnSuccessListener { list ->
                    val text = list.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                    if (!text.isNullOrBlank()) {
                        lastText = text
                        showResult(text)
                    } else {
                        Toast.makeText(this, "No code found", Toast.LENGTH_SHORT).show()
                    }
                }
        } catch (e: Exception) {
            Toast.makeText(this, "Load failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showResult(text: String) {
        txtResult.text = text
        resultCard.visibility = View.VISIBLE
        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2)

        lifecycleScope.launch {
            ScanRepository.get(this@ScannerActivity)
                .add(text, "SCAN", text.startsWith("http"), System.currentTimeMillis())
        }

        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        vibrator.vibrate(50)
    }

    private fun updateTorchUi(on: Boolean) {
        torchOn = on
        btnFlash.setImageResource(if (on) R.drawable.ic_torch_on_24 else R.drawable.ico_torch)
        applyThemeUI() // Refresh tint
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        toneGenerator.release()
    }
}
