package com.quantumqr.util

import android.graphics.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object QRUtils {

    fun generateQRCode(
        content: String,
        size: Int = 512,
        centerImage: Bitmap? = null
    ): Bitmap {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to 1
        )

        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)

        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }

        return if (centerImage != null) {
            addCenterImage(bitmap, centerImage)
        } else {
            bitmap
        }
    }

    private fun addCenterImage(qrBitmap: Bitmap, centerImage: Bitmap): Bitmap {
        val combined = Bitmap.createBitmap(qrBitmap.width, qrBitmap.height, qrBitmap.config)
        val canvas = Canvas(combined)
        canvas.drawBitmap(qrBitmap, 0f, 0f, null)

        // Scale center image to 20% of QR size
        val logoSize = qrBitmap.width / 5
        val scaledLogo = Bitmap.createScaledBitmap(centerImage, logoSize, logoSize, true)

        val left = (qrBitmap.width - logoSize) / 2f
        val top = (qrBitmap.height - logoSize) / 2f

        // Draw a white background for the logo to make it pop and preserve scannability
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.WHITE
        val rect = RectF(left - 4, top - 4, left + logoSize + 4, top + logoSize + 4)
        canvas.drawRoundRect(rect, 10f, 10f, paint)

        canvas.drawBitmap(scaledLogo, left, top, null)
        return combined
    }

    fun createVCard(name: String, phone: String, email: String, org: String = ""): String {
        return "BEGIN:VCARD\n" +
                "VERSION:3.0\n" +
                "FN:$name\n" +
                "ORG:$org\n" +
                "TEL:$phone\n" +
                "EMAIL:$email\n" +
                "END:VCARD"
    }

    fun createWifi(ssid: String, password: String, encryption: String = "WPA"): String {
        return "WIFI:S:$ssid;T:$encryption;P:$password;;"
    }

    fun createEmail(address: String, subject: String, body: String): String {
        return "MATMSG:TO:$address;SUB:$subject;BODY:$body;;"
    }

    fun createSms(phone: String, message: String): String {
        return "SMSTO:$phone:$message"
    }

    fun createPhone(phone: String): String {
        return "tel:$phone"
    }

    fun createGeo(lat: Double, lon: Double): String {
        return "geo:$lat,$lon"
    }
}
