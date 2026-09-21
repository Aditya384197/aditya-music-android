package com.aditya.music.media.service

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider

/**
 * Native Media3 notification provider.
 *
 * Using Media3's default MediaStyle notification is intentional: Android System UI can expose
 * the real media seek bar from the MediaSession/PlaybackState instead of a non-interactive
 * RemoteViews progress widget. This keeps lock-screen controls connected to the actual player.
 */
@UnstableApi
class ProgressMediaNotificationProvider(context: Context) : androidx.media3.session.MediaNotification.Provider {
    private val delegate = DefaultMediaNotificationProvider.Builder(context).build().apply {
        setSmallIcon(com.aditya.music.R.drawable.ic_notification_aditya)
    }

    override fun createNotification(
        mediaSession: androidx.media3.session.MediaSession,
        customLayout: com.google.common.collect.ImmutableList<androidx.media3.session.CommandButton>,
        actionFactory: androidx.media3.session.MediaNotification.ActionFactory,
        onNotificationChangedCallback: androidx.media3.session.MediaNotification.Provider.Callback
    ): androidx.media3.session.MediaNotification {
        return delegate.createNotification(
            mediaSession,
            customLayout,
            actionFactory,
            onNotificationChangedCallback
        )
    }

    override fun handleCustomCommand(
        session: androidx.media3.session.MediaSession,
        action: String,
        extras: android.os.Bundle
    ): Boolean = delegate.handleCustomCommand(session, action, extras)
}
