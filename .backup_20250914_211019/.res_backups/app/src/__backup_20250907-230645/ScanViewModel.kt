package com.quantumqr.main.java.com.quantumqr.viewmodel

import android.app.Application
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.quantumqr.data.AppDatabase
import com.quantumqr.data.ScanEntity
import com.quantumqr.util.QRGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

class ScanViewModel(app: Application): AndroidViewModel(app) {

    private val db = AppDatabase.get(app)
    private val scansDao = db.scansDao()

    private val _lastScan = MutableStateFlow<String?>(null)
    val lastScan: StateFlow<String?> = _lastScan.asStateFlow()

    val history = scansDao.observeAll()

    private val _cameraPermission = MutableStateFlow(false)
    val cameraPermission = _cameraPermission.asStateFlow()

    private val _autoOpen = MutableStateFlow(true)
    val autoOpen = _autoOpen.asStateFlow()

    fun setCameraPermission(granted: Boolean) { _cameraPermission.value = granted }

    fun onScan(content: String) {
        viewModelScope.launch {
            val entity = ScanEntity(
                content = content,
                format = "AUTO",
                timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            )
            scansDao.insert(entity)
            _lastScan.value = content

            if (_autoOpen.first()) {
                openResult(content)
            }
        }
    }

    fun clearLast() { _lastScan.value = null }

    fun openResult(content: String) {
        val app = getApplication<Application>()
        try {
            if (content.startsWith("http://") || content.startsWith("https://")) {
                val customTabsIntent = CustomTabsIntent.Builder().build()
                customTabsIntent.launchUrl(app, Uri.parse(content))
            } else {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, content)
                }
                val shareIntent = Intent.createChooser(sendIntent, "Share scanned content")
                shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                app.startActivity(shareIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun exportCsv() {
        viewModelScope.launch {
            val list = scansDao.getAllOnce()
            val csv = buildString {
                append("timestamp,format,content\n")
                list.forEach {
                    append("\"${it.timestamp}\",\"${it.format}\",\"${it.content.replace("\"","\"\"")}\"\n")
                }
            }
            val resolver = getApplication<Application>().contentResolver
            val fileName = "quantumqr_history_${System.currentTimeMillis()}.csv"
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                resolver.openOutputStream(it)?.use { os ->
                    os.write(csv.toByteArray())
                }
            }
        }
    }

    fun setAutoOpen(value: Boolean) { _autoOpen.value = value }

    fun generateQrPng(text: String): Uri? {
        if (text.isBlank()) return null
        val app = getApplication<Application>()
        val bitmap: Bitmap = QRGenerator.generate(text, 1024, 1024)
        val resolver = app.contentResolver
        val fileName = "quantumqr_${System.currentTimeMillis()}.png"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        uri?.let {
            resolver.openOutputStream(it)?.use { out: OutputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
        return uri
    }
}

