package com.aditya.music.media.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import com.aditya.music.MainActivity
import com.aditya.music.R
import com.aditya.music.media.player.EqualizerController
import com.aditya.music.media.player.EqualizerManager
import java.util.Locale

/**
 * Long-lived playback service for Aditya Music.
 *
 * Responsibilities:
 * - Keep the ExoPlayer/MediaSession independent of the Activity lifecycle.
 * - Keep the media notification + foreground service alive for up to 60 minutes after pause.
 * - Remove the notification after the one-hour paused grace period.
 * - Provide previous / play-pause / next controls through the MediaSession.
 * - Keep elapsed/total duration readable without waking the UI every second.
 */
@UnstableApi
class MusicService : MediaLibraryService() {

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "aditya_music_playback"
        private const val NOTIFICATION_CHANNEL_NAME = "Music playback"
        private const val NOTIFICATION_ID = 1001
        private const val PAUSED_NOTIFICATION_TIMEOUT_MS = 60L * 60L * 1000L
    }

    private var player: ExoPlayer? = null
    private var mediaSession: MediaLibrarySession? = null
    private var equalizerManager: EqualizerManager? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val notificationManager by lazy {
        getSystemService(NotificationManager::class.java)
    }

    private val removePausedNotification = Runnable {
        val currentPlayer = player ?: return@Runnable
        if (!currentPlayer.isPlaying) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            notificationManager?.cancel(NOTIFICATION_ID)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            rebuildEqualizer(audioSessionId)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

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

    /**
     * Media3 1.3.1's built-in paused foreground grace period is limited to its internal default.
     * We therefore own the notification/foreground transition and deliberately keep it active for
     * one hour after a pause, while still using Media3's MediaStyle so System UI can expose the
     * same media session controls.
     */
    override fun onUpdateNotification(
        session: MediaSession,
        startInForegroundRequired: Boolean
    ) {
        val currentPlayer = session.player
        if (currentPlayer.mediaItemCount == 0) {
            mainHandler.removeCallbacks(removePausedNotification)
            stopForeground(STOP_FOREGROUND_REMOVE)
            notificationManager?.cancel(NOTIFICATION_ID)
            return
        }

        val metadata = currentPlayer.mediaMetadata
        val title = metadata.title?.toString()?.takeIf { it.isNotBlank() } ?: "Aditya Music"
        val artist = metadata.artist?.toString()?.takeIf { it.isNotBlank() } ?: "Unknown artist"
        val album = metadata.albumTitle?.toString()?.takeIf { it.isNotBlank() }
        val duration = currentPlayer.duration.takeIf { it >= 0L } ?: 0L
        val position = currentPlayer.currentPosition.coerceAtLeast(0L)

        val playPauseIntent = mediaActionPendingIntent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        val previousIntent = mediaActionPendingIntent(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        val nextIntent = mediaActionPendingIntent(KeyEvent.KEYCODE_MEDIA_NEXT)

        val notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_aditya)
            .setContentTitle(title)
            .setContentText(artist)
            .setSubText(buildProgressText(position, duration, album))
            .setContentIntent(session.getSessionActivity())
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(currentPlayer.isPlaying)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setShowWhen(false)
            .addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_previous,
                    "Previous",
                    previousIntent
                )
            )
            .addAction(
                NotificationCompat.Action(
                    if (currentPlayer.isPlaying) android.R.drawable.ic_media_pause
                    else android.R.drawable.ic_media_play,
                    if (currentPlayer.isPlaying) "Pause" else "Play",
                    playPauseIntent
                )
            )
            .addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_next,
                    "Next",
                    nextIntent
                )
            )
            .setStyle(
                MediaStyleNotificationHelper.MediaStyle(session)
                    .setShowActionsInCompactView(0, 1, 2)
            )

        if (duration > 0L) {
            notificationBuilder.setProgress(
                1000,
                ((position.coerceAtMost(duration).toDouble() / duration.toDouble()) * 1000.0)
                    .toInt()
                    .coerceIn(0, 1000),
                false
            )
            notificationBuilder.setWhen(System.currentTimeMillis() - position)
            notificationBuilder.setUsesChronometer(currentPlayer.isPlaying)
        }

        val notification: Notification = notificationBuilder.build()

        mainHandler.removeCallbacks(removePausedNotification)
        if (currentPlayer.isPlaying) {
            // Active playback stays in a foreground service for reliable long-running music.
            startForeground(NOTIFICATION_ID, notification)
            notificationManager?.notify(NOTIFICATION_ID, notification)
        } else {
            // A paused player does not need a non-dismissible foreground notification. Detach it so
            // Android can treat it as a normal media notification that the user may swipe away.
            notificationManager?.notify(NOTIFICATION_ID, notification)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_DETACH)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(false)
            }
            mainHandler.postDelayed(removePausedNotification, PAUSED_NOTIFICATION_TIMEOUT_MS)
        }
    }

    private fun mediaActionPendingIntent(keyCode: Int): PendingIntent {
        val intent = Intent(this, MusicService::class.java).apply {
            action = Intent.ACTION_MEDIA_BUTTON
            putExtra(
                Intent.EXTRA_KEY_EVENT,
                KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
            )
        }
        return PendingIntent.getService(
            this,
            keyCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun buildProgressText(position: Long, duration: Long, album: String?): String {
        val timing = if (duration > 0L) {
            "${formatDuration(position)} / ${formatDuration(duration)}"
        } else {
            formatDuration(position)
        }
        return if (!album.isNullOrBlank()) "$timing • $album" else timing
    }

    private fun formatDuration(ms: Long): String {
        val totalSeconds = ms.coerceAtLeast(0L) / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = notificationManager ?: return
        if (manager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    NOTIFICATION_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Playback controls for Aditya Music"
                    setShowBadge(false)
                }
            )
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
        mainHandler.removeCallbacks(removePausedNotification)
        EqualizerController.detach(equalizerManager)
        equalizerManager?.release()
        equalizerManager = null

        player?.removeListener(playerListener)
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null

        notificationManager?.cancel(NOTIFICATION_ID)
        super.onDestroy()
    }
}
