package com.quantumqr.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent

object ShareUtils {
    fun copy(ctx: Context, text: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("QR", text))
    }
    fun share(ctx: Context, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        ctx.startActivity(Intent.createChooser(intent, "Share result"))
    }
}