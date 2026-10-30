package com.music.player.data

import android.net.Uri

/** A track indexed by Android MediaStore. No filesystem paths are exposed to the UI. */
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val contentUri: Uri,
    val artworkUri: Uri?,
    val sizeBytes: Long,
    val dateAddedSeconds: Long
) {
    val durationLabel: String get() = formatDuration(durationMs)
}

fun formatDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
