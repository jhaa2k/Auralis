package com.auralis.app.playback

import androidx.media3.common.AudioListener
import androidx.media3.exoplayer.ExoPlayer
import com.auralis.app.audio.AudioSessionBridge
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class AuralisPlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this).build()
        AudioSessionBridge.publish(player.audioSessionId)
        player.addAudioListener(object : AudioListener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                AudioSessionBridge.publish(audioSessionId)
            }
        })
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        AudioSessionBridge.publish(0)
        super.onDestroy()
    }
}
