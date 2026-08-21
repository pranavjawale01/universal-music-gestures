package com.example.universaloffscreenmusic

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.sin

class EdgeLightingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val wavePaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    }

    private var time = 0f
    var amplitude = 0f // Controlled by Visualizer in GestureOverlayService
    
    // Modern Siri-style colors
    private val colorBlue = Color.parseColor("#4A90E2")
    private val colorCyan = Color.parseColor("#00FFFF")
    private val colorMagenta = Color.parseColor("#FF00FF")
    private val colorIndigo = Color.parseColor("#4B0082")
    private val colorWhite = Color.WHITE

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 3000 // Faster rotation for more energy
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            time = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        visibility = GONE
        // We use hardware acceleration for smooth paths and gradients
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun setTheme(theme: String) {
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

        val w = width.toFloat()
        val h = height.toFloat()

        // Layer 1: Indigo/Blue Base
        drawFullEdgeWave(canvas, colorIndigo, colorBlue, 0.8f, 0.0f, 0.4f, 0.6f)
        
        // Layer 2: Magenta/Purple
        drawFullEdgeWave(canvas, colorMagenta, colorIndigo, 1.1f, 2.0f, 0.5f, 1.0f)
        
        // Layer 3: Cyan/Blue Glow
        drawFullEdgeWave(canvas, colorCyan, colorBlue, 1.4f, 4.0f, 0.6f, 1.3f)
        
        // Layer 4: White/Cyan Bright Highlights
        drawFullEdgeWave(canvas, colorWhite, colorCyan, 1.8f, 1.0f, 0.3f, 1.8f)
    }

    private fun drawFullEdgeWave(canvas: Canvas, colorStart: Int, colorEnd: Int, speed: Float, phase: Float, alpha: Float, freq: Float) {
        val w = width.toFloat()
        val h = height.toFloat()
        
        val segments = 40
        val t = (time * speed + phase) * 2f * Math.PI.toFloat()
        
        // Base height for the wave, boosted by audio amplitude
        val baseHeight = 30f + amplitude * 180f 
        val dynamicFreq = freq + amplitude * 2f

        wavePaint.alpha = (255 * alpha).toInt()
        
        // Horizontal Waves
        drawBorderWave(canvas, w, h, segments, t, dynamicFreq, baseHeight, colorStart, colorEnd, isBottom = true)
        drawBorderWave(canvas, w, h, segments, t + 1.5f, dynamicFreq, baseHeight, colorStart, colorEnd, isTop = true)
        
        // Vertical Waves
        drawBorderWave(canvas, w, h, segments, t + 0.7f, dynamicFreq, baseHeight, colorStart, colorEnd, isLeft = true)
        drawBorderWave(canvas, w, h, segments, t + 2.2f, dynamicFreq, baseHeight, colorStart, colorEnd, isRight = true)
    }

    private fun drawBorderWave(canvas: Canvas, w: Float, h: Float, segments: Int, t: Float, freq: Float, 
                               baseHeight: Float, colorStart: Int, colorEnd: Int,
                               isTop: Boolean = false, isBottom: Boolean = false, isLeft: Boolean = false, isRight: Boolean = false) {
        val path = Path()
        
        if (isBottom) {
            wavePaint.shader = LinearGradient(0f, h - baseHeight, 0f, h, colorStart, colorEnd, Shader.TileMode.CLAMP)
            path.moveTo(0f, h)
            for (i in 0..segments) {
                val x = (i.toFloat() / segments) * w
                val y = h - (baseHeight * 0.3f) + sin(x * 0.01f * freq + t) * baseHeight
                path.lineTo(x, y)
            }
            path.lineTo(w, h)
        } else if (isTop) {
            wavePaint.shader = LinearGradient(0f, 0f, 0f, baseHeight, colorEnd, colorStart, Shader.TileMode.CLAMP)
            path.moveTo(0f, 0f)
            for (i in 0..segments) {
                val x = (i.toFloat() / segments) * w
                val y = (baseHeight * 0.3f) + sin(x * 0.01f * freq + t) * baseHeight
                path.lineTo(x, y)
            }
            path.lineTo(w, 0f)
        } else if (isLeft) {
            wavePaint.shader = LinearGradient(0f, 0f, baseHeight, 0f, colorEnd, colorStart, Shader.TileMode.CLAMP)
            path.moveTo(0f, 0f)
            for (i in 0..segments) {
                val y = (i.toFloat() / segments) * h
                val x = (baseHeight * 0.3f) + sin(y * 0.01f * freq + t) * baseHeight
                path.lineTo(x, y)
            }
            path.lineTo(0f, h)
        } else if (isRight) {
            wavePaint.shader = LinearGradient(w - baseHeight, 0f, w, 0f, colorStart, colorEnd, Shader.TileMode.CLAMP)
            path.moveTo(w, 0f)
            for (i in 0..segments) {
                val y = (i.toFloat() / segments) * h
                val x = w - (baseHeight * 0.3f) + sin(y * 0.01f * freq + t) * baseHeight
                path.lineTo(x, y)
            }
            path.lineTo(w, h)
        }
        
        path.close()
        canvas.drawPath(path, wavePaint)
    }
}
