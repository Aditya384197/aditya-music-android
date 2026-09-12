package com.aditya.music.data.model

import android.net.Uri

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val duration: Long, // in milliseconds
    val contentUri: Uri,
    val albumArtUri: Uri?,
    val dateAdded: Long,
    val size: Long,
    val isFavorite: Boolean = false,
    val playCount: Int = 0
) {
    val formattedDuration: String
        get() {
            val totalSec = duration / 1000
            val min = totalSec / 60
            val sec = totalSec % 60
            return String.format("%d:%02d", min, sec)
        }
}
