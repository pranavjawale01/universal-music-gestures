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
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.*
import android.widget.FrameLayout
import androidx.core.app.NotificationCompat
import kotlin.math.abs

class GestureOverlayService : Service(), SensorEventListener {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var magicTrailView: MagicTrailView
    private lateinit var edgeLightingView: EdgeLightingView
    
    private lateinit var powerManager: PowerManager
    private lateinit var sensorManager: SensorManager
    private var proximitySensor: Sensor? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var isNextEnabled = true
    private var isPrevEnabled = true
    private var isPauseEnabled = true
    private var controlMode = "LOCK_SCREEN"
    
    private var isEdgeEnabled = true
    private var edgeTheme = "RAINBOW"

    private var isNearPocket = false
    private val touchPoints = mutableListOf<PointF>()
    private var lastTapTime = 0L
    private val DOUBLE_TAP_THRESHOLD = 300L
    
    private val handler = Handler(Looper.getMainLooper())
    private val playbackMonitor = object : Runnable {
        override fun run() {
            if (isEdgeEnabled) {
                if (UniversalMediaService.isMusicPlaying) {
                    edgeLightingView.startAnimation()
                } else {
                    edgeLightingView.stopAnimation()
                }
            }
            handler.postDelayed(this, 1000)
        }
    }

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_ON) {
                Log.d("GestureMusic", "Screen ON detected, stopping overlay")
                stopSelf()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("GestureMusic", "Overlay Service Created")
        
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

        proximitySensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        val filter = IntentFilter(Intent.ACTION_SCREEN_ON)
        registerReceiver(screenStateReceiver, filter)

        if (controlMode == "LOCK_SCREEN") {
            wakeLock = powerManager.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP or
                        PowerManager.ON_AFTER_RELEASE,
                "Sense:ScreenOnWake"
            ).apply {
                acquire(10 * 60 * 1000L)
            }
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
            .setContentText("Intelligent gestures enabled.")
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
            screenBrightness = 0.01f
            buttonBrightness = 0.0f
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

        windowManager.addView(overlayView, params)
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        if (isNearPocket) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchPoints.clear()
                touchPoints.add(PointF(event.x, event.y))

                val now = System.currentTimeMillis()
                if (now - lastTapTime < DOUBLE_TAP_THRESHOLD) {
                    wakeUpScreen()
                }
                lastTapTime = now
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2 && isPauseEnabled) {
                    UniversalMediaService.togglePlayPause()
                }
            }
            MotionEvent.ACTION_MOVE -> touchPoints.add(PointF(event.x, event.y))
            MotionEvent.ACTION_UP -> {
                evaluateShape(touchPoints)
            }
        }
        return true
    }

    private fun evaluateShape(points: List<PointF>) {
        if (points.size < 5) return

        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }
        
        val width = maxX - minX
        val height = maxY - minY
        val start = points.first()
        val end = points.last()

        if (width < 100f && height < 100f) return

        if (isNextEnabled && width > 150f && start.x < minX + width * 0.4f && end.x < minX + width * 0.4f && maxX > start.x + 100f) {
            UniversalMediaService.sendNext()
            return
        }

        if (isPrevEnabled && width > 150f && start.x > maxX - width * 0.4f && end.x > maxX - width * 0.4f && minX < start.x - 100f) {
            UniversalMediaService.sendPrevious()
            return
        }
    }

    private fun wakeUpScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            val wakeLock = powerManager.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP or
                        PowerManager.ON_AFTER_RELEASE, "Sense:Wake"
            )
            wakeLock.acquire(3000)
            wakeLock.release()
        } else {
            @Suppress("DEPRECATION")
            val wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP, "Sense:Wake"
            )
            wakeLock.acquire(3000)
            wakeLock.release()
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
