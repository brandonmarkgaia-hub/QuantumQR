package com.quantumqr.util


import com.quantumqr.R

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat

object UiUtils {

    fun copyToClipboard(ctx: Context, text: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("scan", text))
        Toast.makeText(ctx, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    fun shareText(ctx: Context, text: String) {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        ctx.startActivity(Intent.createChooser(i, "Share via"))
    }

    fun openUrl(ctx: Context, url: String) {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        ctx.startActivity(i)
    }

    fun productWebSearch(ctx: Context, code: String) {
        val url = "https://www.google.com/search?q=$code"
        openUrl(ctx, url)
    }

    fun vibrateSuccess(ctx: Context) {
        val v = ContextCompat.getSystemService(ctx, Vibrator::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            v.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION") v.vibrate(50)
        }
    }

    fun vibrateInfo(ctx: Context) {
        val v = ContextCompat.getSystemService(ctx, Vibrator::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            v.vibrate(VibrationEffect.createOneShot(20, 40))
        } else {
            @Suppress("DEPRECATION") v.vibrate(20)
        }
    }

    fun toggleTheme(ctx: Context, dark: Boolean) {
        AppCompatDelegate.setDefaultNightMode(
            if (dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
        ctx.getSharedPreferences("quantumqr_theme", Context.MODE_PRIVATE)
            .edit().putBoolean("dark", dark).apply()
    }

    fun applySavedTheme(ctx: Context) {
        val dark = ctx.getSharedPreferences("quantumqr_theme", Context.MODE_PRIVATE)
            .getBoolean("dark", false)
        AppCompatDelegate.setDefaultNightMode(
            if (dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }
}