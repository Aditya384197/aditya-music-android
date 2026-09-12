package com.aditya.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackManagerTest {

    @Test
    fun queueShuffling_producesValidPermutation() {
        val list = listOf(1L, 2L, 3L, 4L, 5L)
        val shuffled = list.shuffled()

        assertEquals(list.size, shuffled.size)
        assertTrue(shuffled.containsAll(list))
    }
}
