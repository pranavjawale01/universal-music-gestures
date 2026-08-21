package com.example.universaloffscreenmusic

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.SparseArray
import android.view.MotionEvent
import android.view.View
import java.util.*

class MagicTrailView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paths = SparseArray<Path>()
    private val particles = mutableListOf<SenseParticle>()
    private val random = Random()

    // VFX: Glowing Aura (Large Bloom)
    private val auraPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 60f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
        maskFilter = BlurMaskFilter(50f, BlurMaskFilter.Blur.NORMAL)
    }

    // VFX: Radiant Mid-layer
    private val midPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 25f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
        maskFilter = BlurMaskFilter(15f, BlurMaskFilter.Blur.NORMAL)
    }

    // VFX: Intense White Core
    private val corePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 12f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
        maskFilter = BlurMaskFilter(5f, BlurMaskFilter.Blur.NORMAL)
    }

    // VFX: Sparkle Paint
    private val sparklePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private var masterAlpha = 255
    private var isDisappearing = false

    private val vfxTickRunnable = object : Runnable {
        override fun run() {
            var needsInvalidate = false
            
            if (isDisappearing) {
                masterAlpha -= 15
                if (masterAlpha <= 0) {
                    resetVFX()
                }
                needsInvalidate = true
            }

            if (particles.isNotEmpty()) {
                updateParticles()
                needsInvalidate = true
            }

            if (needsInvalidate) {
                invalidate()
                postDelayed(this, 20)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Draw Main Magical Paths (Multi-layered Bloom)
        for (i in 0 until paths.size()) {
            val path = paths.valueAt(i)
            
            auraPaint.alpha = (masterAlpha * 0.25f).toInt()
            canvas.drawPath(path, auraPaint)
            
            midPaint.alpha = (masterAlpha * 0.5f).toInt()
            canvas.drawPath(path, midPaint)
            
            corePaint.alpha = masterAlpha
            canvas.drawPath(path, corePaint)
        }

        // Draw Magical Sparks
        for (p in particles) {
            sparklePaint.alpha = (p.life * 255).toInt()
            // Randomly flicker the sparkles
            if (random.nextFloat() > 0.1f) {
                canvas.drawCircle(p.x, p.y, p.size, sparklePaint)
            }
        }
    }

    fun handleTouch(event: MotionEvent) {
        val index = event.actionIndex
        val id = event.getPointerId(index)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                removeCallbacks(vfxTickRunnable)
                isDisappearing = false
                masterAlpha = 255
                
                val p = Path()
                p.moveTo(event.getX(index), event.getY(index))
                paths.put(id, p)
                post(vfxTickRunnable)
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val pid = event.getPointerId(i)
                    val px = event.getX(i)
                    val py = event.getY(i)
                    paths.get(pid)?.lineTo(px, py)
                    
                    // Emit sparkles at every segment of the trail
                    emitSenseSparks(px, py)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    isDisappearing = true
                }
            }
            MotionEvent.ACTION_CANCEL -> resetVFX()
        }
        invalidate()
    }

    private fun emitSenseSparks(x: Float, y: Float) {
        // High density sparks for a professional look
        repeat(4) {
            particles.add(SenseParticle(
                x, y,
                (random.nextFloat() - 0.5f) * 12f, // Velocity X
                (random.nextFloat() - 0.5f) * 12f, // Velocity Y
                random.nextFloat() * 7f + 2f      // Size
            ))
        }
        // Limit particle count for performance
        if (particles.size > 200) particles.removeAt(0)
    }

    private fun updateParticles() {
        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.x += p.vx
            p.y += p.vy
            p.vx *= 0.95f // Air resistance
            p.vy *= 0.95f 
            p.life -= 0.04f // Fade out
            if (p.life <= 0) it.remove()
        }
    }

    private fun resetVFX() {
        paths.clear()
        particles.clear()
        masterAlpha = 255
        isDisappearing = false
        invalidate()
    }

    private class SenseParticle(var x: Float, var y: Float, var vx: Float, var vy: Float, var size: Float) {
        var life = 1.0f
    }
}
