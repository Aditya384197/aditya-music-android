package com.aditya.music.data.database.dao

import androidx.room.*
import com.aditya.music.data.database.entity.*
import kotlinx.coroutines.flow.Flow

/**
 * Lightweight query-result POJO for per-song play counts.
 * Not a Room entity — only used to carry COUNT(*) results out of the DAO.
 */
data class SongPlayCount(
    val songId: Long,
    val playCount: Int
)

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY name ASC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :newName WHERE id = :id")
    suspend fun updatePlaylistName(id: Long, newName: String)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addSongToPlaylist(crossRef: PlaylistSongCrossRef)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long)

    @Query("SELECT songId FROM playlist_songs WHERE playlistId = :playlistId ORDER BY position ASC")
    fun getSongIdsForPlaylist(playlistId: Long): Flow<List<Long>>
}

@Dao
interface FavoriteDao {
    @Query("SELECT songId FROM favorites ORDER BY addedAt DESC")
    fun getAllFavoriteSongIds(): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE songId = :songId")
    suspend fun removeFavorite(songId: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE songId = :songId)")
    suspend fun isFavorite(songId: Long): Boolean
}

@Dao
interface HistoryDao {
    @Query("SELECT songId FROM playback_history GROUP BY songId ORDER BY MAX(playedAt) DESC LIMIT 20")
    fun getRecentlyPlayedIds(): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun recordPlay(history: HistoryEntity)

    @Query("SELECT songId FROM playback_history GROUP BY songId ORDER BY COUNT(*) DESC LIMIT 50")
    suspend fun getMostPlayedSongIds(): List<Long>

    /**
     * Har gaane ka total play count, real-time Flow ke through.
     * Iska use SongsScreen me list ko "sabse zyada chalne wala gaana pehle"
     * ke hisaab se sort karne ke liye hota hai.
     */
    @Query("SELECT songId, COUNT(*) as playCount FROM playback_history GROUP BY songId")
    fun getAllPlayCounts(): Flow<List<SongPlayCount>>
}
