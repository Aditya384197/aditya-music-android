package com.aditya.music.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.aditya.music.data.database.dao.FavoriteDao
import com.aditya.music.data.database.dao.HistoryDao
import com.aditya.music.data.database.dao.PlaylistDao
import com.aditya.music.data.database.entity.FavoriteEntity
import com.aditya.music.data.database.entity.HistoryEntity
import com.aditya.music.data.database.entity.PlaylistEntity
import com.aditya.music.data.database.entity.PlaylistSongCrossRef

@Database(
    entities = [
        PlaylistEntity::class,
        PlaylistSongCrossRef::class,
        FavoriteEntity::class,
        HistoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MusicDatabase : RoomDatabase() {

    abstract fun playlistDao(): PlaylistDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile
        private var INSTANCE: MusicDatabase? = null

        fun getDatabase(context: Context): MusicDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MusicDatabase::class.java,
                    "aditya_music.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
