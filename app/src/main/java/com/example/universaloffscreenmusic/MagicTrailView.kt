package com.example.universaloffscreenmusic

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.SparseArray
import android.view.MotionEvent
import android.view.View

class MagicTrailView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paths = SparseArray<Path>()
    private val paint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 15f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
        // Strong magical glow
        maskFilter = BlurMaskFilter(20f, BlurMaskFilter.Blur.NORMAL)
    }

    private var alphaVal = 255
    private var isFading = false

    private val fadeRunnable = object : Runnable {
        override fun run() {
            alphaVal -= 10
            if (alphaVal > 0) {
                invalidate()
                postDelayed(this, 20)
            } else {
                paths.clear()
                alphaVal = 255
                isFading = false
                invalidate()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.alpha = alphaVal
        for (i in 0 until paths.size()) {
            canvas.drawPath(paths.valueAt(i), paint)
        }
    }

    fun handleTouch(event: MotionEvent) {
        val pointerIndex = event.actionIndex
        val pointerId = event.getPointerId(pointerIndex)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                removeCallbacks(fadeRunnable)
                isFading = false
                alphaVal = 255
                
                val p = Path()
                p.moveTo(event.getX(pointerIndex), event.getY(pointerIndex))
                paths.put(pointerId, p)
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    paths.get(id)?.lineTo(event.getX(i), event.getY(i))
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                // If it's the last finger, start fade
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    isFading = true
                    postDelayed(fadeRunnable, 50)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                paths.clear()
                invalidate()
            }
        }
        invalidate()
    }

    fun clearTrail() {
        paths.clear()
        invalidate()
    }
}
