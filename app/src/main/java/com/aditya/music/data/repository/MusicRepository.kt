package com.aditya.music.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.aditya.music.data.database.MusicDatabase
import com.aditya.music.data.database.entity.FavoriteEntity
import com.aditya.music.data.database.entity.HistoryEntity
import com.aditya.music.data.database.entity.PlaylistEntity
import com.aditya.music.data.database.entity.PlaylistSongCrossRef
import com.aditya.music.data.model.Album
import com.aditya.music.data.model.Artist
import com.aditya.music.data.model.Playlist
import com.aditya.music.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class MusicRepository(
    private val context: Context,
    private val database: MusicDatabase
) {
    private val contentResolver = context.contentResolver
    private val playlistDao = database.playlistDao()
    private val favoriteDao = database.favoriteDao()
    private val historyDao = database.historyDao()

    val favoriteSongIds: Flow<List<Long>> = favoriteDao.getAllFavoriteSongIds()
    val recentlyPlayedIds: Flow<List<Long>> = historyDao.getRecentlyPlayedIds()
    val songIdsByPlayCount: Flow<List<Long>> = historyDao.getSongIdsByPlayCount()

    /**
     * Efficiently scans the Android MediaStore for local audio files (MP3, WAV, FLAC, AAC, M4A, OGG).
     * Filters out ringtones, alarms, and tracks shorter than 10 seconds.
     */
    suspend fun scanDeviceSongs(): List<Song> = withContext(Dispatchers.IO) {
        val songList = mutableListOf<Song>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.SIZE
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 10000"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        try {
            contentResolver.query(collection, projection, selection, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)

                val artworkUriBase = Uri.parse("content://media/external/audio/albumart")

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol) ?: "Unknown Title"
                    val artist = cursor.getString(artistCol) ?: "Unknown Artist"
                    val album = cursor.getString(albumCol) ?: "Unknown Album"
                    val albumId = cursor.getLong(albumIdCol)
                    val duration = cursor.getLong(durationCol)
                    val dateAdded = cursor.getLong(dateAddedCol)
                    val size = cursor.getLong(sizeCol)

                    val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    val albumArtUri = ContentUris.withAppendedId(artworkUriBase, albumId)

                    songList.add(
                        Song(
                            id = id,
                            title = title,
                            artist = if (artist == "<unknown>") "Unknown Artist" else artist,
                            album = if (album == "<unknown>") "Unknown Album" else album,
                            albumId = albumId,
                            duration = duration,
                            contentUri = contentUri,
                            albumArtUri = albumArtUri,
                            dateAdded = dateAdded,
                            size = size
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        songList
    }

    suspend fun toggleFavorite(songId: Long) = withContext(Dispatchers.IO) {
        val isFav = favoriteDao.isFavorite(songId)
        if (isFav) {
            favoriteDao.removeFavorite(songId)
        } else {
            favoriteDao.addFavorite(FavoriteEntity(songId = songId))
        }
    }

    suspend fun recordHistory(songId: Long) = withContext(Dispatchers.IO) {
        historyDao.recordPlay(HistoryEntity(songId = songId))
    }

    fun getPlaylists(): Flow<List<Playlist>> = playlistDao.getAllPlaylists().map { entities ->
        entities.map { Playlist(id = it.id, name = it.name, createdAt = it.createdAt) }
    }

    suspend fun createPlaylist(name: String): Long = withContext(Dispatchers.IO) {
        playlistDao.insertPlaylist(PlaylistEntity(name = name))
    }

    suspend fun renamePlaylist(id: Long, newName: String) = withContext(Dispatchers.IO) {
        playlistDao.updatePlaylistName(id, newName)
    }

    suspend fun deletePlaylist(id: Long) = withContext(Dispatchers.IO) {
        playlistDao.deletePlaylist(id)
    }

    suspend fun addSongToPlaylist(playlistId: Long, songId: Long, position: Int = 0) = withContext(Dispatchers.IO) {
        playlistDao.addSongToPlaylist(PlaylistSongCrossRef(playlistId, songId, position))
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) = withContext(Dispatchers.IO) {
        playlistDao.removeSongFromPlaylist(playlistId, songId)
    }

    fun getSongsForPlaylist(playlistId: Long): Flow<List<Long>> = playlistDao.getSongIdsForPlaylist(playlistId)

    /**
     * Result of a delete attempt. On Android 10+, deleting a MediaStore item that this app didn't
     * create requires the user to confirm a system dialog first - that dialog can't be shown from
     * here, so [NeedsPermission] hands the caller an IntentSender to launch for that confirmation.
     */
    sealed class DeleteResult {
        object Success : DeleteResult()
        data class NeedsPermission(val intentSender: android.content.IntentSender) : DeleteResult()
        object Failure : DeleteResult()
    }

    /**
     * Deletes a song file from the device via MediaStore.
     */
    suspend fun deleteSong(song: Song): DeleteResult = withContext(Dispatchers.IO) {
        try {
            val rows = contentResolver.delete(song.contentUri, null, null)
            if (rows > 0) DeleteResult.Success else DeleteResult.Failure
        } catch (securityException: SecurityException) {
            val intentSender = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    MediaStore.createDeleteRequest(contentResolver, listOf(song.contentUri)).intentSender
                }
                Build.VERSION.SDK_INT == Build.VERSION_CODES.Q &&
                    securityException is android.app.RecoverableSecurityException -> {
                    securityException.userAction.actionIntent.intentSender
                }
                else -> null
            }
            if (intentSender != null) DeleteResult.NeedsPermission(intentSender) else DeleteResult.Failure
        } catch (e: Exception) {
            e.printStackTrace()
            DeleteResult.Failure
        }
    }
}
