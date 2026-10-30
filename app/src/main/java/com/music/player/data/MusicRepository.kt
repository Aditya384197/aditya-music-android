package com.music.player.data

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the on-device audio library through MediaStore. All provider work runs on Dispatchers.IO.
 * Persisted content URIs are used for playback; no broad filesystem access is required.
 */
class MusicRepository(context: Context) {
    private val resolver = context.applicationContext.contentResolver

    suspend fun scanSongs(): List<Song> = withContext(Dispatchers.IO) {
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
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        val artworkBase = android.net.Uri.parse("content://media/external/audio/albumart")
        val output = ArrayList<Song>()

        try {
            resolver.query(collection, projection, selection, null, sortOrder)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val albumId = cursor.getLong(albumIdColumn)
                    val title = cursor.getString(titleColumn)?.trim().orEmpty().ifBlank { "Unknown title" }
                    val rawArtist = cursor.getString(artistColumn)?.trim().orEmpty()
                    val rawAlbum = cursor.getString(albumColumn)?.trim().orEmpty()
                    val artist = rawArtist.takeUnless { it.isBlank() || it == "<unknown>" } ?: "Unknown artist"
                    val album = rawAlbum.takeUnless { it.isBlank() || it == "<unknown>" } ?: "Unknown album"
                    val contentUri = ContentUris.withAppendedId(collection, id)
                    val artUri = if (albumId > 0L) ContentUris.withAppendedId(artworkBase, albumId) else null

                    output += Song(
                        id = id,
                        title = title,
                        artist = artist,
                        album = album,
                        albumId = albumId,
                        durationMs = cursor.getLong(durationColumn).coerceAtLeast(0L),
                        contentUri = contentUri,
                        artworkUri = artUri,
                        sizeBytes = cursor.getLong(sizeColumn).coerceAtLeast(0L),
                        dateAddedSeconds = cursor.getLong(dateAddedColumn).coerceAtLeast(0L)
                    )
                }
            }
        } catch (security: SecurityException) {
            throw security
        }

        output
    }
}
