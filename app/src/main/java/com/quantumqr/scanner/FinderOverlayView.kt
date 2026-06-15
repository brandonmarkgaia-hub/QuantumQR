package com.quantumqr.scanner

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.graphics.toColorInt
import kotlin.math.min
import java.util.Random

class ViewfinderOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private var currentTheme = "NEON"

    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#CC000000".toColorInt()
        style = Paint.Style.FILL
    }
    
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f.dp
    }

    private val laserPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f.dp
    }

    // Matrix Rain properties
    private val matrixPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#00FF41".toColorInt() // Matrix Green
        textSize = 14f.dp
        typeface = Typeface.MONOSPACE
    }
    private val charArray = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray()
    private val random = Random()
    private var columns = IntArray(0)
    private var columnOffsets = FloatArray(0)

    private val path = Path()
    private val box = RectF()
    private var laserY = 0f
    
    private val laserAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2000
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = LinearInterpolator()
        addUpdateListener { animator ->
            val fraction = animator.animatedValue as Float
            laserY = box.top + (box.height() * fraction)
            invalidate()
        }
    }

    // Tunables
    var boxWidthRatio = 0.75f
    var boxHeightRatio = 0.35f
    var cornerRadius = 24f.dp

    init {
        updateThemeColors()
    }

    private fun updateThemeColors() {
        currentTheme = prefs.getString("app_theme", "NEON") ?: "NEON"
        val mainColor = when(currentTheme) {
            "MATRIX" -> "#00FF41".toColorInt()
            "PURPLE" -> "#BC00FF".toColorInt()
            else -> "#00F2FF".toColorInt()
        }
        borderPaint.color = mainColor
        laserPaint.color = mainColor
        laserPaint.setShadowLayer(10f, 0f, 0f, mainColor)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateThemeColors()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val bw = w * boxWidthRatio
        val bh = h * boxHeightRatio
        val left = (w - bw) / 2f
        val top = (h - bh) / 2f
        box.set(left, top, left + bw, top + bh)
        
        cornerRadius = min(cornerRadius, min(box.width(), box.height()) / 2f)
        
        // Setup Matrix Columns
        val columnWidth = matrixPaint.textSize
        val count = (w / columnWidth).toInt() + 1
        columns = IntArray(count) { random.nextInt(h) }
        columnOffsets = FloatArray(count) { it * columnWidth }

        if (!laserAnimator.isRunning) {
            laserAnimator.start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (currentTheme == "MATRIX") {
            drawMatrixRain(canvas)
        }

        // 1) Dim outside
        path.reset()
        path.addRoundRect(box, cornerRadius, cornerRadius, Path.Direction.CW)
        path.fillType = Path.FillType.INVERSE_WINDING
        canvas.drawPath(path, maskPaint)

        // 2) Draw corners
        drawCorners(canvas)

        // 3) Laser line
        canvas.drawLine(box.left + 10f, laserY, box.right - 10f, laserY, laserPaint)
    }

    private fun drawMatrixRain(canvas: Canvas) {
        for (i in columns.indices) {
            val x = columnOffsets[i]
            var y = columns[i].toFloat()
            
            // Draw a string of random characters
            for (j in 0 until 10) {
                val charY = y - (j * matrixPaint.textSize)
                if (charY < 0) continue
                if (charY > height) break
                
                // Only draw IF NOT inside the viewfinder box
                if (!box.contains(x, charY)) {
                    val char = charArray[random.nextInt(charArray.size)]
                    // Fade out the tail
                    matrixPaint.alpha = (255 * (1 - j / 10f)).toInt()
                    canvas.drawText(char.toString(), x, charY, matrixPaint)
                }
            }
            
            // Update position
            columns[i] = (columns[i] + 15) % (height + 200)
        }
        invalidate() // Keep the animation going
    }

    private fun drawCorners(canvas: Canvas) {
        val len = 40f.dp
        
        // Top Left
        canvas.drawLine(box.left, box.top + cornerRadius, box.left, box.top + len, borderPaint)
        canvas.drawLine(box.left + cornerRadius, box.top, box.left + len, box.top, borderPaint)
        canvas.drawArc(box.left, box.top, box.left + cornerRadius * 2, box.top + cornerRadius * 2, 180f, 90f, false, borderPaint)

        // Top Right
        canvas.drawLine(box.right, box.top + cornerRadius, box.right, box.top + len, borderPaint)
        canvas.drawLine(box.right - cornerRadius, box.top, box.right - len, box.top, borderPaint)
        canvas.drawArc(box.right - cornerRadius * 2, box.top, box.right, box.top + cornerRadius * 2, 270f, 90f, false, borderPaint)

        // Bottom Left
        canvas.drawLine(box.left, box.bottom - cornerRadius, box.left, box.bottom - len, borderPaint)
        canvas.drawLine(box.left + cornerRadius, box.bottom, box.left + len, box.bottom, borderPaint)
        canvas.drawArc(box.left, box.bottom - cornerRadius * 2, box.left + cornerRadius * 2, box.bottom, 90f, 90f, false, borderPaint)

        // Bottom Right
        canvas.drawLine(box.right, box.bottom - cornerRadius, box.right, box.bottom - len, borderPaint)
        canvas.drawLine(box.right - cornerRadius, box.bottom, box.right - len, box.bottom, borderPaint)
        canvas.drawArc(box.right - cornerRadius * 2, box.bottom - cornerRadius * 2, box.right, box.bottom, 0f, 90f, false, borderPaint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        laserAnimator.cancel()
    }

    private val Float.dp: Float get() = this * resources.displayMetrics.density
}
