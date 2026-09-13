package com.aditya.music.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.aditya.music.data.database.MusicDatabase
import com.aditya.music.data.model.Album
import com.aditya.music.data.model.Artist
import com.aditya.music.data.model.Playlist
import com.aditya.music.data.model.Song
import com.aditya.music.data.repository.MusicRepository
import com.aditya.music.media.player.EqualizerController
import com.aditya.music.media.player.EqualizerState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MusicRepository(
        application,
        MusicDatabase.getDatabase(application)
    )

    private var mediaController: MediaController? = null
    private var tickerJob: Job? = null

    /**
     * Resolves the [Song] backing the player's current MediaItem.
     *
     * Why this exists: after the OS recreates the service (or while the library is still
     * being scanned after a cold start), the player already has the restored queue loaded,
     * but `allSongs` can still be empty for a few seconds. Previously this returned null and
     * the UI showed "No song loaded" even though a song WAS loaded in ExoPlayer. We now fall
     * back to building a lightweight Song straight from the MediaItem's persisted metadata
     * so Now Playing works instantly; it is upgraded to the full Song once the scan finishes.
     */
    private fun songFromMediaItem(mediaItem: MediaItem?): Song? {
        if (mediaItem == null) return null
        val id = mediaItem.mediaId.toLongOrNull() ?: return null
        allSongs.value.find { it.id == id }?.let { return it }
        val md = mediaItem.mediaMetadata
        val title = md.title?.toString()?.takeIf { it.isNotBlank() } ?: "Unknown Title"
        val artist = md.artist?.toString()?.takeIf { it.isNotBlank() } ?: "Unknown Artist"
        val album = md.albumTitle?.toString()?.takeIf { it.isNotBlank() } ?: "Unknown Album"
        return Song(
            id = id,
            title = title,
            artist = artist,
            album = album,
            albumId = -1L,
            duration = 0L,
            contentUri = mediaItem.localConfiguration?.uri ?: Uri.EMPTY,
            albumArtUri = md.artworkUri,
            dateAdded = 0L,
            size = 0L
        )
    }

    val isScanning = MutableStateFlow(false)
    val hasPermission = MutableStateFlow(false)

    val allSongs = MutableStateFlow<List<Song>>(emptyList())
    val searchQuery = MutableStateFlow("")
    val currentSong = MutableStateFlow<Song?>(null)
    val isPlaying = MutableStateFlow(false)
    val currentPosition = MutableStateFlow(0L)
    val playbackQueue = MutableStateFlow<List<Song>>(emptyList())
    val isShuffle = MutableStateFlow(false)
    val repeatMode = MutableStateFlow("off")
    val themeMode = MutableStateFlow("system")

    val equalizerState: StateFlow<EqualizerState> = EqualizerController.state

    private val _openNowPlayingEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val openNowPlayingEvents = _openNowPlayingEvents.asSharedFlow()

    val playlists: StateFlow<List<Playlist>> = repository.getPlaylists()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val filteredSongs: StateFlow<List<Song>> = combine(allSongs, searchQuery) { songs, query ->
        if (query.isBlank()) songs
        else songs.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true) ||
                it.album.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val favoriteSongs: StateFlow<List<Song>> = combine(allSongs, repository.favoriteSongIds) { songs, favIds ->
        val idSet = favIds.toSet()
        songs.filter { idSet.contains(it.id) }.map { it.copy(isFavorite = true) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val recentlyPlayedSongs: StateFlow<List<Song>> = combine(allSongs, repository.recentlyPlayedIds) { songs, recentIds ->
        val songMap = songs.associateBy { it.id }
        recentIds.mapNotNull { songMap[it] }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val albums: StateFlow<List<Album>> = allSongs.map { songs ->
        songs.groupBy { it.albumId }.map { (albumId, albumSongs) ->
            val first = albumSongs.first()
            Album(
                id = albumId,
                name = first.album,
                artist = first.artist,
                artworkUri = first.albumArtUri,
                songCount = albumSongs.size,
                songs = albumSongs
            )
        }.sortedBy { it.name }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val artists: StateFlow<List<Artist>> = allSongs.map { songs ->
        songs.groupBy { it.artist }.map { (artistName, artistSongs) ->
            Artist(
                id = artistName.hashCode().toLong(),
                name = artistName,
                songCount = artistSongs.size,
                albumCount = artistSongs.map { it.albumId }.distinct().size,
                songs = artistSongs
            )
        }.sortedBy { it.name }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun onPermissionResult(granted: Boolean) {
        hasPermission.value = granted
        if (granted) refreshLibrary()
    }

    fun refreshLibrary() {
        if (isScanning.value) return
        viewModelScope.launch {
            isScanning.value = true
            try {
                val favorites = repository.favoriteSongIds.first().toSet()
                val scanned = repository.scanDeviceSongs().map { song ->
                    song.copy(isFavorite = song.id in favorites)
                }
                allSongs.value = scanned
                // Re-resolve the active MediaSession item after a scan. The playback service
                // can remain alive while the Activity is recreated or reopened.
                syncCurrentSongFromController()
                syncQueueFromController()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isScanning.value = false
            }
        }
    }

    fun bindMediaController(controller: MediaController) {
        mediaController = controller
        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying.value = playing
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val song = songFromMediaItem(mediaItem)
                currentSong.value = song
                currentPosition.value = 0L
                if (song != null && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    viewModelScope.launch { repository.recordHistory(song.id) }
                }
                syncQueueFromController()
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                syncQueueFromController()
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                isShuffle.value = shuffleModeEnabled
            }

            override fun onRepeatModeChanged(repeatModeInt: Int) {
                repeatMode.value = when (repeatModeInt) {
                    Player.REPEAT_MODE_ALL -> "all"
                    Player.REPEAT_MODE_ONE -> "one"
                    else -> "off"
                }
            }
        })

        syncCurrentSongFromController()
        isPlaying.value = controller.isPlaying
        currentPosition.value = controller.currentPosition.coerceAtLeast(0)
        isShuffle.value = controller.shuffleModeEnabled
        repeatMode.value = when (controller.repeatMode) {
            Player.REPEAT_MODE_ALL -> "all"
            Player.REPEAT_MODE_ONE -> "one"
            else -> "off"
        }
        syncQueueFromController()
        startPositionTicker()
    }

    private fun startPositionTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (true) {
                mediaController?.let { c ->
                    if (c.isPlaying) currentPosition.value = c.currentPosition.coerceAtLeast(0)
                }
                delay(250)
            }
        }
    }

    private fun syncCurrentSongFromController() {
        val controller = mediaController ?: return
        currentSong.value = songFromMediaItem(controller.currentMediaItem)
        currentPosition.value = controller.currentPosition.coerceAtLeast(0L)
    }

    private fun syncQueueFromController() {
        val controller = mediaController ?: return
        val songMap = allSongs.value.associateBy { it.id }
        playbackQueue.value = (0 until controller.mediaItemCount).mapNotNull { i ->
            songMap[controller.getMediaItemAt(i).mediaId.toLongOrNull()]
        }
    }

    fun playSong(song: Song) = playSongs(listOf(song), 0)

    fun playSongs(context: List<Song>, startIndex: Int) {
        if (context.isEmpty()) return
        val safeIndex = startIndex.coerceIn(context.indices)
        val target = context[safeIndex]

        currentSong.value = target
        currentPosition.value = 0L

        mediaController?.let { controller ->
            val mediaItems = context.map { song ->
                MediaItem.Builder()
                    .setMediaId(song.id.toString())
                    .setUri(song.contentUri)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artist)
                            .setAlbumTitle(song.album)
                            .setArtworkUri(song.albumArtUri)
                            .build()
                    )
                    .build()
            }
            controller.setMediaItems(mediaItems, safeIndex, 0L)
            controller.prepare()
            controller.play()
        }

        viewModelScope.launch { repository.recordHistory(target.id) }
        _openNowPlayingEvents.tryEmit(Unit)
    }

    fun playQueueIndex(index: Int) {
        mediaController?.let { c ->
            if (index in 0 until c.mediaItemCount) {
                c.seekTo(index, 0L)
                c.play()
                _openNowPlayingEvents.tryEmit(Unit)
            }
        }
    }

    fun togglePlayPause() {
        mediaController?.let { controller ->
            if (controller.isPlaying) {
                controller.pause()
            } else {
                // After the service is recreated, the player can be in STATE_IDLE even though
                // the queue was restored; calling play() alone does nothing in that state.
                if (controller.playbackState == Player.STATE_IDLE) {
                    controller.prepare()
                }
                controller.play()
            }
        }
    }

    fun playNext() = mediaController?.seekToNextMediaItem()

    fun playPrevious() {
        mediaController?.let { controller ->
            if (controller.currentPosition > 3000) controller.seekTo(0L)
            else controller.seekToPreviousMediaItem()
        }
    }

    fun seekTo(positionMs: Long) {
        currentPosition.value = positionMs.coerceAtLeast(0L)
        mediaController?.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun toggleShuffle() {
        mediaController?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    fun toggleRepeat() {
        mediaController?.let { controller ->
            controller.repeatMode = when (controller.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    fun toggleFavorite(songId: Long) {
        viewModelScope.launch {
            repository.toggleFavorite(songId)
            allSongs.update { songs ->
                songs.map { song ->
                    if (song.id == songId) song.copy(isFavorite = !song.isFavorite) else song
                }
            }
        }
    }

    fun createPlaylist(name: String) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return
        viewModelScope.launch { repository.createPlaylist(cleanName) }
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setTheme(theme: String) {
        themeMode.value = theme
    }

    fun setEqualizerEnabled(enabled: Boolean) = EqualizerController.setEnabled(enabled)

    fun applyEqualizerPreset(preset: String) = EqualizerController.applyPreset(preset)

    fun setEqualizerBass(strength: Int) = EqualizerController.setBassStrength(strength)

    fun setEqualizerClarity(strength: Int) = EqualizerController.setClarityStrength(strength)

    fun setEqualizerRoom(room: String) = EqualizerController.setRoom(room)

    fun setEqualizerBand(index: Int, levelMb: Int) = EqualizerController.setBandLevel(index, levelMb)

    fun clearQueue() {
        val controller = mediaController ?: return
        val keepIndex = controller.currentMediaItemIndex
        for (i in controller.mediaItemCount - 1 downTo 0) {
            if (i != keepIndex) controller.removeMediaItem(i)
        }
        syncQueueFromController()
    }
}
