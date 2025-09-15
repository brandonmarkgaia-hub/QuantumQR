package com.quantumqr.scanner.ui

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.min

class OverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class State { IDLE, TRACKING, CONFIRMED }

    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(140, 0, 0, 0) // dim background
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY // neutral grey frame by default
        style = Paint.Style.STROKE
        strokeWidth = 3f * resources.displayMetrics.density
    }
    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY
        style = Paint.Style.STROKE
        strokeWidth = 5f * resources.displayMetrics.density
        strokeCap = Paint.Cap.SQUARE
    }
    private val successPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(90, 0, 200, 0) // green fill on confirm
        style = Paint.Style.FILL
    }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(40, 255, 235, 59) // subtle yellow wash when tracking
        style = Paint.Style.FILL
    }

    private val fullPath = Path().apply { fillType = Path.FillType.EVEN_ODD }
    private val fullRect = RectF()
    private var vfRect = RectF()             // current smoothed rect
    private var targetRect = RectF()         // target from detector
    private var haveTarget = false

    private var successUntil = 0L
    private var lastTrackAt = 0L
    private var state = State.IDLE

    private val cornerLen by lazy { 18f * resources.displayMetrics.density }
    private val cornerRadius by lazy { 14f * resources.displayMetrics.density }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val size = (min(w, h) * 0.70f)
        val left = (w - size) / 2f
        val top = (h - size) / 2f
        vfRect.set(left, top, left + size, top + size)
        targetRect.set(vfRect)
    }

    /** Called from analyzer: update with raw points in preview coordinates. */
    fun setTrackingPoints(points: List<PointF>?) {
        if (points == null || points.isEmpty()) return
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        for (p in points) {
            if (p.x < minX) minX = p.x
            if (p.y < minY) minY = p.y
            if (p.x > maxX) maxX = p.x
            if (p.y > maxY) maxY = p.y
        }
        // Expand a bit and clamp
        val pad = max(12f, min(width, height) * 0.02f)
        minX = (minX - pad).coerceAtLeast(0f); minY = (minY - pad).coerceAtLeast(0f)
        maxX = (maxX + pad).coerceAtMost(width.toFloat()); maxY = (maxY + pad).coerceAtMost(height.toFloat())
        targetRect.set(minX, minY, maxX, maxY)
        haveTarget = true
        lastTrackAt = SystemClock.uptimeMillis()
        if (state != State.CONFIRMED) state = State.TRACKING
        postInvalidateOnAnimation()
    }

    /** Called on successful decode. */
    fun flashConfirmed(durationMillis: Long = 350L) {
        successUntil = SystemClock.uptimeMillis() + durationMillis
        state = State.CONFIRMED
        invalidate(); postInvalidateOnAnimation()
        postDelayed({ invalidate() }, durationMillis)
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)

        // Smooth towards target
        if (haveTarget) {
            val a = 0.25f // smoothing factor
            vfRect.left   = vfRect.left   + (targetRect.left   - vfRect.left)   * a
            vfRect.top    = vfRect.top    + (targetRect.top    - vfRect.top)    * a
            vfRect.right  = vfRect.right  + (targetRect.right  - vfRect.right)  * a
            vfRect.bottom = vfRect.bottom + (targetRect.bottom - vfRect.bottom) * a
        }

        // Reset to IDLE if tracking stale
        val now = SystemClock.uptimeMillis()
        if (state == State.TRACKING && now - lastTrackAt > 400) {
            state = State.IDLE
            haveTarget = false
        }
        if (state == State.CONFIRMED && now > successUntil) {
            state = if (haveTarget) State.TRACKING else State.IDLE
        }

        // Scrim with cutout
        fullPath.reset()
        fullRect.set(0f, 0f, width.toFloat(), height.toFloat())
        fullPath.addRect(fullRect, Path.Direction.CW)
        fullPath.addRoundRect(vfRect, cornerRadius, cornerRadius, Path.Direction.CW)
        c.drawPath(fullPath, scrimPaint)

        // State fills
        when (state) {
            State.TRACKING -> c.drawRoundRect(vfRect, cornerRadius, cornerRadius, trackPaint)
            State.CONFIRMED -> c.drawRoundRect(vfRect, cornerRadius, cornerRadius, successPaint)
            else -> {}
        }

        // Border + corners (grey/yellow/green)
        val cornerColor = when (state) {
            State.TRACKING -> Color.rgb(255, 213, 0) // amber/yellow
            State.CONFIRMED -> Color.rgb(0, 200, 0)
            else -> Color.LTGRAY
        }
        borderPaint.color = cornerColor
        cornerPaint.color = cornerColor

        c.drawRoundRect(vfRect, cornerRadius, cornerRadius, borderPaint)

        val l = vfRect.left; val t = vfRect.top; val r = vfRect.right; val b = vfRect.bottom
        val cl = cornerLen
        // TL
        c.drawLine(l, t, l + cl, t, cornerPaint); c.drawLine(l, t, l, t + cl, cornerPaint)
        // TR
        c.drawLine(r, t, r - cl, t, cornerPaint); c.drawLine(r, t, r, t + cl, cornerPaint)
        // BL
        c.drawLine(l, b, l + cl, b, cornerPaint); c.drawLine(l, b, l, b - cl, cornerPaint)
        // BR
        c.drawLine(r, b, r - cl, b, cornerPaint); c.drawLine(r, b, r, b - cl, cornerPaint)
    }
}