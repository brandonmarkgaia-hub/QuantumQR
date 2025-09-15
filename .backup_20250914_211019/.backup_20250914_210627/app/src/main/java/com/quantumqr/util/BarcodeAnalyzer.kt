package com.quantumqr.util


import com.quantumqr.R

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * CameraX analyzer that feeds frames to ML Kit and returns the first decoded value.
 * Call site passes a lambda: onResult(value, formatName).
 */
class BarcodeAnalyzer(
    private val onResult: (value: String, formatName: String) -> Unit
) : ImageAnalysis.Analyzer {

    private val scanner by lazy {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_QR_CODE,
                Barcode.FORMAT_AZTEC,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_CODE_39,
                Barcode.FORMAT_CODE_93,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_ITF,
                Barcode.FORMAT_PDF417,
                Barcode.FORMAT_DATA_MATRIX
            )
            .build()
        BarcodeScanning.getClient(options)
    }

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: run { imageProxy.close(); return }
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                if (barcodes.isNotEmpty()) {
                    val b = barcodes.first()
                    val value = b.rawValue ?: return@addOnSuccessListener
                    val formatName = formatToString(b.format)
                    onResult(value, formatName)
                }
            }
            .addOnFailureListener {
                // Ignore; keep analyzing frames
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun formatToString(format: Int): String = when (format) {
        Barcode.FORMAT_QR_CODE      -> "QR_CODE"
        Barcode.FORMAT_AZTEC        -> "AZTEC"
        Barcode.FORMAT_EAN_8        -> "EAN_8"
        Barcode.FORMAT_EAN_13       -> "EAN_13"
        Barcode.FORMAT_UPC_A        -> "UPC_A"
        Barcode.FORMAT_UPC_E        -> "UPC_E"
        Barcode.FORMAT_CODE_39      -> "CODE_39"
        Barcode.FORMAT_CODE_93      -> "CODE_93"
        Barcode.FORMAT_CODE_128     -> "CODE_128"
        Barcode.FORMAT_ITF          -> "ITF"
        Barcode.FORMAT_PDF417       -> "PDF417"
        Barcode.FORMAT_DATA_MATRIX  -> "DATA_MATRIX"
        else                        -> "UNKNOWN"
    }
}