package com.aditya.music

import com.aditya.music.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Test

class MusicRepositoryTest {

    @Test
    fun formattedDuration_formatsCorrectly() {
        val song = Song(
            id = 1L,
            title = "Test Audio",
            artist = "Aditya",
            album = "Aditya Beats",
            albumId = 10L,
            duration = 185000L, // 3 mins 5 secs
            contentUri = android.net.Uri.EMPTY,
            albumArtUri = null,
            dateAdded = 1700000000L,
            size = 5000000L
        )

        assertEquals("3:05", song.formattedDuration)
    }
}
