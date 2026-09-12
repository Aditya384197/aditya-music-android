package com.aditya.music.media.player

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer

/**
 * Real Android hardware AudioFX Equalizer controller.
 * Connects directly to ExoPlayer's audioSessionId.
 */
class EqualizerManager(audioSessionId: Int) {

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null

    init {
        try {
            if (audioSessionId != 0) {
                equalizer = Equalizer(0, audioSessionId).apply {
                    enabled = true
                }
                bassBoost = BassBoost(0, audioSessionId).apply {
                    enabled = true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val numberOfBands: Short
        get() = equalizer?.numberOfBands ?: 5

    val bandLevelRange: ShortArray
        get() = equalizer?.bandLevelRange ?: shortArrayOf(-1500, 1500)

    fun setBandLevel(band: Short, level: Short) {
        try {
            equalizer?.setBandLevel(band, level)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setBassBoostStrength(strength: Short) {
        try {
            bassBoost?.setStrength(strength)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun release() {
        equalizer?.release()
        bassBoost?.release()
        equalizer = null
        bassBoost = null
    }
}
