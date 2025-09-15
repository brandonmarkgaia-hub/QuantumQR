package com.quantumqr.scanner

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class ViewfinderOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B3000000") // ~70% black
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f.dp
    }
    private val path = Path()
    private val box = RectF()

    // Tunables
    var boxWidthRatio = 0.72f   // % of view width
    var boxHeightRatio = 0.38f  // % of view height
    var cornerRadius = 18f.dp
    var borderEnabled = true

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val bw = w * boxWidthRatio
        val bh = h * boxHeightRatio
        val left = (w - bw) / 2f
        val top = (h - bh) / 2f
        box.set(left, top, left + bw, top + bh)
        // Cap radius to box
        cornerRadius = min(cornerRadius, min(box.width(), box.height()) / 2f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1) Dim everything outside the rounded rect
        path.reset()
        path.addRoundRect(box, cornerRadius, cornerRadius, Path.Direction.CW)
        path.fillType = Path.FillType.INVERSE_WINDING
        canvas.drawPath(path, maskPaint)

        // 2) Draw a clean white border
        if (borderEnabled) {
            canvas.drawRoundRect(box, cornerRadius, cornerRadius, borderPaint)
        }
    }

    fun setBoxRatios(widthRatio: Float, heightRatio: Float) {
        boxWidthRatio = widthRatio.coerceIn(0.2f, 0.95f)
        boxHeightRatio = heightRatio.coerceIn(0.2f, 0.95f)
        requestLayout(); invalidate()
    }

    private val Float.dp: Float get() = this * resources.displayMetrics.density
}
