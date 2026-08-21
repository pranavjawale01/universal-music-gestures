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

    // VFX Layer 1: Massive Atmospheric Glow
    private val atmospherePaint = Paint().apply {
        style = Paint.Style.STROKE
        isAntiAlias = true
        maskFilter = BlurMaskFilter(60f, BlurMaskFilter.Blur.NORMAL)
    }

    // VFX Layer 2: Radiant Bloom
    private val bloomPaint = Paint().apply {
        style = Paint.Style.STROKE
        isAntiAlias = true
        maskFilter = BlurMaskFilter(25f, BlurMaskFilter.Blur.NORMAL)
    }

    // VFX Layer 3: Sharp High-Intensity Core
    private val corePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 10f
        isAntiAlias = true
    }

    private var rotationOffset = 0f
    private var breathFactor = 0f
    private var colors = intArrayOf(Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED)
    
    private val vfxAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 3000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            val progress = it.animatedValue as Float
            rotationOffset = progress * 360f
            breathFactor = sin(progress * Math.PI.toFloat() * 2f) * 0.5f + 0.5f
            invalidate()
        }
    }

    init {
        visibility = GONE
        setLayerType(LAYER_TYPE_SOFTWARE, null) // Required for BlurMaskFilter
    }

    fun setTheme(theme: String) {
        colors = when (theme) {
            "RAINBOW" -> intArrayOf(
                Color.parseColor("#FF0000"), // Intense Red
                Color.parseColor("#FFEA00"), // Intense Yellow
                Color.parseColor("#00E676"), // Neon Green
                Color.parseColor("#00E5FF"), // Neon Cyan
                Color.parseColor("#2979FF"), // Electric Blue
                Color.parseColor("#D500F9"), // Vivid Magenta
                Color.parseColor("#FF0000")
            )
            "AURORA" -> intArrayOf(
                Color.parseColor("#00E5FF"), // Arctic Cyan
                Color.parseColor("#1DE9B6"), // Spirit Teal
                Color.parseColor("#00E676"), // Aurora Green
                Color.parseColor("#651FFF"), // Deep Purple
                Color.parseColor("#00E5FF")
            )
            "FIRE" -> intArrayOf(
                Color.parseColor("#FF3D00"), // Solar Orange
                Color.parseColor("#FFEA00"), // Sun Yellow
                Color.parseColor("#D50000"), // Magma Red
                Color.parseColor("#FF3D00")
            )
            else -> intArrayOf(Color.WHITE, Color.LTGRAY, Color.WHITE)
        }
        invalidate()
    }

    fun startAnimation() {
        visibility = VISIBLE
        if (!vfxAnimator.isRunning) vfxAnimator.start()
    }

    fun stopAnimation() {
        visibility = GONE
        vfxAnimator.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (visibility != VISIBLE) return

        val w = width.toFloat()
        val h = height.toFloat()
        
        // Accurate Bezel Path with high-end rounded corners
        val radius = 130f
        val displayPath = Path().apply {
            addRoundRect(0f, 0f, w, h, radius, radius, Path.Direction.CW)
        }

        // Liquid Shader Logic
        val shader = SweepGradient(w / 2f, h / 2f, colors, null)
        val matrix = Matrix()
        matrix.postRotate(rotationOffset, w / 2f, h / 2f)
        shader.setLocalMatrix(matrix)

        // Render Layer 1: Atmospheric Glow (Pulses slightly)
        atmospherePaint.shader = shader
        atmospherePaint.strokeWidth = 80f + (20f * breathFactor)
        atmospherePaint.alpha = (80 + (40 * breathFactor)).toInt()
        canvas.drawPath(displayPath, atmospherePaint)

        // Render Layer 2: Radiant Bloom
        bloomPaint.shader = shader
        bloomPaint.strokeWidth = 35f
        bloomPaint.alpha = 200
        canvas.drawPath(displayPath, bloomPaint)

        // Render Layer 3: High-Intensity Core
        corePaint.shader = shader
        corePaint.alpha = 255
        canvas.drawPath(displayPath, corePaint)
    }
}
