package com.aditya.music.media.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper.MediaStyle
import com.google.common.collect.ImmutableList

/**
 * Media3 notification with a real platform progress bar and elapsed/total text.
 * Android 11+ system media controls can expose an interactive seek bar from the same MediaSession.
 * On older Android versions the progress bar remains informational because standard notifications
 * do not provide an interactive scrubber.
 */
@UnstableApi
class ProgressMediaNotificationProvider(private val context: Context) : MediaNotification.Provider {

    companion object {
        const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "aditya_music_playback"
        private const val CHANNEL_NAME = "Music playback"

        private fun formatDuration(ms: Long): String {
            val seconds = (ms.coerceAtLeast(0L) / 1000L)
            val minutes = seconds / 60L
            val remaining = seconds % 60L
            return if (minutes >= 60L) {
                val hours = minutes / 60L
                "%d:%02d:%02d".format(hours, minutes % 60L, remaining)
            } else {
                "%d:%02d".format(minutes, remaining)
            }
        }
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Background music playback controls"
                    setShowBadge(false)
                }
            )
        }
    }

    override fun createNotification(
        mediaSession: MediaSession,
        customLayout: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback
    ): MediaNotification {
        val player = mediaSession.player
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.aditya.music.R.drawable.ic_notification_aditya)
            .setContentTitle(player.mediaMetadata.title ?: "Aditya Music")
            .setContentText(buildProgressTextWithArtist(player))
            .setSubText(player.mediaMetadata.albumTitle ?: "Aditya Music")
            .setContentIntent(mediaSession.sessionActivity)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSilent(true)

        val compactIndices = mutableListOf<Int>()
        var actionIndex = 0
        if (player.availableCommands.contains(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)) {
            builder.addAction(
                actionFactory.createMediaAction(
                    mediaSession,
                    IconCompat.createWithResource(context, android.R.drawable.ic_media_previous),
                    "Previous",
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
                )
            )
            compactIndices += actionIndex++
        }

        if (player.availableCommands.contains(Player.COMMAND_PLAY_PAUSE)) {
            builder.addAction(
                actionFactory.createMediaAction(
                    mediaSession,
                    IconCompat.createWithResource(
                        context,
                        if (player.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                    ),
                    if (player.isPlaying) "Pause" else "Play",
                    Player.COMMAND_PLAY_PAUSE
                )
            )
            compactIndices += actionIndex++
        }

        if (player.availableCommands.contains(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)) {
            builder.addAction(
                actionFactory.createMediaAction(
                    mediaSession,
                    IconCompat.createWithResource(context, android.R.drawable.ic_media_next),
                    "Next",
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
                )
            )
            compactIndices += actionIndex
        }

        val style = MediaStyle(mediaSession)
        if (compactIndices.isNotEmpty()) {
            style.setShowActionsInCompactView(*compactIndices.take(3).toIntArray())
        }

        val duration = player.duration
        if (duration > 0L && duration <= Int.MAX_VALUE.toLong()) {
            val position = player.currentPosition.coerceIn(0L, duration).toInt()
            builder.setProgress(duration.toInt(), position, false)
        }

        val notification = builder
            .setStyle(style)
            .setOngoing(player.isPlaying)
            .build()

        return MediaNotification(NOTIFICATION_ID, notification)
    }

    override fun handleCustomCommand(
        session: MediaSession,
        action: String,
        extras: android.os.Bundle
    ): Boolean = false

    private fun buildProgressTextWithArtist(player: Player): String {
        val artist = player.mediaMetadata.artist?.toString().orEmpty()
        val progress = buildProgressText(player)
        return if (artist.isBlank()) progress else "$artist • $progress"
    }

    private fun buildProgressText(player: Player): String {
        val duration = player.duration
        return if (duration > 0L) {
            "${formatDuration(player.currentPosition)} / ${formatDuration(duration)}"
        } else {
            "Playing"
        }
    }
}
