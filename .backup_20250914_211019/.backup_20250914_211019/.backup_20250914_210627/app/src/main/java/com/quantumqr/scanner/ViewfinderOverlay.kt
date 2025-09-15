package com.quantumqr.scanner

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class ViewfinderOverlay @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val maskPaint = Paint().apply { color = 0x66000000 }
    private val framePaint = Paint().apply {
        isAntiAlias = true
        strokeWidth = 6f
        style = Paint.Style.STROKE
        color = Color.WHITE
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pw = (parent as? View)?.width ?: width
        val ph = (parent as? View)?.height ?: height
        val rect = RectF(
            (pw - width)/2f, (ph - height)/2f,
            (pw + width)/2f, (ph + height)/2f
        )

        // Dim outside
        val dim = Path().apply {
            addRect(0f, 0f, pw.toFloat(), ph.toFloat(), Path.Direction.CW)
            addRoundRect(rect, 20f, 20f, Path.Direction.CCW)
        }
        canvas.drawPath(dim, maskPaint)

        // Corner brackets
        val L = 28f
        canvas.save()
        canvas.translate(rect.left, rect.top)
        val w = rect.width(); val h = rect.height()
        // TL
        canvas.drawLine(0f, 0f, L, 0f, framePaint)
        canvas.drawLine(0f, 0f, 0f, L, framePaint)
        // TR
        canvas.drawLine(w, 0f, w - L, 0f, framePaint)
        canvas.drawLine(w, 0f, w, L, framePaint)
        // BL
        canvas.drawLine(0f, h, L, h, framePaint)
        canvas.drawLine(0f, h, 0f, h - L, framePaint)
        // BR
        canvas.drawLine(w, h, w - L, h, framePaint)
        canvas.drawLine(w, h, w, h - L, framePaint)
        canvas.restore()
    }
}