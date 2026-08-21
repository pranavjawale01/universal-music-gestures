package com.example.universaloffscreenmusic

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.util.Log

class UniversalMediaService : NotificationListenerService() {
    private var sessionManager: MediaSessionManager? = null
    private var isMasterEnabled = true
    private var isAutoActivateEnabled = false
    private val handler = Handler(Looper.getMainLooper())

    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { sessions ->
        updateActiveController(sessions)
    }

    private val playbackCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            updatePlaybackStatus()
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d("GestureMusic", "Screen Receiver event: ${intent.action}")
            if (intent.action == Intent.ACTION_SCREEN_OFF && isMasterEnabled && isAutoActivateEnabled) {
                Log.d("GestureMusic", "Screen OFF, preparing lockscreen gestures...")
                handler.postDelayed({
                    try {
                        val lockIntent = Intent(context, SenseLockActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        context.startActivity(lockIntent)
                    } catch (e: Exception) {
                        Log.e("GestureMusic", "Failed to start SenseLockActivity: ${e.message}")
                    }
                }, 150)
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
        registerReceiver(screenReceiver, filter)
        updateConfig()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "UPDATE_CONFIG") {
            updateConfig()
        }
        return super.onStartCommand(intent, flags, startId)
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
