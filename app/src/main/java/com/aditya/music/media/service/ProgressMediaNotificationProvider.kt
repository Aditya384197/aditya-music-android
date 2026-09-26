package com.aditya.music.media.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaStyleNotificationHelper
import com.aditya.music.R

/**
 * Native Media3 notification, augmented with a real, always-visible progress bar and
 * elapsed/remaining time - Android's own MediaStyle template has no slot for these (some phones'
 * launchers add their own version of this on top of a well-formed session, others don't), so a
 * small custom RemoteViews content view is layered onto Media3's own notification instead of
 * depending on that. Media3 still owns the channel, the action buttons/PendingIntents, and the
 * lock-screen/System UI wiring via [DefaultMediaNotificationProvider] - this only adds a content
 * view on top of what it already builds, plus a subtle brand-colour tint (`setColorized`) so the
 * card doesn't read as flat black.
 */
@UnstableApi
class ProgressMediaNotificationProvider(private val context: Context) :
    androidx.media3.session.MediaNotification.Provider {

    private val delegate = DefaultMediaNotificationProvider.Builder(context).build().apply {
        setSmallIcon(R.drawable.ic_notification_aditya)
    }

    private var lastNotification: android.app.Notification? = null
    private var lastNotificationId: Int = -1
    private var lastMediaSession: androidx.media3.session.MediaSession? = null

    override fun createNotification(
        mediaSession: androidx.media3.session.MediaSession,
        customLayout: com.google.common.collect.ImmutableList<androidx.media3.session.CommandButton>,
        actionFactory: androidx.media3.session.MediaNotification.ActionFactory,
        onNotificationChangedCallback: androidx.media3.session.MediaNotification.Provider.Callback
    ): androidx.media3.session.MediaNotification {
        val base = delegate.createNotification(mediaSession, customLayout, actionFactory, onNotificationChangedCallback)
        val augmented = withProgressContent(base.notification, mediaSession)
        lastNotification = augmented
        lastNotificationId = base.notificationId
        lastMediaSession = mediaSession
        return androidx.media3.session.MediaNotification(base.notificationId, augmented)
    }

    override fun handleCustomCommand(
        session: androidx.media3.session.MediaSession,
        action: String,
        extras: android.os.Bundle
    ): Boolean = delegate.handleCustomCommand(session, action, extras)

    /**
     * Called roughly once a second while something is playing (see MusicService's ticker) so the
     * progress bar and elapsed time visibly advance instead of only updating on play/pause/track
     * change. Cheap: reuses the last full notification and only refreshes the custom content view.
     */
    fun tick(): android.app.Notification? {
        val session = lastMediaSession ?: return null
        val base = lastNotification ?: return null
        if (lastNotificationId < 0) return null
        val refreshed = withProgressContent(base, session)
        lastNotification = refreshed
        return refreshed
    }

    val notificationId: Int get() = lastNotificationId

    private fun withProgressContent(
        base: android.app.Notification,
        mediaSession: androidx.media3.session.MediaSession
    ): android.app.Notification {
        val player = mediaSession.player
        val durationMs = player.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: 0L
        val positionMs = player.currentPosition.coerceIn(0L, if (durationMs > 0) durationMs else Long.MAX_VALUE)
        val metadata = player.mediaMetadata

        val remoteViews = RemoteViews(context.packageName, R.layout.notification_media_progress).apply {
            setTextViewText(R.id.notif_title, metadata.title?.toString() ?: "Aditya Music")
            setTextViewText(R.id.notif_artist, metadata.artist?.toString() ?: "")
            setTextViewText(R.id.notif_elapsed, formatMs(positionMs))
            setTextViewText(R.id.notif_total, formatMs(durationMs))
            val progress = if (durationMs > 0) ((positionMs * 1000L) / durationMs).toInt().coerceIn(0, 1000) else 0
            setProgressBar(R.id.notif_progress, 1000, progress, durationMs <= 0)
            setImageViewBitmap(R.id.notif_art, loadArt(metadata.artworkUri))
        }

        return runCatching {
            androidx.core.app.NotificationCompat.Builder(context, base)
                .setStyle(MediaStyleNotificationHelper.DecoratedMediaCustomViewStyle(mediaSession))
                .setCustomContentView(remoteViews)
                .setCustomBigContentView(remoteViews)
                .setColorized(true)
                .setColor(context.getColor(R.color.aditya_primary))
                .build()
        }.getOrDefault(base)
    }

    private fun loadArt(uri: Uri?): Bitmap {
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }.getOrNull()?.let { return it }
        }
        return runCatching {
            BitmapFactory.decodeResource(context.resources, R.drawable.aditya_logo)
        }.getOrElse {
            Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }
    }

    private fun formatMs(ms: Long): String {
        val totalSeconds = (ms.coerceAtLeast(0L) / 1000L)
        return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
    }
}
