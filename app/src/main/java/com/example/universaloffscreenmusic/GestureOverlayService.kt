package com.example.universaloffscreenmusic

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.PointF
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.audiofx.Visualizer
import android.os.*
import android.util.Log
import android.view.*
import android.widget.FrameLayout
import androidx.core.app.NotificationCompat
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

class GestureOverlayService : Service(), SensorEventListener {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var magicTrailView: MagicTrailView
    private lateinit var edgeLightingView: EdgeLightingView
    private var visualizer: Visualizer? = null
    
    private lateinit var powerManager: PowerManager
    private lateinit var sensorManager: SensorManager
    private var proximitySensor: Sensor? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var vibrator: Vibrator? = null

    private var isNextEnabled = true
    private var isPrevEnabled = true
    private var isPauseEnabled = true
    private var controlMode = "LOCK_SCREEN"
    
    private var isEdgeEnabled = true
    private var edgeTheme = "RAINBOW"

    private var isNearPocket = false
    private val touchPoints = mutableListOf<PointF>()
    private var lastTapTime = 0L
    private var lastTapPoint: PointF? = null
    private var touchDownTime = 0L
    private val DOUBLE_TAP_THRESHOLD = 380L
    private var serviceStartTime = 0L
    
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

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Ignore SCREEN_ON within 2 seconds of service creation to avoid self-canceling during wakeup
            if (intent?.action == Intent.ACTION_SCREEN_ON) {
                if (System.currentTimeMillis() - serviceStartTime > 2000L) {
                    Log.d("GestureMusic", "User turned screen on manually, stopping overlay")
                    stopSelf()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        serviceStartTime = System.currentTimeMillis()
        Log.d("GestureMusic", "Overlay Service Created for Off-Screen Gestures")
        
        val prefs = getSharedPreferences("gestures_prefs", Context.MODE_PRIVATE)
        isNextEnabled = prefs.getBoolean("gesture_next", true)
        isPrevEnabled = prefs.getBoolean("gesture_prev", true)
        isPauseEnabled = prefs.getBoolean("gesture_pause", true)
        controlMode = prefs.getString("control_mode", "LOCK_SCREEN") ?: "LOCK_SCREEN"
        isEdgeEnabled = prefs.getBoolean("edge_lighting_enabled", true)
        edgeTheme = prefs.getString("edge_lighting_theme", "RAINBOW") ?: "RAINBOW"

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        powerManager = getSystemService(POWER_SERVICE) as PowerManager
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

        @Suppress("DEPRECATION")
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        proximitySensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        val filter = IntentFilter(Intent.ACTION_SCREEN_ON)
        registerReceiver(screenStateReceiver, filter)

        // Keep CPU and touchscreen digitizer active while screen appears off
        try {
            @Suppress("DEPRECATION")
            wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_DIM_WAKE_LOCK or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP or
                        PowerManager.ON_AFTER_RELEASE,
                "Sense:ScreenOnWake"
            ).apply {
                acquire(15 * 60 * 1000L) // 15 min lock
            }
        } catch (e: Exception) {
            Log.e("GestureMusic", "WakeLock error: ${e.message}")
        }

        startSenseForegroundService()
        createBlackOverlay()
        
        handler.post(playbackMonitor)
    }

    private fun startSenseForegroundService() {
        val channelId = "sense_gesture_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Sense Gestures Active", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Sense Active")
            .setContentText("Off-screen music gestures and double-tap wake active.")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(1, notification)
    }

    private fun createBlackOverlay() {
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        @Suppress("DEPRECATION")
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { 
            // 0.0f ensures AMOLED/OLED displays emit 0 light (true pitch black off appearance)
            screenBrightness = 0.0f
            buttonBrightness = 0.0f
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        
        edgeLightingView = EdgeLightingView(this).apply {
            setTheme(edgeTheme)
        }
        root.addView(edgeLightingView)
        
        magicTrailView = MagicTrailView(this)
        root.addView(magicTrailView)

        overlayView = root
        overlayView.setOnTouchListener { _, event -> 
            magicTrailView.handleTouch(event)
            handleTouch(event) 
        }

        try {
            windowManager.addView(overlayView, params)
        } catch (e: Exception) {
            Log.e("GestureMusic", "Failed to add overlay: ${e.message}")
        }
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        if (isNearPocket) return false

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

                // Check if this was a quick stationary tap (Double-tap to wake up phone)
                if (duration < 300L && totalMovement < 60f) {
                    val prevPoint = lastTapPoint
                    val timeSinceLastTap = now - lastTapTime

                    if (timeSinceLastTap < DOUBLE_TAP_THRESHOLD && prevPoint != null && hypot(pt.x - prevPoint.x, pt.y - prevPoint.y) < 220f) {
                        Log.d("GestureMusic", "Double tap recognized -> Waking up phone!")
                        wakeUpScreen()
                        lastTapTime = 0L
                        lastTapPoint = null
                        return true
                    } else {
                        lastTapTime = now
                        lastTapPoint = pt
                    }
                } else {
                    // It was a gesture stroke
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
                Log.d("GestureMusic", "Next Track Gesture recognized")
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
                Log.d("GestureMusic", "Previous Track Gesture recognized")
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
                Log.d("GestureMusic", "Circle Play/Pause Gesture recognized")
                triggerHaptic(1)
                UniversalMediaService.togglePlayPause()
                return
            }
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

    private fun wakeUpScreen() {
        triggerHaptic(2)
        try {
            @Suppress("DEPRECATION")
            val tempWakeLock = powerManager.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP or
                        PowerManager.ON_AFTER_RELEASE,
                "Sense:WakePhone"
            )
            tempWakeLock.acquire(3000)
            tempWakeLock.release()
        } catch (e: Exception) {
            Log.e("GestureMusic", "Wake error: ${e.message}")
        }
        stopSelf()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_PROXIMITY) {
            isNearPocket = event.values[0] < (proximitySensor?.maximumRange ?: 5f)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        super.onDestroy()
        stopVisualizer()
        handler.removeCallbacks(playbackMonitor)
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        try {
            unregisterReceiver(screenStateReceiver)
        } catch (e: Exception) { }
        sensorManager.unregisterListener(this)
        
        if (::overlayView.isInitialized) {
            try {
                windowManager.removeView(overlayView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

