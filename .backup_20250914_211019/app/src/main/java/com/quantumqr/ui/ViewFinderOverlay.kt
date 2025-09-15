package com.quantumqr.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class ViewfinderOverlay @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private fun dp(v: Float) = v * resources.displayMetrics.density

    private val maskPaint = Paint().apply {
        color = Color.parseColor("#66000000") // translucent black
        isAntiAlias = true
    }

    private val framePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        isAntiAlias = true
    }

    private val cornerPaint = Paint().apply {
        color = Color.parseColor("#FF3DDC84") // nice teal/green
        style = Paint.Style.STROKE
        strokeWidth = dp(4f)
        isAntiAlias = true
    }

    /** Fraction of the smallest screen dimension used for the square box */
    var frameRatio: Float = 0.65f

    /** Latest computed frame rect (useful if you want hit testing later) */
    var frameRect: RectF? = null
        private set

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val side = minOf(w, h) * frameRatio
        val left = (w - side) / 2f
        val top  = (h - side) / 2.5f // sit a little higher than center
        val rect = RectF(left, top, left + side, top + side)
        frameRect = rect

        // 1) Mask outside the rect
        canvas.drawRect(0f, 0f, w, rect.top, maskPaint)
        canvas.drawRect(0f, rect.top, rect.left, rect.bottom, maskPaint)
        canvas.drawRect(rect.right, rect.top, w, rect.bottom, maskPaint)
        canvas.drawRect(0f, rect.bottom, w, h, maskPaint)

        // 2) Main rounded frame
        val r = dp(12f)
        canvas.drawRoundRect(rect, r, r, framePaint)

        // 3) Corner ticks
        val L = dp(22f)
        // TL
        canvas.drawLine(rect.left, rect.top, rect.left + L, rect.top, cornerPaint)
        canvas.drawLine(rect.left, rect.top, rect.left, rect.top + L, cornerPaint)
        // TR
        canvas.drawLine(rect.right - L, rect.top, rect.right, rect.top, cornerPaint)
        canvas.drawLine(rect.right, rect.top, rect.right, rect.top + L, cornerPaint)
        // BL
        canvas.drawLine(rect.left, rect.bottom - L, rect.left, rect.bottom, cornerPaint)
        canvas.drawLine(rect.left, rect.bottom, rect.left + L, rect.bottom, cornerPaint)
        // BR
        canvas.drawLine(rect.right - L, rect.bottom, rect.right, rect.bottom, cornerPaint)
        canvas.drawLine(rect.right, rect.bottom - L, rect.right, rect.bottom, cornerPaint)
    }
}