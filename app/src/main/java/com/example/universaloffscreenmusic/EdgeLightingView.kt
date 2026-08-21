package com.example.universaloffscreenmusic

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.sin

class EdgeLightingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Layer Paints
    private val bloomPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    }

    private val ribbonPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    }

    private val filamentOuterPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    }

    private val filamentMidPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    }

    private val filamentCorePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
        color = Color.WHITE
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    }

    private val flarePaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    }

    private var currentTheme = "RAINBOW"
    private var time = 0f
    var amplitude = 0f

    // Apple Intelligence / Siri Radiant Spectrum
    private val paletteSiri = intArrayOf(
        Color.parseColor("#FF1493"), // Hot Neon Magenta
        Color.parseColor("#8A2BE2"), // Electric Purple / Violet
        Color.parseColor("#4D00E6"), // Deep Indigo
        Color.parseColor("#0066FF"), // Electric Azure Blue
        Color.parseColor("#00F0FF"), // Bright Radiant Cyan
        Color.parseColor("#00FFA3"), // Neon Mint
        Color.parseColor("#10E778"), // Vivid Emerald Green
        Color.parseColor("#FF6F00"), // Warm Solar Amber
        Color.parseColor("#FF1493")  // Seamless loop back to Magenta
    )

    // Nordic Aurora Spectrum
    private val paletteAurora = intArrayOf(
        Color.parseColor("#0A1128"), // Midnight Blue
        Color.parseColor("#1D4ED8"), // Royal Cobalt
        Color.parseColor("#00F5D4"), // Electric Cyan
        Color.parseColor("#00FF87"), // Neon Emerald
        Color.parseColor("#70FFD0"), // Ice Mint
        Color.parseColor("#9B5DE5"), // Arctic Violet
        Color.parseColor("#00F5D4"), // Radiant Cyan
        Color.parseColor("#0A1128")  // Seamless loop
    )

    // Solar Fire Spectrum
    private val paletteFire = intArrayOf(
        Color.parseColor("#590D22"), // Deep Maroon
        Color.parseColor("#FF0054"), // Neon Crimson
        Color.parseColor("#FF5400"), // Radiant Flame Orange
        Color.parseColor("#FF9E00"), // Amber Gold
        Color.parseColor("#FFEA00"), // Electric Solar Yellow
        Color.parseColor("#FF5400"), // Fiery Orange
        Color.parseColor("#590D22")  // Seamless loop
    )

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 3200
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            time = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        visibility = GONE
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun setTheme(theme: String) {
        currentTheme = theme.uppercase()
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

    private fun getActivePalette(): IntArray {
        return when (currentTheme) {
            "AURORA" -> paletteAurora
            "FIRE" -> paletteFire
            else -> paletteSiri
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (visibility != VISIBLE) return

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val palette = getActivePalette()
        val amp = amplitude.coerceIn(0f, 1f)

        // Dynamic sizes reacting to music beat
        val bloomSize = 75f + amp * 110f
        val ribbonBaseHeight = 45f + amp * 105f

        // Layer 0: Wide Ambient Bloom (soft glowing background aura)
        drawAmbientBloom(canvas, w, h, bloomSize, palette, amp)

        // Layer 1: Deep Volumetric Wave Ribbons (Harmonic Layer A)
        drawEdgeWaveLayer(
            canvas = canvas,
            w = w,
            h = h,
            palette = palette,
            baseHeight = ribbonBaseHeight * 1.25f,
            speed = 0.75f,
            phaseOffset = 0.0f,
            freqMult = 0.9f,
            alpha = 0.55f + amp * 0.25f,
            drawFilament = false,
            amp = amp
        )

        // Layer 2: Radiant Mid-Wave Ribbons (Harmonic Layer B with glowing filaments)
        drawEdgeWaveLayer(
            canvas = canvas,
            w = w,
            h = h,
            palette = palette,
            baseHeight = ribbonBaseHeight * 0.85f,
            speed = 1.2f,
            phaseOffset = 2.2f,
            freqMult = 1.35f,
            alpha = 0.70f + amp * 0.25f,
            drawFilament = true,
            amp = amp
        )

        // Layer 3: High-Frequency Shimmer Ribbons (Harmonic Layer C with ultra-bright core)
        drawEdgeWaveLayer(
            canvas = canvas,
            w = w,
            h = h,
            palette = palette,
            baseHeight = ribbonBaseHeight * 0.60f,
            speed = 1.7f,
            phaseOffset = 4.4f,
            freqMult = 1.85f,
            alpha = 0.85f + amp * 0.15f,
            drawFilament = true,
            amp = amp
        )

        // Layer 4: Luminous Corner Flares (Smooth Bezel Transitions)
        drawCornerFlares(canvas, w, h, palette, bloomSize, amp)
    }

    private fun drawAmbientBloom(canvas: Canvas, w: Float, h: Float, bloomSize: Float, palette: IntArray, amp: Float) {
        val baseAlpha = (0.45f + amp * 0.35f).coerceIn(0f, 1f)

        // Top Bloom
        val topColor = samplePalette(palette, (time + 0.125f) % 1f)
        bloomPaint.shader = LinearGradient(
            0f, 0f, 0f, bloomSize,
            intArrayOf(adjustAlpha(topColor, baseAlpha * 0.8f), adjustAlpha(topColor, baseAlpha * 0.3f), Color.TRANSPARENT),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, bloomSize, bloomPaint)

        // Bottom Bloom
        val bottomColor = samplePalette(palette, (time + 0.625f) % 1f)
        bloomPaint.shader = LinearGradient(
            0f, h, 0f, h - bloomSize,
            intArrayOf(adjustAlpha(bottomColor, baseAlpha * 0.8f), adjustAlpha(bottomColor, baseAlpha * 0.3f), Color.TRANSPARENT),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, h - bloomSize, w, h, bloomPaint)

        // Left Bloom
        val leftColor = samplePalette(palette, (time + 0.875f) % 1f)
        bloomPaint.shader = LinearGradient(
            0f, 0f, bloomSize, 0f,
            intArrayOf(adjustAlpha(leftColor, baseAlpha * 0.8f), adjustAlpha(leftColor, baseAlpha * 0.3f), Color.TRANSPARENT),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, bloomSize, h, bloomPaint)

        // Right Bloom
        val rightColor = samplePalette(palette, (time + 0.375f) % 1f)
        bloomPaint.shader = LinearGradient(
            w, 0f, w - bloomSize, 0f,
            intArrayOf(adjustAlpha(rightColor, baseAlpha * 0.8f), adjustAlpha(rightColor, baseAlpha * 0.3f), Color.TRANSPARENT),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(w - bloomSize, 0f, w, h, bloomPaint)
    }

    private fun drawEdgeWaveLayer(
        canvas: Canvas,
        w: Float,
        h: Float,
        palette: IntArray,
        baseHeight: Float,
        speed: Float,
        phaseOffset: Float,
        freqMult: Float,
        alpha: Float,
        drawFilament: Boolean,
        amp: Float
    ) {
        val segments = 55
        val dynamicFreq = freqMult + amp * 0.5f

        // Top Border (Perimeter progress: 0.0 -> 0.25)
        drawSingleBorder(
            canvas = canvas,
            palette = palette,
            length = w,
            baseHeight = baseHeight,
            segments = segments,
            timeVal = (time * speed + phaseOffset) * 2f * PI.toFloat(),
            freq = dynamicFreq,
            startProgress = 0.0f,
            endProgress = 0.25f,
            alpha = alpha,
            drawFilament = drawFilament,
            amp = amp,
            edgeType = EdgeType.TOP,
            w = w,
            h = h
        )

        // Right Border (Perimeter progress: 0.25 -> 0.50)
        drawSingleBorder(
            canvas = canvas,
            palette = palette,
            length = h,
            baseHeight = baseHeight,
            segments = segments,
            timeVal = (time * speed + phaseOffset + 1.2f) * 2f * PI.toFloat(),
            freq = dynamicFreq,
            startProgress = 0.25f,
            endProgress = 0.50f,
            alpha = alpha,
            drawFilament = drawFilament,
            amp = amp,
            edgeType = EdgeType.RIGHT,
            w = w,
            h = h
        )

        // Bottom Border (Perimeter progress: 0.50 -> 0.75)
        drawSingleBorder(
            canvas = canvas,
            palette = palette,
            length = w,
            baseHeight = baseHeight,
            segments = segments,
            timeVal = (time * speed + phaseOffset + 2.5f) * 2f * PI.toFloat(),
            freq = dynamicFreq,
            startProgress = 0.50f,
            endProgress = 0.75f,
            alpha = alpha,
            drawFilament = drawFilament,
            amp = amp,
            edgeType = EdgeType.BOTTOM,
            w = w,
            h = h
        )

        // Left Border (Perimeter progress: 0.75 -> 1.00)
        drawSingleBorder(
            canvas = canvas,
            palette = palette,
            length = h,
            baseHeight = baseHeight,
            segments = segments,
            timeVal = (time * speed + phaseOffset + 3.7f) * 2f * PI.toFloat(),
            freq = dynamicFreq,
            startProgress = 0.75f,
            endProgress = 1.00f,
            alpha = alpha,
            drawFilament = drawFilament,
            amp = amp,
            edgeType = EdgeType.LEFT,
            w = w,
            h = h
        )
    }

    private enum class EdgeType { TOP, BOTTOM, LEFT, RIGHT }

    private fun drawSingleBorder(
        canvas: Canvas,
        palette: IntArray,
        length: Float,
        baseHeight: Float,
        segments: Int,
        timeVal: Float,
        freq: Float,
        startProgress: Float,
        endProgress: Float,
        alpha: Float,
        drawFilament: Boolean,
        amp: Float,
        edgeType: EdgeType,
        w: Float,
        h: Float
    ) {
        val ribbonPath = Path()
        val filamentPath = Path()

        val cStart = samplePalette(palette, (time + startProgress) % 1f)
        val cMid = samplePalette(palette, (time + (startProgress + endProgress) * 0.5f) % 1f)
        val cEnd = samplePalette(palette, (time + endProgress) % 1f)

        // Configure LinearGradient along ribbon
        when (edgeType) {
            EdgeType.TOP -> {
                ribbonPaint.shader = LinearGradient(
                    0f, 0f, 0f, baseHeight * 1.2f,
                    intArrayOf(adjustAlpha(cMid, alpha * 0.95f), adjustAlpha(cMid, alpha * 0.45f), Color.TRANSPARENT),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP
                )
                ribbonPath.moveTo(0f, 0f)
            }
            EdgeType.BOTTOM -> {
                ribbonPaint.shader = LinearGradient(
                    0f, h, 0f, h - baseHeight * 1.2f,
                    intArrayOf(adjustAlpha(cMid, alpha * 0.95f), adjustAlpha(cMid, alpha * 0.45f), Color.TRANSPARENT),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP
                )
                ribbonPath.moveTo(0f, h)
            }
            EdgeType.LEFT -> {
                ribbonPaint.shader = LinearGradient(
                    0f, 0f, baseHeight * 1.2f, 0f,
                    intArrayOf(adjustAlpha(cMid, alpha * 0.95f), adjustAlpha(cMid, alpha * 0.45f), Color.TRANSPARENT),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP
                )
                ribbonPath.moveTo(0f, 0f)
            }
            EdgeType.RIGHT -> {
                ribbonPaint.shader = LinearGradient(
                    w, 0f, w - baseHeight * 1.2f, 0f,
                    intArrayOf(adjustAlpha(cMid, alpha * 0.95f), adjustAlpha(cMid, alpha * 0.45f), Color.TRANSPARENT),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP
                )
                ribbonPath.moveTo(w, 0f)
            }
        }

        // Multi-harmonic wave calculation
        for (i in 0..segments) {
            val s = (i.toFloat() / segments) * length
            val normS = i.toFloat() / segments

            val harmonic = (
                sin(normS * (3.5f * freq) * PI.toFloat() + timeVal) * 0.60f +
                sin(normS * (6.5f * freq) * PI.toFloat() - timeVal * 0.75f + 1.3f) * 0.30f +
                sin(normS * (10.0f * freq) * PI.toFloat() + timeVal * 1.4f + 2.6f) * 0.10f
            )
            // Normalized offset between 0.20 and 1.0 of baseHeight
            val waveOffset = (0.35f + 0.65f * (harmonic + 1f) * 0.5f) * baseHeight

            val px: Float
            val py: Float

            when (edgeType) {
                EdgeType.TOP -> {
                    px = s
                    py = waveOffset
                }
                EdgeType.BOTTOM -> {
                    px = s
                    py = h - waveOffset
                }
                EdgeType.LEFT -> {
                    px = waveOffset
                    py = s
                }
                EdgeType.RIGHT -> {
                    px = w - waveOffset
                    py = s
                }
            }

            if (i == 0) {
                ribbonPath.lineTo(px, py)
                filamentPath.moveTo(px, py)
            } else {
                ribbonPath.lineTo(px, py)
                filamentPath.lineTo(px, py)
            }
        }

        // Close ribbon path back to edge
        when (edgeType) {
            EdgeType.TOP -> {
                ribbonPath.lineTo(w, 0f)
                ribbonPath.close()
            }
            EdgeType.BOTTOM -> {
                ribbonPath.lineTo(w, h)
                ribbonPath.close()
            }
            EdgeType.LEFT -> {
                ribbonPath.lineTo(0f, h)
                ribbonPath.close()
            }
            EdgeType.RIGHT -> {
                ribbonPath.lineTo(w, h)
                ribbonPath.close()
            }
        }

        // Draw Ribbon
        canvas.drawPath(ribbonPath, ribbonPaint)

        // Draw Radiant Filaments (Apple Intelligence glowing light strands)
        if (drawFilament) {
            // Pass 1: Wide Colored Outer Filament Glow
            filamentOuterPaint.strokeWidth = 14f + amp * 8f
            filamentOuterPaint.color = adjustAlpha(cMid, (alpha * 0.45f).coerceIn(0f, 1f))
            canvas.drawPath(filamentPath, filamentOuterPaint)

            // Pass 2: Vivid Colored Middle Filament
            filamentMidPaint.strokeWidth = 6.5f + amp * 4f
            filamentMidPaint.color = adjustAlpha(cMid, (alpha * 0.85f).coerceIn(0f, 1f))
            canvas.drawPath(filamentPath, filamentMidPaint)

            // Pass 3: Ultra-Luminous Hot White Specular Core
            filamentCorePaint.strokeWidth = 2.5f + amp * 2.5f
            filamentCorePaint.color = adjustAlpha(Color.WHITE, (0.92f + amp * 0.08f).coerceIn(0f, 1f))
            canvas.drawPath(filamentPath, filamentCorePaint)
        }
    }

    private fun drawCornerFlares(canvas: Canvas, w: Float, h: Float, palette: IntArray, bloomSize: Float, amp: Float) {
        val flareRadius = bloomSize * 1.15f
        val flareAlpha = (0.55f + amp * 0.35f).coerceIn(0f, 1f)

        // Top-Left (Progress 0.0)
        val cTL = samplePalette(palette, time % 1f)
        drawCornerFlare(canvas, 0f, 0f, flareRadius, cTL, flareAlpha)

        // Top-Right (Progress 0.25)
        val cTR = samplePalette(palette, (time + 0.25f) % 1f)
        drawCornerFlare(canvas, w, 0f, flareRadius, cTR, flareAlpha)

        // Bottom-Right (Progress 0.50)
        val cBR = samplePalette(palette, (time + 0.50f) % 1f)
        drawCornerFlare(canvas, w, h, flareRadius, cBR, flareAlpha)

        // Bottom-Left (Progress 0.75)
        val cBL = samplePalette(palette, (time + 0.75f) % 1f)
        drawCornerFlare(canvas, 0f, h, flareRadius, cBL, flareAlpha)
    }

    private fun drawCornerFlare(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int, alpha: Float) {
        flarePaint.shader = RadialGradient(
            cx, cy, radius,
            intArrayOf(
                adjustAlpha(Color.WHITE, alpha * 0.85f),
                adjustAlpha(color, alpha * 0.65f),
                adjustAlpha(color, alpha * 0.20f),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.3f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius, flarePaint)
    }

    private fun samplePalette(palette: IntArray, progress: Float): Int {
        val p = ((progress % 1f) + 1f) % 1f
        val n = palette.size - 1
        val scaled = p * n
        val idx = scaled.toInt().coerceIn(0, n - 1)
        val frac = scaled - idx
        return blendColors(palette[idx], palette[idx + 1], frac)
    }

    private fun blendColors(c1: Int, c2: Int, f: Float): Int {
        val a = (Color.alpha(c1) + (Color.alpha(c2) - Color.alpha(c1)) * f).toInt()
        val r = (Color.red(c1) + (Color.red(c2) - Color.red(c1)) * f).toInt()
        val g = (Color.green(c1) + (Color.green(c2) - Color.green(c1)) * f).toInt()
        val b = (Color.blue(c1) + (Color.blue(c2) - Color.blue(c1)) * f).toInt()
        return Color.argb(a, r, g, b)
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = (Color.alpha(color) * factor.coerceIn(0f, 1f)).toInt().coerceIn(0, 255)
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }
}

