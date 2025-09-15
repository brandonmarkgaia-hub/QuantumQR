package com.quantumqr.scanner
import java.io.File
import android.graphics.PointF
import android.os.Looper
import android.os.Handler
import com.quantumqr.scanner.ui.OverlayView

import com.quantumqr.util.Feedback
import com.quantumqr.util.ShareUtils

import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.camera.core.Camera
import androidx.camera.core.Preview
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.CameraSelector
import androidx.camera.core.TorchState
import androidx.lifecycle.LifecycleOwner

import com.quantumqr.R

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import java.util.concurrent.Executors

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.barcode.BarcodeScanning
import android.os.Environment
import android.os.SystemClock
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog

class ScannerActivity : ComponentActivity() {
/* NEUTRALIZED v1.4 (old) */ //     private var isActionSheetShowing = false
/* NEUTRALIZED v1.4 (old) */ //     private var lastActionAt = 0L
/* NEUTRALIZED v1.4 (old) */ //     private var lastActionText: String? = null
    private val actionHandler = Handler(Looper.getMainLooper())


    private var overlay: OverlayView? = null
    private var overlayAdded = false


    private var lastResultText: String? = null

    private var torchOn: Boolean = false

    private var cameraProvider: ProcessCameraProvider? = null


    private lateinit var previewView: PreviewView
    private lateinit var btnFlash: ImageButton
    private lateinit var btnHistory: ImageButton
    private lateinit var btnCopy: Button
    private lateinit var btnShare: Button
    private lateinit var btnOpen: Button
    private lateinit var txtResult: TextView

    private var camera: Camera? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    @Volatile private var busy = false
    private var lastText: String? = null

