package com.aditya.music.media.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.aditya.music.MainActivity
import com.aditya.music.media.player.EqualizerManager

/**
 * Foreground MediaSessionService powering background playback for Aditya Music.
 * Handles audio focus, notifications, lockscreen, bluetooth, and equalizer session bindings.
 */
@UnstableApi
class MusicService : MediaLibraryService() {

    private var player: ExoPlayer? = null
    private var mediaSession: MediaLibrarySession? = null
    private var equalizerManager: EqualizerManager? = null

    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true) // Handles Android Audio Focus automatically
            .setHandleAudioBecomingNoisy(true) // Pauses when headphones unplugged
            .build()

        player?.let { exo ->
            equalizerManager = EqualizerManager(exo.audioSessionId)
        }

        val activityIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, activityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        player?.let { exo ->
            mediaSession = MediaLibrarySession.Builder(this, exo, object : MediaLibrarySession.Callback {})
                .setSessionActivity(pendingIntent)
                .build()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaSession
    }

    override fun onDestroy() {
        equalizerManager?.release()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        player = null
        super.onDestroy()
    }
}
