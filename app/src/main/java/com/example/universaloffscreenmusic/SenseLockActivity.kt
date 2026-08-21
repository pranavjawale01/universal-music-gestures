package com.example.universaloffscreenmusic

import android.app.KeyguardManager
import android.content.Context
import android.graphics.Color
import android.graphics.PointF
import android.media.audiofx.Visualizer
import android.os.*
import android.util.Log
import android.view.*
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

class SenseLockActivity : AppCompatActivity() {

    private lateinit var rootLayout: FrameLayout
    private lateinit var edgeLightingView: EdgeLightingView
    private lateinit var magicTrailView: MagicTrailView
    private var visualizer: Visualizer? = null
    private var vibrator: Vibrator? = null

    private var isNextEnabled = true
    private var isPrevEnabled = true
    private var isPauseEnabled = true
    private var isEdgeEnabled = true
    private var edgeTheme = "RAINBOW"

    private val touchPoints = mutableListOf<PointF>()
    private var lastTapTime = 0L
    private var lastTapPoint: PointF? = null
    private var touchDownTime = 0L
    private val DOUBLE_TAP_THRESHOLD = 380L

    private val handler = Handler(Looper.getMainLooper())
    private val playbackMonitor = object : Runnable {
        override fun run() {
            if (isEdgeEnabled && ::edgeLightingView.isInitialized) {
                if (UniversalMediaService.isMusicPlaying) {
                    edgeLightingView.startAnimation()
                    startVisualizer()
                } else {
                    edgeLightingView.stopAnimation()
                    stopVisualizer()
                }
            }
            handler.postDelayed(this, 300)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupLockScreenFlags()
        hideSystemUI()

        @Suppress("DEPRECATION")
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        loadPreferences()
        buildViewHierarchy()
        handler.post(playbackMonitor)
    }

    private fun setupLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
    }

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences("gestures_prefs", Context.MODE_PRIVATE)
        isNextEnabled = prefs.getBoolean("gesture_next", true)
        isPrevEnabled = prefs.getBoolean("gesture_prev", true)
        isPauseEnabled = prefs.getBoolean("gesture_pause", true)
        isEdgeEnabled = prefs.getBoolean("edge_lighting_enabled", true)
        edgeTheme = prefs.getString("edge_lighting_theme", "RAINBOW") ?: "RAINBOW"
    }

    private fun buildViewHierarchy() {
        rootLayout = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        edgeLightingView = EdgeLightingView(this).apply {
            setTheme(edgeTheme)
        }
        rootLayout.addView(edgeLightingView)

        magicTrailView = MagicTrailView(this)
        rootLayout.addView(magicTrailView)

        rootLayout.setOnTouchListener { _, event ->
            magicTrailView.handleTouch(event)
            handleTouch(event)
        }

        setContentView(rootLayout)
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownTime = System.currentTimeMillis()
                touchPoints.clear()
                touchPoints.add(PointF(event.x, event.y))
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2 && isPauseEnabled) {
                    triggerHaptic(1)
                    UniversalMediaService.togglePlayPause()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                touchPoints.add(PointF(event.x, event.y))
            }
            MotionEvent.ACTION_UP -> {
                val now = System.currentTimeMillis()
                val duration = now - touchDownTime
                val pt = PointF(event.x, event.y)
                touchPoints.add(pt)

                val minX = touchPoints.minOfOrNull { it.x } ?: event.x
                val maxX = touchPoints.maxOfOrNull { it.x } ?: event.x
                val minY = touchPoints.minOfOrNull { it.y } ?: event.y
                val maxY = touchPoints.maxOfOrNull { it.y } ?: event.y
                val totalMovement = hypot(maxX - minX, maxY - minY)

                // Stationary Tap check (Double Tap to Open Phone)
                if (duration < 300L && totalMovement < 60f) {
                    val prevPoint = lastTapPoint
                    val timeSinceLastTap = now - lastTapTime

                    if (timeSinceLastTap < DOUBLE_TAP_THRESHOLD && prevPoint != null && hypot(pt.x - prevPoint.x, pt.y - prevPoint.y) < 220f) {
                        Log.d("GestureMusic", "Lock Screen Double Tap -> Unlocking phone!")
                        unlockAndDismiss()
                        lastTapTime = 0L
                        lastTapPoint = null
                        return true
                    } else {
                        lastTapTime = now
                        lastTapPoint = pt
                    }
                } else {
                    evaluateShape(touchPoints)
                }
            }
        }
        return true
    }

    private fun evaluateShape(points: List<PointF>) {
        if (points.size < 4) return

        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }

        val width = maxX - minX
        val height = maxY - minY
        val start = points.first()
        val end = points.last()

        if (width < 80f && height < 80f) return

        // 1. Next Track: Right Arrow ( > ) OR Horizontal Swipe Right ( ---> )
        if (isNextEnabled) {
            val isArrowRight = width > 120f && start.x < minX + width * 0.45f && end.x < minX + width * 0.45f && maxX > start.x + 80f
            val isSwipeRight = width > 140f && height < width * 0.75f && start.x < minX + width * 0.35f && end.x > maxX - width * 0.35f

            if (isArrowRight || isSwipeRight) {
                Log.d("GestureMusic", "Next Track Gesture recognized on Lock Screen")
                triggerHaptic(2)
                UniversalMediaService.sendNext()
                return
            }
        }

        // 2. Previous Track: Left Arrow ( < ) OR Horizontal Swipe Left ( <--- )
        if (isPrevEnabled) {
            val isArrowLeft = width > 120f && start.x > maxX - width * 0.45f && end.x > maxX - width * 0.45f && minX < start.x - 80f
            val isSwipeLeft = width > 140f && height < width * 0.75f && start.x > maxX - width * 0.35f && end.x < minX + width * 0.35f

            if (isArrowLeft || isSwipeLeft) {
                Log.d("GestureMusic", "Previous Track Gesture recognized on Lock Screen")
                triggerHaptic(2)
                UniversalMediaService.sendPrevious()
                return
            }
        }

        // 3. Play / Pause: Circle 'O' Gesture
        if (isPauseEnabled) {
            val distStartEnd = hypot(start.x - end.x, start.y - end.y)
            val isCircle = width > 90f && height > 90f && distStartEnd < max(width, height) * 0.50f
            if (isCircle) {
                Log.d("GestureMusic", "Circle Play/Pause Gesture recognized on Lock Screen")
                triggerHaptic(1)
                UniversalMediaService.togglePlayPause()
                return
            }
        }
    }

    private fun unlockAndDismiss() {
        triggerHaptic(2)
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            keyguardManager?.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    finish()
                }

                override fun onDismissError() {
                    finish()
                }

                override fun onDismissCancelled() {
                    finish()
                }
            }) ?: finish()
        } else {
            finish()
        }
    }

    private fun triggerHaptic(type: Int = 1) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (type == 1) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 30, 50, 30), -1))
                }
            } else {
                @Suppress("DEPRECATION")
                if (type == 1) {
                    vibrator?.vibrate(35)
                } else {
                    vibrator?.vibrate(longArrayOf(0, 30, 50, 30), -1)
                }
            }
        } catch (e: Exception) {
            Log.e("GestureMusic", "Vibration failed: ${e.message}")
        }
    }

    private fun startVisualizer() {
        if (visualizer != null) return
        try {
            visualizer = Visualizer(0).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                        waveform?.let {
                            var sum = 0f
                            for (b in it) {
                                sum += abs(b.toInt() - 128).toFloat()
                            }
                            val amp = sum / it.size / 128f
                            if (::edgeLightingView.isInitialized) {
                                edgeLightingView.amplitude = edgeLightingView.amplitude * 0.7f + amp * 0.3f
                            }
                        }
                    }

                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {}
                }, Visualizer.getMaxCaptureRate() / 2, true, false)
                enabled = true
            }
        } catch (e: Exception) {
            Log.e("GestureMusic", "Visualizer failed: ${e.message}")
        }
    }

    private fun stopVisualizer() {
        visualizer?.enabled = false
        visualizer?.release()
        visualizer = null
        if (::edgeLightingView.isInitialized) {
            edgeLightingView.amplitude = 0f
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVisualizer()
        handler.removeCallbacks(playbackMonitor)
    }
}
