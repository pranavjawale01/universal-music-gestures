package com.example.universaloffscreenmusic

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

class EdgeLightingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 20f
        isAntiAlias = true
        maskFilter = BlurMaskFilter(25f, BlurMaskFilter.Blur.NORMAL)
    }

    private var rotationAngle = 0f
    private var themeColors = intArrayOf(Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED)
    
    private val animator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 3000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            rotationAngle = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        visibility = GONE
    }

    fun setTheme(theme: String) {
        themeColors = when (theme) {
            "RAINBOW" -> intArrayOf(Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED)
            "AURORA" -> intArrayOf(Color.CYAN, Color.GREEN, Color.BLUE, Color.CYAN)
            "FIRE" -> intArrayOf(Color.RED, Color.YELLOW, Color.parseColor("#FF6D00"), Color.RED)
            else -> intArrayOf(Color.WHITE, Color.LTGRAY, Color.WHITE)
        }
        invalidate()
    }

    fun startAnimation() {
        visibility = VISIBLE
        if (!animator.isRunning) animator.start()
    }

    fun stopAnimation() {
        visibility = GONE
        animator.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (visibility != VISIBLE) return

        val rect = RectF(10f, 10f, width - 10f, height - 10f)
        
        // Create a sweeping gradient that rotates
        val gradient = SweepGradient(width / 2f, height / 2f, themeColors, null)
        val matrix = Matrix()
        matrix.postRotate(rotationAngle, width / 2f, height / 2f)
        gradient.setLocalMatrix(matrix)
        
        paint.shader = gradient
        canvas.drawRect(rect, paint)
    }
}
