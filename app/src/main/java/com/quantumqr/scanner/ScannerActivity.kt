package com.quantumqr.scanner

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
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
import com.google.android.material.card.MaterialCardView
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.quantumqr.R
import java.util.concurrent.Executors

@OptIn(ExperimentalGetImage::class)
class ScannerActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var btnFlash: ImageButton
    private lateinit var btnHistory: ImageButton
    private lateinit var btnSettings: ImageButton
    private lateinit var btnGoToGenerate: ImageButton
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

    private val askPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera() else Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scanner)

        // Initialize UI
        previewView = findViewById(R.id.previewView)
        btnFlash = findViewById(R.id.btnFlash)
        btnHistory = findViewById(R.id.btnHistory)
        btnSettings = findViewById(R.id.btnSettings)
        btnGoToGenerate = findViewById(R.id.btnGoToGenerate)
        btnCopy = findViewById(R.id.btnCopy)
        btnShare = findViewById(R.id.btnShare)
        btnOpen = findViewById(R.id.btnOpen)
        txtResult = findViewById(R.id.txtResult)
        resultCard = findViewById(R.id.resultCard)

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
            Toast.makeText(this, "History coming soon", Toast.LENGTH_SHORT).show() 
        }

        btnCopy.setOnClickListener {
            lastText?.let {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("QR", it))
                Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                // Auto-hide card after copy? Optional.
            }
        }

        btnShare.setOnClickListener {
            lastText?.let {
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, it)
                }, "Share Result"))
            }
        }

        btnOpen.setOnClickListener {
            lastText?.let {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it)))
                } catch (_: Exception) {
                    Toast.makeText(this, "Not a valid link", Toast.LENGTH_SHORT).show()
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

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()

            val preview = Preview.Builder()
                .setTargetRotation(previewView.display?.rotation ?: android.view.Surface.ROTATION_0)
                .build().also { it.setSurfaceProvider(previewView.surfaceProvider) }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build().also { ia ->
                    ia.setAnalyzer(cameraExecutor) { proxy ->
                        val media = proxy.image ?: run { proxy.close(); return@setAnalyzer }
                        if (busy) { proxy.close(); return@setAnalyzer }
                        busy = true
                        
                        val img = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                        BarcodeScanning.getClient().process(img)
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
                Toast.makeText(this, "Camera error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun showResult(text: String) {
        txtResult.text = text
        resultCard.visibility = View.VISIBLE
        // Futuristic feel: Haptic feedback on scan
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        vibrator.vibrate(50)
    }

    private fun updateTorchUi(on: Boolean) {
        torchOn = on
        btnFlash.setImageResource(if (on) R.drawable.ic_torch_on_24 else R.drawable.ico_torch)
        btnFlash.imageTintList = android.content.res.ColorStateList.valueOf(
            if (on) ContextCompat.getColor(this, R.color.brandPrimary) 
            else ContextCompat.getColor(this, R.color.neon_blue)
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