    private val askPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera() else Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scanner)

        previewView = findViewById(R.id.previewView)
        btnFlash = findViewById(R.id.btnFlash)
        btnHistory = findViewById(R.id.btnHistory)
        btnCopy = findViewById(R.id.btnCopy)
        btnShare = findViewById(R.id.btnShare)
        btnOpen = findViewById(R.id.btnOpen)
        txtResult = findViewById(R.id.txtResult)

        btnFlash.setOnClickListener {
            camera?.let { c ->
                val on = c.cameraInfo.torchState.value == TorchState.ON
                c.cameraControl.enableTorch(!on)
            }
        }
        btnHistory.setOnClickListener { Toast.makeText(this, "History coming soon", Toast.LENGTH_SHORT).show() }
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
                }, "Share with"))
            }
        }
        btnOpen.setOnClickListener {
            lastText?.let {
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
                catch (_: Exception) { Toast.makeText(this, "Not a URL", Toast.LENGTH_SHORT).show() }
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) startCamera()
        else askPermission.launch(Manifest.permission.CAMERA)
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()

            val preview = Preview.Builder()
                .setTargetRotation(previewView.display?.rotation ?: 0)
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
                                if (!text.isNullOrBlank()) {
                                    lastText = text
                                    runOnUiThread {
                                        txtResult.text = text
                                        txtResult.visibility = View.VISIBLE
                                    }
                                }
                            }
                            .addOnCompleteListener { proxy.close(); busy = false }
                    }
                }

            val selector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                provider.unbindAll()
                camera = provider.bindToLifecycle(this, selector, preview, analysis)
            this.camera = camera
            this.camera?.cameraInfo?.torchState?.observe(this) { state ->
                torchOn = (state == TorchState.ON)
                updateFlashUi(torchOn)
            }
            } catch (t: Throwable) {
                t.printStackTrace()
                Toast.makeText(this, "Camera init failed: ${t.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private fun startCameraSafe() {
        // Try to call your existing startCamera() via reflection if present; swallow if absent.
        try {
            val m = this::class.java.getDeclaredMethod("startCamera")
            m.isAccessible = true
            m.invoke(this)
        } catch (_: Throwable) { /* no-op */ }
    }

    private fun toggleTorch() {
        val c = camera ?: return
        c.cameraControl.enableTorch(!torchOn)
    }

            private  fun updateFlashUi(on: Boolean) {
        val v = findViewById<android.view.View>(R.id.btnFlash)
        when (v) {
            is android.widget.Button -> v.text = if (on) "Flash Off" else "Flash On"
            is android.widget.TextView -> v.text = if (on) "Flash Off" else "Flash On"
            is android.widget.ImageButton -> v.contentDescription = if (on) "Flash Off" else "Flash On"
        }
    }
    private fun ensureOverlay() {
        if (overlayAdded) return
        val root = findViewById<android.view.ViewGroup>(android.R.id.content)
        val existing = root.findViewWithTag<OverlayView>("scanOverlay")
        if (existing == null) {
            val v = OverlayView(this).apply { tag = "scanOverlay" }
            root.addView(v, android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            ))
            overlay = v
        } else {
            overlay = existing
        }
        overlayAdded = true
    }
    override fun onResume() {
        ensureOverlay()
super.onResume()
        ensureOverlay()
    }

    // --- v1.4 action sheet state (UI-only) ---
    private var isActionSheetShowing: Boolean = false
    private var lastActionAt: Long = 0L
    private var lastActionText: String? = null

    private fun maybeShowActionSheet(resultText: String) {
        if (isFinishing || isDestroyed) return
        val now = SystemClock.uptimeMillis()
        if (isActionSheetShowing) return
        if (lastActionText == resultText && (now - lastActionAt) < 1500L) return

        lastActionText = resultText
        lastActionAt = now
        isActionSheetShowing = true

        window?.decorView?.postDelayed({
            if (isFinishing || isDestroyed) { isActionSheetShowing = false; return@postDelayed }
            showActionSheet(resultText)
        }, 1000L)
    }

    private fun showActionSheet(resultText: String) {
        val sheet = BottomSheetDialog(this@ScannerActivity)

        val options = listOf("Open", "Save", "Share", "Copy", "Cancel")
        val list = ListView(this@ScannerActivity).apply {
            adapter = ArrayAdapter(this@ScannerActivity, android.R.layout.simple_list_item_1, options)
            setOnItemClickListener { _, _, i, _ ->
                when (options[i]) {
                    "Open"  -> openResult(resultText)
                    "Save"  -> saveResult(resultText)
                    "Share" -> shareResult(resultText)
                    "Copy"  -> copyResult(resultText)
                    else    -> { /* Cancel */ }
                }
                sheet.dismiss()
            }
        }

        sheet.setOnDismissListener {
            isActionSheetShowing = false
            lastActionAt = SystemClock.uptimeMillis()
        }
        sheet.setContentView(list)
        sheet.show()
    }

    private fun openResult(text: String) {
        runCatching {
            val uri = Uri.parse(text)
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        }.onFailure {
            copyResult(text)
            Toast.makeText(this@ScannerActivity, "Copied (not a link)", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveResult(text: String) {
        val baseDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: filesDir
        val outFile = File(baseDir, "qr_${System.currentTimeMillis()}.txt")
        runCatching {
            outFile.parentFile?.mkdirs()
            outFile.writeText(text, Charsets.UTF_8)
            Toast.makeText(this@ScannerActivity, "Saved: ${outFile.name}", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(this@ScannerActivity, "Save failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareResult(text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(send, "Share QR content"))
    }

    private fun copyResult(text: String) {
        val cm = getSystemService(ClipboardManager::class.java)
        cm?.setPrimaryClip(ClipData.newPlainText("QR content", text))
        Toast.makeText(this@ScannerActivity, "Copied", Toast.LENGTH_SHORT).show()
    }
}
    private fun scheduleResultActionSheet(text: String) {
        val now = android.os.SystemClock.uptimeMillis()
        if (isActionSheetShowing) return
        if (now - lastActionAt < 1200) return
        lastActionAt = now
        lastActionText = text
        actionHandler.postDelayed({
            if (isActionSheetShowing) return@postDelayed
            val items = arrayOf("Open", "Save", "Share", "Copy")
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("QR Result")
                .setItems(items) { _, which ->
                    when (which) {
                        0 -> { // Open
                            try {
                                val uri = Uri.parse(text)
                                val intent = Intent(Intent.ACTION_VIEW, uri)
                                startActivity(intent)
                            } catch (t: Throwable) { /* ignore */ }
                        }
                        1 -> { // Save to file (app external)
                            try {
                                val dir = getExternalFilesDir(null)
                                if (dir != null) {
                                    val f = java.io.File(dir, "qr-" + System.currentTimeMillis() + ".txt")
                                    f.writeText(text)
                                    android.widget.Toast.makeText(this, "Saved: " + f.name, android.widget.Toast.LENGTH_SHORT).show()
                                }
                            } catch (t: Throwable) { /* ignore */ }
                        }
                        2 -> { // Share
                            try { com.quantumqr.util.ShareUtils.share(this, text) } catch (t: Throwable) { /* ignore */ }
                        }
                        3 -> { // Copy
                            try { com.quantumqr.util.ShareUtils.copy(this, text) } catch (t: Throwable) { /* ignore */ }
                        }
                    }
                }
                .setOnDismissListener { isActionSheetShowing = false }
                .create().also { dlg ->
                    isActionSheetShowing = true
                    dlg.show()
                }
        }, 1000L) // 1s after green
    }
