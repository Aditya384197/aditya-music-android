package com.aditya.music.media.service

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.aditya.music.MainActivity
import com.aditya.music.media.player.EqualizerController
import com.aditya.music.media.player.EqualizerManager

/**
 * Foreground MediaSessionService powering background playback for Aditya Music.
 * The equalizer is bound to the real ExoPlayer audio session reported after the
 * audio output is created, not to the initial placeholder session id.
 */
@UnstableApi
class MusicService : MediaLibraryService() {

    private var player: ExoPlayer? = null
    private var mediaSession: MediaLibrarySession? = null
    private var equalizerManager: EqualizerManager? = null

    private val playerListener = object : Player.Listener {
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            rebuildEqualizer(audioSessionId)
        }
    }

    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .also { it.addListener(playerListener) }

        player = exoPlayer

        if (exoPlayer.audioSessionId > 0) {
            rebuildEqualizer(exoPlayer.audioSessionId)
        }

        val activityIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            activityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaLibrarySession.Builder(
            this,
            exoPlayer,
            object : MediaLibrarySession.Callback {}
        )
            .setSessionActivity(pendingIntent)
            .build()
    }

    private fun rebuildEqualizer(audioSessionId: Int) {
        if (audioSessionId <= 0) return

        equalizerManager?.let { old ->
            EqualizerController.detach(old)
            old.release()
        }

        val manager = EqualizerManager(this, audioSessionId)
        equalizerManager = manager
        EqualizerController.attach(manager)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaSession
    }

    override fun onDestroy() {
        EqualizerController.detach(equalizerManager)
        equalizerManager?.release()
        equalizerManager = null

        player?.removeListener(playerListener)
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null

        super.onDestroy()
    }
}
