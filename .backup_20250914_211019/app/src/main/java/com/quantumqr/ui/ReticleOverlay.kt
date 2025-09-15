package com.quantumqr.ui


import com.quantumqr.R

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.withSave

class ReticleOverlay @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : View(ctx, attrs) {

    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x99000000.toInt() // 60% black
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 2f
    }
    private val path = Path()
    private val r = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val boxW = w * 0.65f
        val boxH = boxW
        val cx = w / 2f
        val cy = h / 2f
        r.set(cx - boxW/2, cy - boxH/2, cx + boxW/2, cy + boxH/2)
        val radius = resources.displayMetrics.density * 14f

        // Dim full screen
        canvas.drawRect(0f, 0f, w, h, dimPaint)

        // Punch a transparent hole (destination-out)
        val saved = canvas.saveLayer(0f, 0f, w, h, null)
        canvas.drawRect(0f, 0f, w, h, Paint().apply { color = Color.TRANSPARENT })
        path.reset(); path.addRoundRect(r, radius, radius, Path.Direction.CW)
        val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
        canvas.drawPath(path, clear)
        canvas.restoreToCount(saved)

        // White border
        canvas.withSave {
            drawRoundRect(r, radius, radius, strokePaint)
        }
    }
}