package com.aditya.music.data.model

import android.net.Uri

data class Album(
    val id: Long,
    val name: String,
    val artist: String,
    val artworkUri: Uri?,
    val songCount: Int,
    val songs: List<Song> = emptyList()
)
