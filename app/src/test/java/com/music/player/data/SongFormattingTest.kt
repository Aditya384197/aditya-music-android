package com.music.player.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SongFormattingTest {
    @Test
    fun formatsMinuteAndSecondDurations() {
        assertEquals("0:00", formatDuration(0L))
        assertEquals("0:09", formatDuration(9_000L))
        assertEquals("3:07", formatDuration(187_000L))
    }

    @Test
    fun formatsHourLongDurations() {
        assertEquals("1:02:03", formatDuration(3_723_000L))
    }
}
