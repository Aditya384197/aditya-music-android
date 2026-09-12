package com.aditya.music.data.model

data class Artist(
    val id: Long,
    val name: String,
    val songCount: Int,
    val albumCount: Int,
    val songs: List<Song> = emptyList()
)
