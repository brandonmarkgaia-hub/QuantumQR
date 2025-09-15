package com.quantumqr.scanner

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast

var isActionSheetShowing: Boolean = false
var lastActionAt: Long = 0L
var lastActionText: String? = null
var actionHandler: ((String) -> Unit)? = null

fun ScannerActivity.// maybeShowActionSheet(decodedText: String) {
    val now = SystemClock.elapsedRealtime()
    if (isActionSheetShowing) return
    if (now - lastActionAt < 900L && decodedText == lastActionText) return
    lastActionAt = now
    lastActionText = decodedText
    isActionSheetShowing = true
    Toast.makeText(this@ScannerActivity, decodedText, Toast.LENGTH_SHORT).show()
    Handler(Looper.getMainLooper()).postDelayed({ isActionSheetShowing = false }, 900L)
}