package com.quantumqr.scanner


import com.quantumqr.R

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

class FinderOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val boxSizeDp = 220f
    private val strokePx = resources.displayMetrics.density * 3
    private val cornerLenPx = resources.displayMetrics.density * 22

    private val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = strokePx
    }

    private val dim = Paint().apply {
        color = 0x66000000
        style = Paint.Style.FILL
    }

    private val boxRect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val half = (boxSizeDp * resources.displayMetrics.density) / 2f

        boxRect.set(cx - half, cy - half, cx + half, cy + half)

        // Dim outside area
        canvas.drawRect(0f, 0f, width.toFloat(), boxRect.top, dim)
        canvas.drawRect(0f, boxRect.bottom, width.toFloat(), height.toFloat(), dim)
        canvas.drawRect(0f, boxRect.top, boxRect.left, boxRect.bottom, dim)
        canvas.drawRect(boxRect.right, boxRect.top, width.toFloat(), boxRect.bottom, dim)

        // Corners
        drawCorners(canvas, boxRect)
    }

    private fun drawCorners(c: Canvas, r: RectF) {
        val cl = cornerLenPx
        val l = r.left; val t = r.top; val rgt = r.right; val b = r.bottom

        // TL
        c.drawLine(l, t, l + cl, t, frame)
        c.drawLine(l, t, l, t + cl, frame)
        // TR
        c.drawLine(rgt - cl, t, rgt, t, frame)
        c.drawLine(rgt, t, rgt, t + cl, frame)
        // BL
        c.drawLine(l, b, l + cl, b, frame)
        c.drawLine(l, b - cl, l, b, frame)
        // BR
        c.drawLine(rgt - cl, b, rgt, b, frame)
        c.drawLine(rgt, b - cl, rgt, b, frame)
    }
}