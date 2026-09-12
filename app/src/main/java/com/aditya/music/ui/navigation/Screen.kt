package com.aditya.music.ui.navigation

sealed class Screen(val route: String, val title: String) {
    object Home : Screen("home", "Home")
    object Songs : Screen("songs", "Songs")
    object Albums : Screen("albums", "Albums")
    object Artists : Screen("artists", "Artists")
    object Playlists : Screen("playlists", "Playlists")
    object Settings : Screen("settings", "Settings")
    object NowPlaying : Screen("now_playing", "Now Playing")
    object Queue : Screen("queue", "Queue")
    object AlbumDetail : Screen("album_detail/{albumId}", "Album") {
        fun createRoute(albumId: Long) = "album_detail/$albumId"
    }
    object ArtistDetail : Screen("artist_detail/{artistId}", "Artist") {
        fun createRoute(artistId: Long) = "artist_detail/$artistId"
    }
}
