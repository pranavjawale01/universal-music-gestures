package com.example.universaloffscreenmusic

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService

class UniversalMediaService : NotificationListenerService() {
    private var sessionManager: MediaSessionManager? = null

    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { sessions ->
        updateActiveController(sessions)
    }

    companion object {
        private var activeController: MediaController? = null

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
    }

    private fun updateActiveController(sessions: List<MediaController>?) {
        if (sessions.isNullOrEmpty()) {
            activeController = null
        } else {
            // Pick the first one that is playing, or just the first one if none are playing
            activeController = sessions.find { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?: sessions[0]
        }
    }
}
