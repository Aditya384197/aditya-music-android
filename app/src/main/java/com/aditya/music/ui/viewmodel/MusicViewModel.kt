package com.aditya.music.ui.viewmodel

import android.app.Application
import android.content.ContentObserver
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.aditya.music.data.database.MusicDatabase
import com.aditya.music.data.headset.HeadsetProfile
import com.aditya.music.data.headset.HeadsetProfileStore
import com.aditya.music.data.model.Album
import com.aditya.music.data.model.Artist
import com.aditya.music.data.model.Playlist
import com.aditya.music.data.model.Song
import com.aditya.music.data.repository.MusicRepository
import com.aditya.music.media.player.EqualizerController
import com.aditya.music.media.player.EqualizerState
import kotlinx.coroutines.Dispatchers
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
    private var sleepTimerJob: Job? = null
    private var libraryRefreshJob: Job? = null
    private val _sleepTimerRemainingMs = MutableStateFlow(0L)
    val sleepTimerRemainingMs: StateFlow<Long> = _sleepTimerRemainingMs.asStateFlow()

    private fun songFromMediaItem(mediaItem: MediaItem?): Song? {
        if (mediaItem == null) return null
        val id = mediaItem.mediaId.toLongOrNull() ?: return null
        allSongs.value.find { it.id == id }?.let { return it }
        val md = mediaItem.mediaMetadata
        return Song(
            id = id,
            title = md.title?.toString()?.takeIf { it.isNotBlank() } ?: "Unknown Title",
            artist = md.artist?.toString()?.takeIf { it.isNotBlank() } ?: "Unknown Artist",
            album = md.albumTitle?.toString()?.takeIf { it.isNotBlank() } ?: "Unknown Album",
            albumId = -1L, duration = 0L,
            contentUri = mediaItem.localConfiguration?.uri ?: Uri.EMPTY,
            albumArtUri = md.artworkUri, dateAdded = 0L, size = 0L
        )
    }

    private fun mediaItemFor(song: Song): MediaItem = MediaItem.Builder()
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
    val themeMode = MutableStateFlow(
        application.getSharedPreferences("aditya_music_preferences", Application.MODE_PRIVATE)
            .getString("theme_mode", "dark")
            ?: "dark"
    )

    val equalizerState: StateFlow<EqualizerState> = EqualizerController.state

    private val headsetPrefs = application.getSharedPreferences("aditya_music_headset", Application.MODE_PRIVATE)
    private val _headsetProfiles = MutableStateFlow<List<HeadsetProfile>>(emptyList())
    val headsetProfiles: StateFlow<List<HeadsetProfile>> = _headsetProfiles.asStateFlow()
    private val _selectedHeadsetProfile = MutableStateFlow<HeadsetProfile?>(null)
    val selectedHeadsetProfile: StateFlow<HeadsetProfile?> = _selectedHeadsetProfile.asStateFlow()
    private val _selectedHeadsetType = MutableStateFlow<String?>(headsetPrefs.getString("type", null))
    val selectedHeadsetType: StateFlow<String?> = _selectedHeadsetType.asStateFlow()

    private val mediaStoreObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            if (!hasPermission.value) return
            libraryRefreshJob?.cancel()
            libraryRefreshJob = viewModelScope.launch {
                delay(450L)
                refreshLibrary()
            }
        }
    }

    private fun audioCollectionUri(): Uri =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _headsetProfiles.value = HeadsetProfileStore.load(application)
            val saved = headsetPrefs.getString("profile", null)
            if (!saved.isNullOrBlank()) {
                runCatching { org.json.JSONObject(saved) }
                    .getOrNull()
                    ?.let { HeadsetProfile.fromJson(it) }
                    ?.let { _selectedHeadsetProfile.value = it }
            }
        }

        // Keep the library current when another app/file manager downloads or copies music.
        // Changes are debounced so a multi-file download triggers one lightweight rescan.
        getApplication<Application>().contentResolver.registerContentObserver(
            audioCollectionUri(),
            true,
            mediaStoreObserver
        )
    }

    private val _openNowPlayingEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val openNowPlayingEvents = _openNowPlayingEvents.asSharedFlow()

    val playlists: StateFlow<List<Playlist>> = repository.getPlaylists()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val filteredSongs: StateFlow<List<Song>> = combine(
        allSongs, searchQuery
    ) { songs, query ->
        val base = if (query.isBlank()) songs
        else songs.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true) ||
                it.album.contains(query, ignoreCase = true)
        }
        // Library order is download/import order: newest MediaStore additions first.
        // Playback history must never reorder the main song library.
        base.sortedWith(
            compareByDescending<Song> { it.dateAdded }
                .thenBy { it.title.lowercase() }
        )
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
                delay(500)
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
            val mediaItems = context.map(::mediaItemFor)
            controller.setMediaItems(mediaItems, safeIndex, 0L)
            controller.prepare()
            controller.play()
        }

        viewModelScope.launch { repository.recordHistory(target.id) }
        _openNowPlayingEvents.tryEmit(Unit)
    }

    /** Insert a song immediately after the currently playing item. */
    fun playNextSong(song: Song) {
        val controller = mediaController ?: return
        val insertAt = (controller.currentMediaItemIndex + 1).coerceAtMost(controller.mediaItemCount)
        controller.addMediaItem(insertAt, mediaItemFor(song))
        syncQueueFromController()
    }

    /** Add a song to the end of the current queue. */
    fun enqueueSong(song: Song) {
        mediaController?.addMediaItem(mediaItemFor(song))
        syncQueueFromController()
    }

    /** Seek relative to the current position; positive = forward, negative = backward. */
    fun seekBy(deltaMs: Long) {
        val controller = mediaController ?: return
        val duration = controller.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
        val target = (controller.currentPosition + deltaMs).coerceIn(0L, duration)
        controller.seekTo(target)
        currentPosition.value = target
    }

    fun startSleepTimer(minutes: Int) {
        val safeMinutes = minutes.coerceIn(1, 180)
        sleepTimerJob?.cancel()
        _sleepTimerRemainingMs.value = safeMinutes * 60_000L
        sleepTimerJob = viewModelScope.launch {
            while (_sleepTimerRemainingMs.value > 0L) {
                delay(1_000L)
                _sleepTimerRemainingMs.update { (it - 1_000L).coerceAtLeast(0L) }
            }
            mediaController?.pause()
            sleepTimerJob = null
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerRemainingMs.value = 0L
    }

    fun addSongToPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch {
            val existing = repository.getSongsForPlaylist(playlistId).first()
            if (songId !in existing) {
                repository.addSongToPlaylist(playlistId, songId, existing.size)
            }
        }
    }

    fun createPlaylistAndAddSong(name: String, songId: Long) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return
        viewModelScope.launch {
            val id = repository.createPlaylist(cleanName)
            repository.addSongToPlaylist(id, songId, 0)
        }
    }

    fun removeQueueItem(index: Int) {
        val controller = mediaController ?: return
        if (index in 0 until controller.mediaItemCount && index != controller.currentMediaItemIndex) {
            controller.removeMediaItem(index)
            syncQueueFromController()
        }
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
                if (controller.playbackState == Player.STATE_IDLE) controller.prepare()
                controller.play()
            }
        }
    }

    fun playNext() = mediaController?.seekToNextMediaItem()

    /** Previous-track action used by the artwork swipe gesture; unlike the button behaviour,
     * it always asks the session for the previous queue item rather than restarting the current song. */
    fun playPreviousTrack() = mediaController?.seekToPreviousMediaItem()

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
        val safeTheme = theme.takeIf { it == "dark" || it == "light" || it == "amoled" } ?: "dark"
        themeMode.value = safeTheme
        getApplication<Application>()
            .getSharedPreferences("aditya_music_preferences", Application.MODE_PRIVATE)
            .edit()
            .putString("theme_mode", safeTheme)
            .apply()
    }

    fun setEqualizerEnabled(enabled: Boolean) = EqualizerController.setEnabled(enabled)

    fun applyEqualizerPreset(preset: String) = EqualizerController.applyPreset(preset)

    fun setEqualizerBassDb(db: Float) = EqualizerController.setBassDb(db)

    fun setEqualizerMidDb(db: Float) = EqualizerController.setMidDb(db)

    fun setEqualizerTrebleDb(db: Float) = EqualizerController.setTrebleDb(db)

    fun setEqualizerBand(index: Int, levelMb: Int) = EqualizerController.setBandLevel(index, levelMb)

    fun resetEqualizer() = EqualizerController.reset()

    fun saveHeadsetType(type: String) {
        _selectedHeadsetType.value = type
        headsetPrefs.edit().putString("type", type).apply()
    }

    fun saveHeadsetProfile(profile: HeadsetProfile) {
        _selectedHeadsetProfile.value = profile
        headsetPrefs.edit()
            .putString("profile", profile.toJson().toString())
            .putBoolean("apply_pending", true)
            .apply()
        EqualizerController.applyHeadsetProfile(profile)
        headsetPrefs.edit().putBoolean("apply_pending", false).apply()
    }

    fun clearHeadsetProfile() {
        _selectedHeadsetProfile.value = null
        _selectedHeadsetType.value = null
        headsetPrefs.edit().remove("profile").remove("type").remove("apply_pending").apply()
        EqualizerController.reset()
    }

    fun dismissPlayer() {
        mediaController?.clearMediaItems()
        currentSong.value = null
        currentPosition.value = 0L
        isPlaying.value = false
        playbackQueue.value = emptyList()
    }

    fun clearQueue() {
        val controller = mediaController ?: return
        val keepIndex = controller.currentMediaItemIndex
        for (i in controller.mediaItemCount - 1 downTo 0) {
            if (i != keepIndex) controller.removeMediaItem(i)
        }
        syncQueueFromController()
    }
    private val _deletePermissionRequest = MutableSharedFlow<android.content.IntentSender>(extraBufferCapacity = 1)
    val deletePermissionRequest = _deletePermissionRequest.asSharedFlow()

    private val _deleteResultEvents = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val deleteResultEvents = _deleteResultEvents.asSharedFlow()

    /** Called after the user confirms deletion in the system permission dialog. */
    fun onDeletePermissionGranted() {
        refreshLibrary()
        _deleteResultEvents.tryEmit(true)
    }

    /** Called if the user backs out of the system permission dialog instead of confirming. */
    fun onDeletePermissionDenied() {
        _deleteResultEvents.tryEmit(false)
    }

    fun shareSong(song: Song, context: android.content.Context) = shareSongs(listOf(song), context)

    fun shareSongs(songs: List<Song>, context: android.content.Context) {
        if (songs.isEmpty()) return
        val shareIntent = if (songs.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, songs[0].contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "audio/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(songs.map { it.contentUri }))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        val label = if (songs.size == 1) "Share \"${songs[0].title}\"" else "Share ${songs.size} songs"
        context.startActivity(Intent.createChooser(shareIntent, label))
    }

    fun deleteSong(song: Song) = deleteSongs(listOf(song))

    /**
     * Deletes one or more songs. If Android needs the user to confirm first, this asks for
     * everything in [songs] via a single system dialog rather than prompting once per song -
     * see [deletePermissionRequest].
     */
    fun deleteSongs(songs: List<Song>) {
        if (songs.isEmpty()) return
        viewModelScope.launch {
            when (val result = repository.deleteSongs(songs)) {
                is MusicRepository.DeleteResult.Success -> {
                    val deletedIds = songs.map { it.id }.toSet()
                    allSongs.update { list -> list.filterNot { it.id in deletedIds } }
                    _deleteResultEvents.tryEmit(true)
                }
                is MusicRepository.DeleteResult.NeedsPermission -> {
                    _deletePermissionRequest.tryEmit(result.intentSender)
                }
                MusicRepository.DeleteResult.Failure -> {
                    _deleteResultEvents.tryEmit(false)
                }
            }
        }
    }

    override fun onCleared() {
        libraryRefreshJob?.cancel()
        tickerJob?.cancel()
        sleepTimerJob?.cancel()
        runCatching {
            getApplication<Application>().contentResolver.unregisterContentObserver(mediaStoreObserver)
        }
        mediaController?.let { controller ->
            runCatching { controller.release() }
        }
        mediaController = null
        super.onCleared()
    }
}
