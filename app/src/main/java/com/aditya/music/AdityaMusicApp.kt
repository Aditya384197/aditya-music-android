package com.aditya.music

import android.app.Application
import com.aditya.music.data.database.MusicDatabase

/**
 * Main Application Class for Aditya Music.
 * Initializes the Room database and repository singletons for local, offline-first performance.
 */
class AdityaMusicApp : Application() {

    val database: MusicDatabase by lazy {
        MusicDatabase.getDatabase(this)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: AdityaMusicApp
            private set
    }
}
