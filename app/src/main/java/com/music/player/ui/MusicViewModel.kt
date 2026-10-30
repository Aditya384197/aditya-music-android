package com.music.player.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.music.player.data.MusicRepository
import com.music.player.data.Song
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MusicRepository(application)
    private val preferences = application.getSharedPreferences("music_preferences", Context.MODE_PRIVATE)

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _favoriteIds = MutableStateFlow(
        preferences.getStringSet(KEY_FAVORITES, emptySet())
            .orEmpty()
            .mapNotNull(String::toLongOrNull)
            .toSet()
    )
    val favoriteIds: StateFlow<Set<Long>> = _favoriteIds.asStateFlow()

    private val _hasAudioPermission = MutableStateFlow(false)
    val hasAudioPermission: StateFlow<Boolean> = _hasAudioPermission.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _controllerReady = MutableStateFlow(false)
    val controllerReady: StateFlow<Boolean> = _controllerReady.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var controller: MediaController? = null
    private var playerListener: Player.Listener? = null
    private var positionJob: Job? = null

    fun onAudioPermissionResult(granted: Boolean) {
        _hasAudioPermission.value = granted
        if (granted) refreshLibrary()
    }

    fun refreshLibrary() {
        if (!_hasAudioPermission.value || _isScanning.value) return
        viewModelScope.launch {
            _isScanning.value = true
            try {
                _songs.value = repository.scanSongs()
                syncSongFromController()
            } catch (_: SecurityException) {
                _hasAudioPermission.value = false
                _errorMessage.value = "Music को गानों की अनुमति नहीं मिली। अनुमति दो और फिर से कोशिश करो।"
            } catch (error: Exception) {
                _errorMessage.value = "गानों की सूची पढ़ी नहीं जा सकी। फिर से स्कैन करो।"
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun attachController(newController: MediaController) {
        if (controller === newController && _controllerReady.value) return
        controller?.let { old -> playerListener?.let(old::removeListener) }
        controller = newController

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _isBuffering.value = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_ENDED) _isPlaying.value = false
                updatePositionAndDuration(newController)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                _currentSong.value = songFromMediaItem(mediaItem)
                updatePositionAndDuration(newController)
            }

            override fun onPlayerError(error: PlaybackException) {
                _isPlaying.value = false
                _isBuffering.value = false
                _errorMessage.value = "यह ऑडियो फ़ाइल नहीं चल सकी। दूसरी फ़ाइल चलाकर देखो। (${error.errorCodeName})"
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _shuffleEnabled.value = shuffleModeEnabled
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _repeatMode.value = repeatMode
            }
        }
        playerListener = listener
        newController.addListener(listener)
        _controllerReady.value = true
        _isPlaying.value = newController.isPlaying
        _shuffleEnabled.value = newController.shuffleModeEnabled
        _repeatMode.value = newController.repeatMode
        syncSongFromController()
        updatePositionAndDuration(newController)

        positionJob?.cancel()
        positionJob = viewModelScope.launch {
            while (isActive) {
                controller?.let(::updatePositionAndDuration)
                delay(if (_isPlaying.value) 350L else 900L)
            }
        }
    }

    fun detachController() {
        controller?.let { current -> playerListener?.let(current::removeListener) }
        controller = null
        playerListener = null
        positionJob?.cancel()
        positionJob = null
        _controllerReady.value = false
    }

    fun playSongs(queue: List<Song>, startIndex: Int) {
        if (queue.isEmpty()) return
        val safeIndex = startIndex.coerceIn(queue.indices)
        val player = controller
        if (player == null || !_controllerReady.value) {
            _errorMessage.value = "प्लेयर तैयार हो रहा है। एक क्षण बाद फिर कोशिश करो।"
            return
        }

        val song = queue[safeIndex]
        try {
            val mediaItems = queue.map(::toMediaItem)
            _errorMessage.value = null
            _currentSong.value = song
            _currentPosition.value = 0L
            player.setMediaItems(mediaItems, safeIndex, 0L)
            player.prepare()
            player.play()
        } catch (_: Exception) {
            _isPlaying.value = false
            _errorMessage.value = "गाना शुरू नहीं हो पाया। यह फ़ाइल हटाई गई हो सकती है या इसकी अनुमति उपलब्ध नहीं है।"
        }
    }

    fun togglePlayPause() {
        val player = controller ?: return
        try {
            if (player.isPlaying) {
                player.pause()
            } else {
                if (player.playbackState == Player.STATE_IDLE) player.prepare()
                if (player.playbackState == Player.STATE_ENDED) player.seekTo(0L)
                player.play()
            }
        } catch (_: Exception) {
            _errorMessage.value = "प्लेबैक नियंत्रण काम नहीं कर पाया।"
        }
    }

    fun skipNext() {
        runPlayerAction { it.seekToNextMediaItem() }
    }

    fun skipPrevious() {
        val player = controller ?: return
        try {
            if (player.currentPosition > 3_000L) player.seekTo(0L) else player.seekToPreviousMediaItem()
        } catch (_: Exception) {
            _errorMessage.value = "पिछला गाना नहीं चलाया जा सका।"
        }
    }

    fun seekTo(positionMs: Long) {
        val safePosition = positionMs.coerceAtLeast(0L)
        try {
            controller?.seekTo(safePosition)
            _currentPosition.value = safePosition
        } catch (_: Exception) {
            _errorMessage.value = "गाने की स्थिति बदल नहीं पाई।"
        }
    }

    fun toggleShuffle() {
        runPlayerAction { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    fun cycleRepeatMode() {
        val player = controller ?: return
        try {
            player.repeatMode = when (player.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        } catch (_: Exception) {
            _errorMessage.value = "Repeat नियंत्रण उपलब्ध नहीं है।"
        }
    }

    fun toggleFavorite(songId: Long) {
        val updated = _favoriteIds.value.toMutableSet().apply {
            if (!add(songId)) remove(songId)
        }.toSet()
        _favoriteIds.value = updated
        preferences.edit().putStringSet(KEY_FAVORITES, updated.map(Long::toString).toSet()).apply()
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun reportError(message: String) {
        _errorMessage.value = message
    }

    private fun runPlayerAction(action: (MediaController) -> Unit) {
        val player = controller ?: return
        try {
            action(player)
        } catch (_: Exception) {
            _errorMessage.value = "प्लेयर नियंत्रण पूरा नहीं हो पाया।"
        }
    }

    private fun toMediaItem(song: Song): MediaItem = MediaItem.Builder()
        .setMediaId(song.id.toString())
        .setUri(song.contentUri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setAlbumTitle(song.album)
                .setArtworkUri(song.artworkUri)
                .build()
        )
        .build()

    private fun songFromMediaItem(item: MediaItem?): Song? {
        val id = item?.mediaId?.toLongOrNull() ?: return null
        return _songs.value.firstOrNull { it.id == id }
    }

    private fun syncSongFromController() {
        val player = controller ?: return
        _currentSong.value = songFromMediaItem(player.currentMediaItem)
        updatePositionAndDuration(player)
    }

    private fun updatePositionAndDuration(player: Player) {
        _currentPosition.value = player.currentPosition.coerceAtLeast(0L)
        val trackDuration = player.duration
        _duration.value = if (trackDuration == C.TIME_UNSET || trackDuration < 0L) 0L else trackDuration
        _isPlaying.value = player.isPlaying
    }

    override fun onCleared() {
        detachController()
        super.onCleared()
    }

    companion object {
        private const val KEY_FAVORITES = "favorite_track_ids"
    }
}
