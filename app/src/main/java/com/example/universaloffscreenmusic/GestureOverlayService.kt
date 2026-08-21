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
import android.media.AudioManager
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
    
    private lateinit var powerManager: PowerManager
    private lateinit var sensorManager: SensorManager
    private var proximitySensor: Sensor? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var vibrator: Vibrator? = null

    private var isNextEnabled = true
    private var isPrevEnabled = true
    private var isPauseEnabled = true
    private var isVolUpEnabled = true
    private var isVolDownEnabled = true
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
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager != null && (audioManager.mode == AudioManager.MODE_IN_CALL ||
                            audioManager.mode == AudioManager.MODE_RINGTONE ||
                            audioManager.mode == AudioManager.MODE_IN_COMMUNICATION)) {
                Log.d("GestureMusic", "Phone call / ringtone active -> Stopping overlay")
                stopSelf()
                return
            }

            if (isEdgeEnabled && ::edgeLightingView.isInitialized) {
                if (UniversalMediaService.isMusicPlaying) {
                    edgeLightingView.startAnimation()
                } else {
                    edgeLightingView.stopAnimation()
                }
            }
            handler.postDelayed(this, 300) 
        }
    }

    private val systemStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            Log.d("GestureMusic", "Overlay received system event: $action -> Exiting gesture overlay")
            stopSelf()
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
        isVolUpEnabled = prefs.getBoolean("gesture_vol_up", true)
        isVolDownEnabled = prefs.getBoolean("gesture_vol_down", true)
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

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            @Suppress("DEPRECATION")
            addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
            addAction(android.telephony.TelephonyManager.ACTION_PHONE_STATE_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_HEADSET_PLUG)
        }
        registerReceiver(systemStateReceiver, filter)

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
            .setContentText("Off-screen music gestures active.")
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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { 
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            buttonBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
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

    private fun calculateSignedArea(points: List<PointF>): Float {
        var area = 0f
        for (i in 0 until points.size - 1) {
            area += (points[i].x * points[i + 1].y) - (points[i + 1].x * points[i].y)
        }
        area += (points.last().x * points.first().y) - (points.first().x * points.last().y)
        return area / 2f
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

        val screenH = resources.displayMetrics.heightPixels.toFloat()

        // Swipe Down from Top (Pull down notification bar / quick settings)
        if (start.y < screenH * 0.22f && (end.y - start.y) > 120f && height > width) {
            Log.d("GestureMusic", "Top swipe down -> Exiting overlay to reveal notification shade")
            stopSelf()
            return
        }

        // Swipe Up from Bottom (Home navigation / exit app)
        if (start.y > screenH * 0.78f && (start.y - end.y) > 120f && height > width) {
            Log.d("GestureMusic", "Bottom swipe up -> Exiting overlay to reveal home/system")
            stopSelf()
            return
        }

        if (width < 60f && height < 60f) return

        // 1. Next Track: Right Arrow ( > ) OR Horizontal Swipe Right ( ---> )
        if (isNextEnabled) {
            val isArrowRight = width > 80f && start.x < minX + width * 0.5f && end.x < minX + width * 0.5f && maxX > minX + 60f
            val isSwipeRight = width > 90f && height < width * 0.9f && (end.x - start.x) > 70f

            if (isArrowRight || isSwipeRight) {
                Log.d("GestureMusic", "Next Track Gesture recognized")
                triggerHaptic(2)
                UniversalMediaService.sendNext()
                return
            }
        }

        // 2. Previous Track: Left Arrow ( < ) OR Horizontal Swipe Left ( <--- )
        if (isPrevEnabled) {
            val isArrowLeft = width > 80f && start.x > maxX - width * 0.5f && end.x > maxX - width * 0.5f && minX < maxX - 60f
            val isSwipeLeft = width > 90f && height < width * 0.9f && (start.x - end.x) > 70f

            if (isArrowLeft || isSwipeLeft) {
                Log.d("GestureMusic", "Previous Track Gesture recognized")
                triggerHaptic(2)
                UniversalMediaService.sendPrevious()
                return
            }
        }

        // 3. Circle Gesture: Clockwise (Volume UP) / Anticlockwise (Volume DOWN)
        val distStartEnd = hypot(start.x - end.x, start.y - end.y)
        val isCircle = width > 60f && height > 60f && distStartEnd < max(width, height) * 0.65f
        if (isCircle) {
            val signedArea = calculateSignedArea(points)
            if (signedArea > 0f && isVolUpEnabled) {
                Log.d("GestureMusic", "Clockwise Circle (Volume UP +10%) recognized")
                triggerHaptic(1)
                UniversalMediaService.adjustVolume(isIncrease = true, this)
                return
            } else if (signedArea < 0f && isVolDownEnabled) {
                Log.d("GestureMusic", "Anticlockwise Circle (Volume DOWN -10%) recognized")
                triggerHaptic(1)
                UniversalMediaService.adjustVolume(isIncrease = false, this)
                return
            } else if (isPauseEnabled) {
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
        handler.removeCallbacks(playbackMonitor)
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        try {
            unregisterReceiver(systemStateReceiver)
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

