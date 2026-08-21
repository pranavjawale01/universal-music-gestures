package com.example.universaloffscreenmusic

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.util.Log
import androidx.core.app.NotificationCompat

class UniversalMediaService : NotificationListenerService() {
    private var sessionManager: MediaSessionManager? = null
    private var isMasterEnabled = true
    private var isAutoActivateEnabled = true
    private val handler = Handler(Looper.getMainLooper())

    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { sessions ->
        updateActiveController(sessions)
    }

    private val playbackCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            updatePlaybackStatus()
        }
    }

    private var lastScreenOffTime = 0L

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d("GestureMusic", "Screen Receiver event: ${intent.action}")
            if (intent.action == Intent.ACTION_SCREEN_OFF && isMasterEnabled && isAutoActivateEnabled) {
                val now = System.currentTimeMillis()

                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                val isAudioActive = isMusicPlaying || (audioManager?.isMusicActive == true)
                val isCallActive = audioManager?.mode == AudioManager.MODE_IN_CALL ||
                                   audioManager?.mode == AudioManager.MODE_RINGTONE ||
                                   audioManager?.mode == AudioManager.MODE_IN_COMMUNICATION

                Log.d("GestureMusic", "Screen off detected. isAudioActive=$isAudioActive, isCallActive=$isCallActive")

                // Auto-launch when enabled and no phone call is ringing/active
                if (isMasterEnabled && isAutoActivateEnabled && !isCallActive && !SenseLockActivity.isSenseActive && (now - lastScreenOffTime > 1500L) && (now - SenseLockActivity.lastUserExitTime > 1500L)) {
                    lastScreenOffTime = now
                    Log.d("GestureMusic", "Starting Sense Lock Screen quietly on screen off")

                    try {
                        val lockIntent = Intent(context, SenseLockActivity::class.java).apply {
                            addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                            )
                        }
                        context.startActivity(lockIntent)
                    } catch (e: Exception) {
                        Log.e("GestureMusic", "Start SenseLockActivity failed: ${e.message}")
                    }
                } else {
                    Log.d("GestureMusic", "Allowing normal screen sleep without interference")
                }
            }
        }
    }

    companion object {
        private var activeController: MediaController? = null
        var isMusicPlaying = false
            private set

        fun sendNext() {
            activeController?.transportControls?.skipToNext()
        }

        fun sendPrevious() {
            activeController?.transportControls?.skipToPrevious()
        }

        fun togglePlayPause() {
            activeController?.let { controller ->
                val state = controller.playbackState?.state
                if (state == PlaybackState.STATE_PLAYING) {
                    controller.transportControls.pause()
                } else {
                    controller.transportControls.play()
                }
            }
        }
        
        fun adjustVolume(isIncrease: Boolean, context: Context) {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val step = kotlin.math.max(1, kotlin.math.round(maxVol * 0.10f).toInt())
            val newVol = if (isIncrease) {
                kotlin.math.min(maxVol, currentVol + step)
            } else {
                kotlin.math.max(0, currentVol - step)
            }
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, AudioManager.FLAG_SHOW_UI)
            Log.d("GestureMusic", "Volume adjusted: $currentVol -> $newVol (step: $step, max: $maxVol)")
        }

        fun updatePlaybackStatus() {
            val newState = activeController?.playbackState?.state == PlaybackState.STATE_PLAYING
            if (newState != isMusicPlaying) {
                Log.d("GestureMusic", "Music status changed: $newState")
            }
            isMusicPlaying = newState
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }
        updateConfig()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        updateConfig()
        return START_STICKY
    }

    private fun updateConfig() {
        val prefs = getSharedPreferences("gestures_prefs", Context.MODE_PRIVATE)
        isMasterEnabled = prefs.getBoolean("master_enabled", true)
        isAutoActivateEnabled = prefs.getBoolean("auto_activate", true)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        sessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val component = ComponentName(this, UniversalMediaService::class.java)
        
        sessionManager?.addOnActiveSessionsChangedListener(sessionListener, component)
        
        // Initial update
        val sessions = sessionManager?.getActiveSessions(component)
        updateActiveController(sessions)
    }

    override fun onDestroy() {
        super.onDestroy()
        sessionManager?.removeOnActiveSessionsChangedListener(sessionListener)
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {}
    }

    private fun updateActiveController(sessions: List<MediaController>?) {
        activeController?.unregisterCallback(playbackCallback)
        if (sessions.isNullOrEmpty()) {
            activeController = null
        } else {
            // Pick the first one that is playing, or just the first one if none are playing
            activeController = sessions.find { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?: sessions[0]
            activeController?.registerCallback(playbackCallback)
        }
        updatePlaybackStatus()
    }
}
