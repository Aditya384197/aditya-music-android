package com.aditya.music.media.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.RemoteViews
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaStyleNotificationHelper
import com.aditya.music.R
import kotlin.math.roundToInt

/**
 * Compact Media3 notification with real progress/time values and a faint version of the current
 * song artwork behind the controls. Artwork is cached so the one-second progress ticker never
 * decodes the same image repeatedly.
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

    private var cachedArtKey: String? = null
    private var cachedArt: Bitmap? = null
    private var cachedBackgroundArt: Bitmap? = null

    override fun createNotification(
        mediaSession: androidx.media3.session.MediaSession,
        customLayout: com.google.common.collect.ImmutableList<androidx.media3.session.CommandButton>,
        actionFactory: androidx.media3.session.MediaNotification.ActionFactory,
        onNotificationChangedCallback: androidx.media3.session.MediaNotification.Provider.Callback
    ): androidx.media3.session.MediaNotification {
        val base = delegate.createNotification(
            mediaSession,
            customLayout,
            actionFactory,
            onNotificationChangedCallback
        )
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
        val positionMs = player.currentPosition.coerceIn(
            0L,
            if (durationMs > 0) durationMs else Long.MAX_VALUE
        )
        val metadata = player.mediaMetadata
        val art = loadArt(metadata.artworkUri)
        val backgroundArt = makeBackgroundArt(art)

        val remoteViews = RemoteViews(context.packageName, R.layout.notification_media_progress).apply {
            setTextViewText(R.id.notif_title, metadata.title?.toString() ?: "Aditya Music")
            setTextViewText(R.id.notif_artist, metadata.artist?.toString() ?: "")
            setTextViewText(R.id.notif_elapsed, formatMs(positionMs))
            setTextViewText(R.id.notif_total, formatMs(durationMs))
            val progress = if (durationMs > 0) {
                ((positionMs * 1000L) / durationMs).toInt().coerceIn(0, 1000)
            } else 0
            setProgressBar(R.id.notif_progress, 1000, progress, durationMs <= 0)
            setImageViewBitmap(R.id.notif_art, art)
            setImageViewBitmap(R.id.notif_background, backgroundArt)
            setImageViewAlpha(R.id.notif_background, 78)
        }

        return runCatching {
            androidx.core.app.NotificationCompat.Builder(context, base)
                .setStyle(MediaStyleNotificationHelper.DecoratedMediaCustomViewStyle(mediaSession))
                .setCustomContentView(remoteViews)
                .setCustomBigContentView(remoteViews)
                .setColorized(false)
                .setColor(context.getColor(R.color.aditya_dark_surface))
                .build()
        }.getOrDefault(base)
    }

    private fun loadArt(uri: Uri?): Bitmap {
        val key = uri?.toString() ?: "__aditya_fallback__"
        if (cachedArtKey == key) {
            cachedArt?.let { return it }
        }

        val decoded = if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }.getOrNull()
        } else null

        val source = decoded ?: runCatching {
            BitmapFactory.decodeResource(context.resources, R.drawable.aditya_logo)
        }.getOrNull() ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

        val scaled = scaleDown(source, 512)
        if (scaled !== source && !source.isRecycled) source.recycle()

        cachedArtKey = key
        cachedArt = scaled
        cachedBackgroundArt = null
        return scaled
    }

    private fun makeBackgroundArt(art: Bitmap): Bitmap {
        cachedBackgroundArt?.let { return it }
        val background = scaleDown(art, 384)
        cachedBackgroundArt = background
        return background
    }

    private fun scaleDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= maxDimension) return bitmap
        val ratio = maxDimension.toFloat() / maxSide.toFloat()
        val width = (bitmap.width * ratio).roundToInt().coerceAtLeast(1)
        val height = (bitmap.height * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun formatMs(ms: Long): String {
        val totalSeconds = ms.coerceAtLeast(0L) / 1000L
        return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
    }
}
