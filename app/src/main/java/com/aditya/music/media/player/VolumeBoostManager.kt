package com.aditya.music.media.player

import android.content.Context
import android.media.audiofx.LoudnessEnhancer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.log10
import kotlin.math.roundToInt

/**
 * In-app pre-volume control. 100% is unity gain; values above 100% use Android's
 * LoudnessEnhancer on the active media audio session. LoudnessEnhancer compresses
 * samples that would exceed the platform sample range, so the boost is safer than
 * simply multiplying PCM samples and hard-clipping them.
 */
data class VolumeState(
    val percent: Int = 100,
    val supported: Boolean = true
)

object VolumeController {
    private const val PREFS = "aditya_music_volume"
    private const val KEY_PERCENT = "percent"
    const val MIN_PERCENT = 0
    const val MAX_PERCENT = 300
    const val UNITY_PERCENT = 100

    private val _state = MutableStateFlow(VolumeState())
    val state: StateFlow<VolumeState> = _state.asStateFlow()

    private var initialized = false
    private var enhancer: LoudnessEnhancer? = null
    private var attachedSessionId = 0

    fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        val saved = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_PERCENT, UNITY_PERCENT)
            .coerceIn(MIN_PERCENT, MAX_PERCENT)
        _state.value = VolumeState(saved)
    }

    fun attach(audioSessionId: Int) {
        if (audioSessionId <= 0 || audioSessionId == attachedSessionId) return
        releaseEnhancer()
        attachedSessionId = audioSessionId
        try {
            enhancer = LoudnessEnhancer(audioSessionId)
            _state.value = _state.value.copy(supported = true)
            applyEnhancerGain(_state.value.percent)
        } catch (_: Throwable) {
            enhancer = null
            _state.value = _state.value.copy(supported = false)
        }
    }

    fun setPercent(context: Context, percent: Int) {
        initialize(context)
        val safe = percent.coerceIn(MIN_PERCENT, MAX_PERCENT)
        _state.value = _state.value.copy(percent = safe)
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_PERCENT, safe)
            .apply()
        applyEnhancerGain(safe)
    }

    /** Player-side volume component: 0..1 for the normal Android player volume. */
    fun playerVolume(): Float = (_state.value.percent.coerceAtMost(UNITY_PERCENT) / 100f)

    private fun applyEnhancerGain(percent: Int) {
        val gainMb = if (percent > UNITY_PERCENT) {
            (20.0 * log10(percent / 100.0) * 100.0).roundToInt().coerceAtMost(954)
        } else 0
        try {
            enhancer?.setTargetGain(gainMb)
            enhancer?.enabled = gainMb > 0
        } catch (_: Throwable) {
        }
    }

    fun release() {
        releaseEnhancer()
        attachedSessionId = 0
    }

    private fun releaseEnhancer() {
        try { enhancer?.enabled = false } catch (_: Throwable) {}
        try { enhancer?.release() } catch (_: Throwable) {}
        enhancer = null
    }
}
