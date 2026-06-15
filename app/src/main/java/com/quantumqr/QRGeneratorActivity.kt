package com.quantumqr

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.quantumqr.databinding.ActivityQrGeneratorBinding
import com.quantumqr.util.QRUtils
import kotlinx.coroutines.launch
import java.io.File
import java.io.OutputStream

class QRGeneratorActivity : AppCompatActivity() {
    private lateinit var binding: ActivityQrGeneratorBinding
    private var selectedPhoto: Bitmap? = null
    private var generatedQr: Bitmap? = null
    
    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var isRecording = false

    private val pickPhotoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val inputStream = contentResolver.openInputStream(it)
            val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            selectedPhoto = bitmap
            Toast.makeText(this, "Photo selected", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestAudioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording() else Toast.makeText(this, "Audio permission needed", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQrGeneratorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupTypeDropdown()
        setupSwitches()

        binding.btnPickPhoto.setOnClickListener {
            pickPhotoLauncher.launch("image/*")
        }

        binding.btnGenerate.setOnClickListener {
            generateQR()
        }

        binding.btnSave.setOnClickListener {
            saveToGallery()
        }

        binding.btnRecord.setOnClickListener {
            if (isRecording) stopRecording() else checkAudioPermission()
        }
    }

    private fun setupTypeDropdown() {
        val types = arrayOf(
            getString(R.string.type_text),
            getString(R.string.type_wifi),
            getString(R.string.type_vcard),
            getString(R.string.type_voice)
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, types)
        binding.typeDropdown.setAdapter(adapter)
        binding.typeDropdown.setText(types[0], false)

        binding.typeDropdown.setOnItemClickListener { _, _, position, _ ->
            updateInputVisibility(position)
        }
    }

    private fun updateInputVisibility(position: Int) {
        binding.inputLayoutText.visibility = if (position == 0) View.VISIBLE else View.GONE
        binding.layoutWifi.visibility = if (position == 1) View.VISIBLE else View.GONE
        binding.layoutVcard.visibility = if (position == 2) View.VISIBLE else View.GONE
        binding.layoutVoice.visibility = if (position == 3) View.VISIBLE else View.GONE
    }

    private fun setupSwitches() {
        binding.swGenericLogo.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.swPersonalizedPhoto.isChecked = false
                binding.btnPickPhoto.visibility = View.GONE
            }
        }

        binding.swPersonalizedPhoto.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.swGenericLogo.isChecked = false
                binding.btnPickPhoto.visibility = View.VISIBLE
            } else {
                binding.btnPickPhoto.visibility = View.GONE
            }
        }
    }

    private fun checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRecording()
        } else {
            requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startRecording() {
        audioFile = File(externalCacheDir, "temp_voice.m4a")
        mediaRecorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else MediaRecorder()).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(audioFile?.absolutePath)
            prepare()
            start()
        }
        isRecording = true
        binding.btnRecord.text = "Stop Recording"
        binding.txtRecordStatus.text = "Recording..."
    }

    private fun stopRecording() {
        mediaRecorder?.apply {
            stop()
            release()
        }
        mediaRecorder = null
        isRecording = false
        binding.btnRecord.text = "Record Again"
        binding.txtRecordStatus.text = "Voice clip saved!"
    }

    private fun generateQR() {
        val type = binding.typeDropdown.text.toString()
        val content = when (type) {
            getString(R.string.type_text) -> binding.etContent.text.toString()
            getString(R.string.type_wifi) -> QRUtils.createWifi(
                binding.etSsid.text.toString(),
                binding.etPassword.text.toString()
            )
            getString(R.string.type_vcard) -> QRUtils.createVCard(
                name = binding.etName.text.toString(),
                org = binding.etCompany.text.toString(),
                title = binding.etJobTitle.text.toString(),
                phone = binding.etPhone.text.toString(),
                workPhone = binding.etWorkPhone.text.toString(),
                email = binding.etEmail.text.toString(),
                address = binding.etAddress.text.toString(),
                url = binding.etWebsite.text.toString()
            )
            getString(R.string.type_voice) -> "VOICE_ID:${System.currentTimeMillis()}" // Placeholder for sound graph logic
            else -> ""
        }

        if (content.isBlank()) {
            Toast.makeText(this, "Please enter some content", Toast.LENGTH_SHORT).show()
            return
        }

        var centerImg: Bitmap? = null
        if (binding.swGenericLogo.isChecked) {
            centerImg = getLogoBitmap()
        } else if (binding.swPersonalizedPhoto.isChecked) {
            centerImg = selectedPhoto
            if (centerImg == null) {
                Toast.makeText(this, "Please pick a photo first", Toast.LENGTH_SHORT).show()
                return
            }
        }

        try {
            if (type == getString(R.string.type_voice)) {
                // Special Soundwave Tattoo generation
                val baseQr = QRUtils.generateQRCode(content, 512, null)
                val soundwave = QRUtils.generateSoundwaveOverlay(200, 200, ContextCompat.getColor(this, R.color.neon_pink))
                generatedQr = addSoundwaveToQr(baseQr, soundwave)
            } else {
                generatedQr = QRUtils.generateQRCode(content, 512, centerImg)
            }
            
            binding.qrImage.setImageBitmap(generatedQr)
            binding.btnSave.visibility = View.VISIBLE
            
            // Save to history
            lifecycleScope.launch {
                com.quantumqr.data.ScanRepository.get(this@QRGeneratorActivity)
                    .add(content, if (type == getString(R.string.type_voice)) "VOICE" else "GENERATED", content.startsWith("http"), System.currentTimeMillis())
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error generating QR: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addSoundwaveToQr(qr: Bitmap, soundwave: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(qr.width, qr.height, qr.config)
        val canvas = Canvas(result)
        canvas.drawBitmap(qr, 0f, 0f, null)
        
        // Place soundwave in the middle with a white backing
        val left = (qr.width - soundwave.width) / 2f
        val top = (qr.height - soundwave.height) / 2f
        
        val p = android.graphics.Paint()
        p.color = android.graphics.Color.WHITE
        canvas.drawRect(left - 10, top - 10, left + soundwave.width + 10, top + soundwave.height + 10, p)
        canvas.drawBitmap(soundwave, left, top, null)
        
        return result
    }

    private fun getLogoBitmap(): Bitmap? {
        val drawable = ContextCompat.getDrawable(this, R.drawable.ic_logo_foreground) ?: return null
        if (drawable is BitmapDrawable) return drawable.bitmap

        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun saveToGallery() {
        val bitmap = generatedQr ?: return
        val filename = "QR_${System.currentTimeMillis()}.png"
        var fos: OutputStream? = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentResolver?.also { resolver ->
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/QuantumQR")
                }
                val imageUri: Uri? = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                fos = imageUri?.let { resolver.openOutputStream(it) }
            }
        } else {
            val imagesDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES).toString()
            val image = java.io.File(imagesDir, filename)
            fos = java.io.FileOutputStream(image)
        }

        fos?.use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            Toast.makeText(this, "Saved to Gallery", Toast.LENGTH_SHORT).show()
        }
    }
}
