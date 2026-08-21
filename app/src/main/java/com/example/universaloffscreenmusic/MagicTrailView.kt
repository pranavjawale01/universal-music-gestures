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

    // Layer 1: Atmospheric Super-Glow
    private val auraPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 220f // Massive glow
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
        maskFilter = BlurMaskFilter(110f, BlurMaskFilter.Blur.NORMAL)
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    }

    // Layer 2: Radiant White Energy
    private val midPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 100f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
        maskFilter = BlurMaskFilter(45f, BlurMaskFilter.Blur.NORMAL)
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    }

    // Layer 3: High-Intensity Neon Core
    private val corePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 55f // Ultra-thick core
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
        maskFilter = BlurMaskFilter(18f, BlurMaskFilter.Blur.NORMAL)
    }

    private val sparklePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    }

    private val vfxTickRunnable = object : Runnable {
        override fun run() {
            if (particles.isNotEmpty()) {
                updateParticles()
                invalidate()
                postDelayed(this, 16)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Draw the white magical trails
        for (i in 0 until paths.size()) {
            val path = paths.valueAt(i)
            auraPaint.alpha = 100
            canvas.drawPath(path, auraPaint)
            midPaint.alpha = 180
            canvas.drawPath(path, midPaint)
            corePaint.alpha = 255
            canvas.drawPath(path, corePaint)
        }

        // Draw Magical Sparks
        for (p in particles) {
            sparklePaint.alpha = (p.life * 255).toInt()
            canvas.drawCircle(p.x, p.y, p.size, sparklePaint)
        }
    }

    fun handleTouch(event: MotionEvent) {
        val index = event.actionIndex
        val id = event.getPointerId(index)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
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
                    emitSenseSparks(px, py)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    resetVFX() // Immediate wipe as soon as finger is lifted
                }
            }
            MotionEvent.ACTION_CANCEL -> resetVFX()
        }
        invalidate()
    }

    private fun emitSenseSparks(x: Float, y: Float) {
        repeat(6) {
            particles.add(SenseParticle(
                x, y,
                (random.nextFloat() - 0.5f) * 20f,
                (random.nextFloat() - 0.5f) * 20f,
                random.nextFloat() * 15f + 5f
            ))
        }
        if (particles.size > 400) particles.removeAt(0)
    }

    private fun updateParticles() {
        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.x += p.vx
            p.y += p.vy
            p.vx *= 0.85f 
            p.vy *= 0.85f 
            p.life -= 0.15f
            if (p.life <= 0) it.remove()
        }
    }

    private fun resetVFX() {
        paths.clear()
        particles.clear()
        invalidate()
    }

    private class SenseParticle(var x: Float, var y: Float, var vx: Float, var vy: Float, var size: Float) {
        var life = 1.0f
    }
}
