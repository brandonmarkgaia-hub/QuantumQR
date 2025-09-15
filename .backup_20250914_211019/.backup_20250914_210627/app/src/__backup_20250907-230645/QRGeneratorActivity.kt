package com.quantumqr.main.java.com.quantumqr

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import java.io.File
import java.io.FileOutputStream

class QRGeneratorActivity : AppCompatActivity() {
    private lateinit var input: EditText
    private lateinit var img: ImageView
    private var lastFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_generator)
        input = findViewById(R.id.input)
        img   = findViewById(R.id.qrImage)

        findViewById<Button>(R.id.generateBtn).setOnClickListener {
            val content = input.text.toString().trim()
            if (content.isNotEmpty()) {
                val bmp = makeQr(content, 1024, 1024)
                img.setImageBitmap(bmp)
                lastFile = saveToCache(bmp, "qr_share.png")
            }
        }

        findViewById<Button>(R.id.shareBtn).setOnClickListener {
            lastFile?.let { file ->
                val uri: Uri = FileProvider.getUriForFile(
                    this, "{packageName}.fileprovider", file
                )
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, input.text.toString())
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    // setPackage("com.whatsapp") // uncomment to prefer WhatsApp
                }
                startActivity(Intent.createChooser(send, "Share QR"))
            }
        }
    }

    private fun makeQr(text: String, w: Int, h: Int): Bitmap {
        val matrix: BitMatrix = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, w, h, null)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        for (y in 0 until h) for (x in 0 until w) {
            bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
        }
        return bmp
    }

    private fun saveToCache(bmp: Bitmap, name: String): File {
        val f = File(cacheDir, name)
        FileOutputStream(f).use { out -> bmp.compress(Bitmap.CompressFormat.PNG, 100, out) }
        return f
    }
}
