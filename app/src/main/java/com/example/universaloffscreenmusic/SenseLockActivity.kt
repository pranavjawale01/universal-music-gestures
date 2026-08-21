package com.example.universaloffscreenmusic

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PointF
import android.media.AudioManager
import android.os.*
import android.telephony.TelephonyManager
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
    private var vibrator: Vibrator? = null

    private var isNextEnabled = true
    private var isPrevEnabled = true
    private var isPauseEnabled = true
    private var isVolUpEnabled = true
    private var isVolDownEnabled = true
    private var isEdgeEnabled = true
    private var edgeTheme = "RAINBOW"
    private var isTestMode = false

    private val touchPoints = mutableListOf<PointF>()
    private var lastTapTime = 0L
    private var lastTapPoint: PointF? = null
    private var touchDownTime = 0L
    private val DOUBLE_TAP_THRESHOLD = 380L

    private var createTime = 0L

    companion object {
        var isSenseActive = false
        var lastUserExitTime = 0L
    }

    private val handler = Handler(Looper.getMainLooper())
    private val playbackMonitor = object : Runnable {
        override fun run() {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val isAudioActive = UniversalMediaService.isMusicPlaying || (audioManager?.isMusicActive == true)

            // Strictly exit if incoming phone call or active communication
            if (audioManager != null && (audioManager.mode == AudioManager.MODE_IN_CALL ||
                            audioManager.mode == AudioManager.MODE_RINGTONE ||
                            audioManager.mode == AudioManager.MODE_IN_COMMUNICATION)) {
                Log.d("GestureMusic", "Phone call / ringtone active -> Strictly exiting gesture mode")
                finish()
                return
            }

            // If music stopped playing and not in test mode, exit gesture mode to normal lock screen
            if (!isTestMode && !isAudioActive) {
                Log.d("GestureMusic", "Music is not active -> Exiting Sense lock screen")
                finish()
                return
            }

            if (isEdgeEnabled && ::edgeLightingView.isInitialized) {
                edgeLightingView.startAnimation()
            }
            handler.postDelayed(this, 500)
        }
    }

    private val systemEventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            if (action == Intent.ACTION_USER_PRESENT ||
                action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
                Log.d("GestureMusic", "System event: $action -> Exiting gesture mode")
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createTime = System.currentTimeMillis()
        isSenseActive = true
        isTestMode = intent.getBooleanExtra("is_test_mode", false)
        setupLockScreenFlags()
        hideSystemUI()

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
        }
        registerReceiver(systemEventReceiver, filter)

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

    override fun onResume() {
        super.onResume()
        isSenseActive = true
        if (isEdgeEnabled && ::edgeLightingView.isInitialized) {
            edgeLightingView.startAnimation()
        }
    }

    override fun onPause() {
        super.onPause()
        isSenseActive = false
        lastUserExitTime = System.currentTimeMillis()
    }

    override fun onDestroy() {
        super.onDestroy()
        isSenseActive = false
        lastUserExitTime = System.currentTimeMillis()
        try {
            unregisterReceiver(systemEventReceiver)
        } catch (e: Exception) {
            // Ignored
        }
        handler.removeCallbacksAndMessages(null)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        finish()
    }

    private fun setupLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
    }

    private fun showConfirmationGlow() {
        if (::edgeLightingView.isInitialized) {
            edgeLightingView.startAnimation()
            if (!isEdgeEnabled && !isTestMode) {
                handler.postDelayed({
                    if (!isEdgeEnabled && !isTestMode) {
                        edgeLightingView.stopAnimation()
                    }
                }, 800L)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_POWER || keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            finish()
            return super.onKeyDown(keyCode, event)
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            finish()
        }
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
        isVolUpEnabled = prefs.getBoolean("gesture_vol_up", true)
        isVolDownEnabled = prefs.getBoolean("gesture_vol_down", true)
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
        if (isEdgeEnabled) {
            edgeLightingView.startAnimation()
        }

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
                    showConfirmationGlow()
                    UniversalMediaService.togglePlayPause()
                    return true
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

                // Any tap / stationary touch on lock screen -> Strictly exit gesture mode
                if (duration < 400L && totalMovement < 70f) {
                    Log.d("GestureMusic", "Tap detected on lock screen -> Strictly exiting gesture mode")
                    unlockAndDismiss()
                    return true
                }

                // If it's a gesture stroke, evaluate shape
                val gestureRecognized = evaluateShape(touchPoints)
                if (!gestureRecognized) {
                    // Any other swipe or touch -> Strictly exit gesture mode
                    Log.d("GestureMusic", "Unrecognized touch/action -> Strictly exiting gesture mode")
                    finish()
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

    private fun evaluateShape(points: List<PointF>): Boolean {
        if (points.size < 4) return false

        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }

        val width = maxX - minX
        val height = maxY - minY
        val start = points.first()
        val end = points.last()

        val screenH = rootLayout.height.toFloat().takeIf { it > 0f } ?: resources.displayMetrics.heightPixels.toFloat()

        // 1. Swipe Down from Top (Notification Shade / Quick Settings)
        if (start.y < screenH * 0.22f && (end.y - start.y) > 100f && height > width) {
            Log.d("GestureMusic", "Top swipe down -> Exiting mode")
            finish()
            return true
        }

        // 2. Swipe Up from Bottom (Home / Unlock navigation)
        if (start.y > screenH * 0.78f && (start.y - end.y) > 100f && height > width) {
            Log.d("GestureMusic", "Bottom swipe up -> Exiting mode")
            finish()
            return true
        }

        if (width < 60f && height < 60f) return false

        // 3. Next Track: Right Arrow ( > ) OR Horizontal Swipe Right ( ---> )
        if (isNextEnabled) {
            val isArrowRight = width > 80f && start.x < minX + width * 0.5f && end.x < minX + width * 0.5f && maxX > minX + 60f
            val isSwipeRight = width > 90f && height < width * 0.9f && (end.x - start.x) > 70f

            if (isArrowRight || isSwipeRight) {
                Log.d("GestureMusic", "Next Track Gesture recognized on Lock Screen")
                triggerHaptic(2)
                showConfirmationGlow()
                UniversalMediaService.sendNext()
                return true
            }
        }

        // 4. Previous Track: Left Arrow ( < ) OR Horizontal Swipe Left ( <--- )
        if (isPrevEnabled) {
            val isArrowLeft = width > 80f && start.x > maxX - width * 0.5f && end.x > maxX - width * 0.5f && minX < maxX - 60f
            val isSwipeLeft = width > 90f && height < width * 0.9f && (start.x - end.x) > 70f

            if (isArrowLeft || isSwipeLeft) {
                Log.d("GestureMusic", "Previous Track Gesture recognized on Lock Screen")
                triggerHaptic(2)
                showConfirmationGlow()
                UniversalMediaService.sendPrevious()
                return true
            }
        }

        // 5. Circle Gesture: Clockwise (Volume UP) / Anticlockwise (Volume DOWN)
        val distStartEnd = hypot(start.x - end.x, start.y - end.y)
        val isCircle = width > 60f && height > 60f && distStartEnd < max(width, height) * 0.65f
        if (isCircle) {
            val signedArea = calculateSignedArea(points)
            if (signedArea > 0f && isVolUpEnabled) {
                Log.d("GestureMusic", "Clockwise Circle (Volume UP +10%) recognized")
                triggerHaptic(1)
                showConfirmationGlow()
                UniversalMediaService.adjustVolume(isIncrease = true, this)
                return true
            } else if (signedArea < 0f && isVolDownEnabled) {
                Log.d("GestureMusic", "Anticlockwise Circle (Volume DOWN -10%) recognized")
                triggerHaptic(1)
                showConfirmationGlow()
                UniversalMediaService.adjustVolume(isIncrease = false, this)
                return true
            } else if (isPauseEnabled) {
                Log.d("GestureMusic", "Circle Play/Pause Gesture recognized")
                triggerHaptic(1)
                showConfirmationGlow()
                UniversalMediaService.togglePlayPause()
                return true
            }
        }

        return false
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
}
