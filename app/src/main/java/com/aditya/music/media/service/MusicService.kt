package com.aditya.music.media.service

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.aditya.music.MainActivity
import com.aditya.music.R
import com.aditya.music.media.player.EqualizerController
import com.aditya.music.media.player.EqualizerManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * Long-lived playback service for Aditya Music.
 *
 * The service intentionally delegates notification rendering to Media3's native provider. This
 * keeps the Android media controls and the MediaSession in one source of truth, including the
 * position/duration state used by supported System UI seek controls.
 */
@UnstableApi
class MusicService : MediaLibraryService() {

    companion object {
        private const val PLAYBACK_STATE_PREFS = "aditya_music_playback_state"
        private const val KEY_QUEUE = "queue"
        private const val KEY_INDEX = "index"
        private const val KEY_POSITION = "position"
    }

    private var player: ExoPlayer? = null
    private var mediaSession: MediaLibrarySession? = null
    private var equalizerManager: EqualizerManager? = null
    private var restoringState = false

    private val playerListener = object : Player.Listener {
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            rebuildEqualizer(audioSessionId)
        }

        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            persistPlaybackState()
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            persistPlaybackState()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            persistPlaybackState()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            persistPlaybackState()
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
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setSeekBackIncrementMs(10_000L)
            .setSeekForwardIncrementMs(10_000L)
            .build()
            .also {
                // Preserve source timing and dynamics. No silence skipping or extra player-side
                // processing is enabled here; EQ is applied only when the user turns it on.
                it.setSkipSilenceEnabled(false)
                it.setPauseAtEndOfMediaItems(false)
                it.addListener(playerListener)
            }

        player = exoPlayer
        restorePlaybackState(exoPlayer)
        if (exoPlayer.audioSessionId > 0) rebuildEqualizer(exoPlayer.audioSessionId)

        val activityIntent = Intent(this, MainActivity::class.java)
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            activityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Media3 owns the media notification so the session's playback state/commands remain
        // synchronized with the notification and System UI. Android 13+ specifically populates
        // its media control from MediaSession data.
        DefaultMediaNotificationProvider(this).also { provider ->
            provider.setSmallIcon(R.drawable.ic_notification_aditya)
            setMediaNotificationProvider(provider)
        }

        mediaSession = MediaLibrarySession.Builder(
            this,
            exoPlayer,
            object : MediaLibrarySession.Callback {}
        )
            .setSessionActivity(sessionActivity)
            .build()
    }

    private fun persistPlaybackState() {
        if (restoringState) return
        val currentPlayer = player ?: return
        if (currentPlayer.mediaItemCount == 0) return

        runCatching {
            val queue = JSONArray()
            for (i in 0 until currentPlayer.mediaItemCount) {
                val item = currentPlayer.getMediaItemAt(i)
                val metadata = item.mediaMetadata
                queue.put(JSONObject().apply {
                    put("id", item.mediaId)
                    put("uri", item.localConfiguration?.uri?.toString() ?: "")
                    put("title", metadata.title?.toString() ?: "")
                    put("artist", metadata.artist?.toString() ?: "")
                    put("album", metadata.albumTitle?.toString() ?: "")
                    put("art", metadata.artworkUri?.toString() ?: "")
                    put("duration", metadata.durationMs ?: 0L)
                })
            }
            getSharedPreferences(PLAYBACK_STATE_PREFS, MODE_PRIVATE).edit()
                .putString(KEY_QUEUE, queue.toString())
                .putInt(KEY_INDEX, currentPlayer.currentMediaItemIndex.coerceAtLeast(0))
                .putLong(KEY_POSITION, currentPlayer.currentPosition.coerceAtLeast(0L))
                .apply()
        }
    }

    private fun restorePlaybackState(targetPlayer: ExoPlayer) {
        val prefs = getSharedPreferences(PLAYBACK_STATE_PREFS, MODE_PRIVATE)
        val rawQueue = prefs.getString(KEY_QUEUE, null) ?: return

        runCatching {
            val array = JSONArray(rawQueue)
            val items = ArrayList<androidx.media3.common.MediaItem>(array.length())

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val uri = obj.optString("uri")
                if (uri.isBlank()) continue

                items += androidx.media3.common.MediaItem.Builder()
                    .setMediaId(obj.optString("id"))
                    .setUri(uri)
                    .setMediaMetadata(
                        androidx.media3.common.MediaMetadata.Builder()
                            .setTitle(obj.optString("title"))
                            .setArtist(obj.optString("artist"))
                            .setAlbumTitle(obj.optString("album"))
                            .setDurationMs(
                                obj.optLong("duration", 0L).takeIf { it > 0L }
                            )
                            .apply {
                                obj.optString("art")
                                    .takeIf { it.isNotBlank() }
                                    ?.let { setArtworkUri(Uri.parse(it)) }
                            }
                            .build()
                    )
                    .build()
            }

            if (items.isNotEmpty()) {
                val index = prefs.getInt(KEY_INDEX, 0).coerceIn(items.indices)
                val position = prefs.getLong(KEY_POSITION, 0L).coerceAtLeast(0L)
                restoringState = true
                try {
                    // Restoring must never auto-play. A later system play command is then free to
                    // start the player without being raced by a restore-time pause().
                    targetPlayer.setMediaItems(items, index, position)
                    targetPlayer.prepare()
                } finally {
                    restoringState = false
                }
            }
        }.onFailure {
            restoringState = false
        }
    }

    private fun rebuildEqualizer(audioSessionId: Int) {
        if (audioSessionId <= 0) return

        equalizerManager?.let { old ->
            EqualizerController.detach(old)
            old.release()
        }

        runCatching {
            EqualizerManager(this, audioSessionId)
        }.onSuccess { manager ->
            equalizerManager = manager
            EqualizerController.attach(manager)
        }.onFailure {
            equalizerManager = null
            EqualizerController.detach()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaSession
    }

    override fun onDestroy() {
        EqualizerController.detach(equalizerManager)
        equalizerManager?.release()
        equalizerManager = null

        player?.removeListener(playerListener)
        persistPlaybackState()
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null

        super.onDestroy()
    }
}
