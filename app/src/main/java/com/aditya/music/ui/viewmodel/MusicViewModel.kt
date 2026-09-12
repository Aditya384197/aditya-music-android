package com.aditya.music.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.aditya.music.data.database.MusicDatabase
import com.aditya.music.data.model.Album
import com.aditya.music.data.model.Artist
import com.aditya.music.data.model.Playlist
import com.aditya.music.data.model.Song
import com.aditya.music.data.repository.MusicRepository
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
        if (granted) {
            refreshLibrary()
        }
    }

    fun refreshLibrary() {
        viewModelScope.launch {
            isScanning.value = true
            try {
                val scanned = repository.scanDeviceSongs()
                allSongs.value = scanned
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
                val currentId = mediaItem?.mediaId?.toLongOrNull()
                currentSong.value = allSongs.value.find { it.id == currentId }
                // A new track just started (tapped, skipped, or auto-advanced) — the elapsed
                // time must restart from zero, not keep showing the previous track's position.
                currentPosition.value = 0L
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
        // Pick up whatever the session already had (e.g. after a config change / re-entering screen).
        currentSong.value = allSongs.value.find { it.id == controller.currentMediaItem?.mediaId?.toLongOrNull() }
        isPlaying.value = controller.isPlaying
        currentPosition.value = controller.currentPosition.coerceAtLeast(0)
        isShuffle.value = controller.shuffleModeEnabled
        syncQueueFromController()
        startPositionTicker()
    }

    /** Keeps [currentPosition] moving every ~300ms while something is actually playing. */
    private fun startPositionTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (true) {
                mediaController?.let { c ->
                    if (c.isPlaying) currentPosition.value = c.currentPosition.coerceAtLeast(0)
                }
                delay(300)
            }
        }
    }

    private fun syncQueueFromController() {
        val controller = mediaController ?: return
        val songMap = allSongs.value.associateBy { it.id }
        val items = (0 until controller.mediaItemCount).mapNotNull { i ->
            songMap[controller.getMediaItemAt(i).mediaId.toLongOrNull()]
        }
        playbackQueue.value = items
    }

    /** Plays [song] as a standalone track with no queue context (e.g. a single search result). */
    fun playSong(song: Song) = playSongs(listOf(song), 0)

    /**
     * Sets the whole [context] list as the active playback queue and starts playing the item
     * at [startIndex]. Passing the real surrounding list (not just one song) is what makes the
     * next/previous buttons and auto-advance actually work.
     */
    fun playSongs(context: List<Song>, startIndex: Int) {
        if (context.isEmpty()) return
        val safeIndex = startIndex.coerceIn(context.indices)
        val target = context[safeIndex]

        currentSong.value = target
        currentPosition.value = 0L
        viewModelScope.launch { repository.recordHistory(target.id) }

        mediaController?.let { controller ->
            val mediaItems = context.map { s ->
                MediaItem.Builder()
                    .setMediaId(s.id.toString())
                    .setUri(s.contentUri)
                    .build()
            }
            controller.setMediaItems(mediaItems, safeIndex, 0L)
            controller.prepare()
            controller.play()
        }
    }

    /** Jumps to a specific position inside the queue that's already loaded on the player. */
    fun playQueueIndex(index: Int) {
        mediaController?.let { c ->
            if (index in 0 until c.mediaItemCount) {
                c.seekTo(index, 0L)
                c.play()
            }
        }
    }

    fun togglePlayPause() {
        mediaController?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    fun playNext() {
        mediaController?.seekToNextMediaItem()
    }

    fun playPrevious() {
        mediaController?.let { c ->
            // Standard player UX: restart the current track if we're more than 3s in,
            // otherwise go to the actual previous track.
            if (c.currentPosition > 3000) c.seekTo(0L) else c.seekToPreviousMediaItem()
        }
    }

    fun seekTo(positionMs: Long) {
        currentPosition.value = positionMs
        mediaController?.seekTo(positionMs)
    }

    fun toggleShuffle() {
        mediaController?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    fun toggleRepeat() {
        mediaController?.let { c ->
            c.repeatMode = when (c.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    fun toggleFavorite(songId: Long) {
        viewModelScope.launch {
            repository.toggleFavorite(songId)
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            repository.createPlaylist(name)
        }
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setTheme(theme: String) {
        themeMode.value = theme
    }

    fun clearQueue() {
        val c = mediaController ?: return
        val keepIndex = c.currentMediaItemIndex
        for (i in c.mediaItemCount - 1 downTo 0) {
            if (i != keepIndex) c.removeMediaItem(i)
        }
    }
}
