package com.aditya.music.media.player

import android.content.Context
import android.media.audiofx.Equalizer
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Lightweight DJ-style equalizer backed by Android's real audio-session Equalizer effect.
 *
 * Playback stays completely untouched while EQ is bypassed. The system effect is created lazily
 * only when the user opens/uses EQ, keeping normal song-start playback as light and compatible as
 * possible on vendor-specific Android audio stacks.
 */
data class EqualizerState(
    val supported: Boolean = false,
    val enabled: Boolean = false,
    val preset: String = PRESET_FLAT,
    val bandLevelsMb: List<Short> = emptyList(),
    val bandFrequenciesHz: List<Int> = emptyList(),
    val bandLevelMinMb: Int = -1200,
    val bandLevelMaxMb: Int = 1200
)

const val PRESET_FLAT = "Flat"
const val PRESET_POP = "Pop"
const val PRESET_ROCK = "Rock"
const val PRESET_DANCE = "Dance"
const val PRESET_DJ = "DJ"
const val PRESET_VOCAL = "Vocal"
const val PRESET_CUSTOM = "Custom"

private const val PREFS_NAME = "aditya_music_equalizer"
private const val KEY_ENABLED = "enabled"
private const val KEY_PRESET = "preset"
private const val KEY_BANDS = "bands"
private const val KEY_SCHEMA = "schema"
private const val CURRENT_SCHEMA = 10

/** Reference gains for standard DJ/hi-fi curves. Frequencies are in Hz, gains in dB. */
private val PRESET_CURVES_DB = mapOf(
    PRESET_FLAT to floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
    PRESET_POP to floatArrayOf(-1f, 1f, 2f, 1f, -1f, 1f, 2f, 3f, 2f, -1f),
    PRESET_ROCK to floatArrayOf(3f, 3f, 2f, 0f, -1f, 1f, 2f, 3f, 4f, 3f),
    PRESET_DANCE to floatArrayOf(4f, 4f, 2f, 0f, -1f, 0f, 2f, 3f, 4f, 4f),
    PRESET_DJ to floatArrayOf(5f, 4f, 3f, 1f, -1f, 0f, 2f, 4f, 5f, 4f),
    PRESET_VOCAL to floatArrayOf(-2f, -1f, 0f, 2f, 4f, 4f, 3f, 1f, -1f, -2f)
)

private val REFERENCE_FREQUENCIES_HZ = intArrayOf(
    31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000
)

object EqualizerController {
    private var manager: EqualizerManager? = null
    private var appContext: Context? = null
    private var pendingAudioSessionId: Int = 0
    private val _state = MutableStateFlow(EqualizerState())
    val state: StateFlow<EqualizerState> = _state.asStateFlow()

    /**
     * Registers the current audio session without creating an Android audio effect.
     * This is deliberately lightweight because it is called when a song starts.
     */
    fun attachSession(context: Context, audioSessionId: Int) {
        manager?.release()
        manager = null
        appContext = context.applicationContext
        pendingAudioSessionId = audioSessionId.takeIf { it > 0 } ?: 0
        _state.value = EqualizerState()
    }

    fun detach() {
        manager?.release()
        manager = null
        appContext = null
        pendingAudioSessionId = 0
        _state.value = EqualizerState()
    }

    fun initialize() {
        ensureManager()?.initializeIfNeeded()
        sync()
    }

    fun setEnabled(enabled: Boolean) {
        ensureManager()?.setEnabled(enabled)
        sync()
    }

    fun applyPreset(preset: String) {
        ensureManager()?.applyPreset(preset)
        sync()
    }

    fun setBandLevel(index: Int, levelMb: Int) {
        ensureManager()?.setBandLevel(index, levelMb)
        sync()
    }

    fun reset() {
        ensureManager()?.reset()
        sync()
    }

    private fun ensureManager(): EqualizerManager? {
        manager?.let { return it }
        val context = appContext ?: return null
        if (pendingAudioSessionId <= 0) return null

        return runCatching {
            EqualizerManager(context, pendingAudioSessionId)
        }.getOrNull()?.also { manager = it }
    }

    private fun sync() {
        manager?.let { _state.value = it.snapshot() }
    }
}

class EqualizerManager(
    context: Context,
    private val audioSessionId: Int,
    @VisibleForTesting private val persistenceEnabled: Boolean = true
) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var equalizer: Equalizer? = null
    private var effectInitialized = false
    private var supported = audioSessionId > 0
    private var enabled = false
    private var preset = PRESET_FLAT
    private var savedBands: List<Int> = emptyList()
    private var bandLevelMinMbInternal = -1200
    private var bandLevelMaxMbInternal = 1200

    private val bandFrequenciesHzInternal = mutableListOf<Int>()
    private val bandLevelsMbInternal = mutableListOf<Short>()

    init {
        readPersistedState()
    }

    fun initializeIfNeeded() {
        if (effectInitialized || audioSessionId <= 0) return

        try {
            val effect = Equalizer(0, audioSessionId)
            val range = effect.bandLevelRange
            if (range.size >= 2) {
                bandLevelMinMbInternal = range[0].toInt()
                bandLevelMaxMbInternal = range[1].toInt()
            }

            val count = effect.numberOfBands.toInt()
            if (count < 3) {
                effect.release()
                supported = false
                return
            }

            equalizer = effect
            bandFrequenciesHzInternal.clear()
            bandLevelsMbInternal.clear()

            repeat(count) { band ->
                val hz = (effect.getCenterFreq(band.toShort()) / 1000)
                    .coerceAtLeast(1)
                bandFrequenciesHzInternal += hz
                bandLevelsMbInternal += effect.getBandLevel(band.toShort())
            }

            effectInitialized = true
            supported = true

            if (savedBands.size == bandLevelsMbInternal.size) {
                savedBands.forEachIndexed { index, value ->
                    bandLevelsMbInternal[index] = value
                        .coerceIn(bandLevelMinMbInternal, bandLevelMaxMbInternal)
                        .toShort()
                }
            }

            applyAllBands()
            applyEnabledState()
        } catch (_: Throwable) {
            release()
            supported = false
            enabled = false
        }
    }


    fun snapshot(): EqualizerState = EqualizerState(
        supported = supported,
        enabled = enabled && effectInitialized,
        preset = preset,
        bandLevelsMb = bandLevelsMbInternal.toList(),
        bandFrequenciesHz = bandFrequenciesHzInternal.toList(),
        bandLevelMinMb = bandLevelMinMbInternal,
        bandLevelMaxMb = bandLevelMaxMbInternal
    )

    fun setEnabled(value: Boolean) {
        if (!value) {
            enabled = false
            applyEnabledState()
            persist()
            return
        }

        initializeIfNeeded()
        if (!supported || !effectInitialized) return

        enabled = true
        if (preset == PRESET_FLAT) preset = PRESET_CUSTOM
        applyEnabledState()
        persist()
    }

    fun applyPreset(requestedPreset: String) {
        initializeIfNeeded()
        if (!supported || !effectInitialized) return

        val canonical = when (requestedPreset) {
            PRESET_FLAT, PRESET_POP, PRESET_ROCK, PRESET_DANCE, PRESET_DJ, PRESET_VOCAL -> requestedPreset
            else -> PRESET_FLAT
        }
        val curve = PRESET_CURVES_DB[canonical] ?: PRESET_CURVES_DB.getValue(PRESET_FLAT)

        bandFrequenciesHzInternal.forEachIndexed { index, hz ->
            bandLevelsMbInternal[index] = dbToMillibel(interpolateCurveDb(hz, curve))
        }

        preset = canonical
        enabled = canonical != PRESET_FLAT
        applyAllBands()
        applyEnabledState()
        persist()
    }

    fun setBandLevel(band: Int, levelMb: Int) {
        initializeIfNeeded()
        if (!supported || !effectInitialized || band !in bandLevelsMbInternal.indices) return

        bandLevelsMbInternal[band] = levelMb
            .coerceIn(bandLevelMinMbInternal, bandLevelMaxMbInternal)
            .toShort()
        preset = PRESET_CUSTOM
        enabled = true
        applyAllBands()
        applyEnabledState()
        persist()
    }

    fun reset() {
        initializeIfNeeded()
        bandLevelsMbInternal.indices.forEach { index ->
            bandLevelsMbInternal[index] = 0
                .coerceIn(bandLevelMinMbInternal, bandLevelMaxMbInternal)
                .toShort()
        }
        preset = PRESET_FLAT
        enabled = false
        applyAllBands()
        applyEnabledState()
        persist()
    }

    private fun applyAllBands() {
        if (!effectInitialized) return
        bandLevelsMbInternal.forEachIndexed { index, level ->
            try {
                equalizer?.setBandLevel(index.toShort(), level)
            } catch (_: Throwable) {
                // Vendor effects may expose a read-only band. Keep the rest of the EQ alive.
            }
        }
    }

    private fun applyEnabledState() {
        try {
            equalizer?.enabled = enabled && supported && effectInitialized
        } catch (_: Throwable) {
        }
    }

    private fun readPersistedState() {
        if (!persistenceEnabled) return

        val schema = prefs.getInt(KEY_SCHEMA, 0)
        if (schema != CURRENT_SCHEMA) {
            enabled = false
            preset = PRESET_FLAT
            savedBands = emptyList()
            persist()
            return
        }

        enabled = prefs.getBoolean(KEY_ENABLED, false)
        preset = when (val stored = prefs.getString(KEY_PRESET, PRESET_FLAT)) {
            PRESET_POP, PRESET_ROCK, PRESET_DANCE, PRESET_DJ, PRESET_VOCAL, PRESET_CUSTOM -> stored
            else -> PRESET_FLAT
        }
        savedBands = prefs.getString(KEY_BANDS, null)
            ?.split(',')
            ?.mapNotNull(String::toIntOrNull)
            ?: emptyList()
    }

    private fun persist() {
        if (!persistenceEnabled) return
        prefs.edit()
            .putBoolean(KEY_ENABLED, enabled)
            .putString(KEY_PRESET, preset)
            .putString(KEY_BANDS, bandLevelsMbInternal.joinToString(",") { it.toString() })
            .putInt(KEY_SCHEMA, CURRENT_SCHEMA)
            .apply()
    }

    fun release() {
        try { equalizer?.enabled = false } catch (_: Throwable) {}
        try { equalizer?.release() } catch (_: Throwable) {}
        equalizer = null
        effectInitialized = false
    }

    private fun dbToMillibel(db: Float): Short =
        (db * 100f).roundToInt()
            .coerceIn(bandLevelMinMbInternal, bandLevelMaxMbInternal)
            .toShort()

    private fun interpolateCurveDb(hz: Int, curve: FloatArray): Float {
        if (curve.size != REFERENCE_FREQUENCIES_HZ.size) return 0f
        val target = hz.coerceAtLeast(REFERENCE_FREQUENCIES_HZ.first()).toFloat()
        if (target >= REFERENCE_FREQUENCIES_HZ.last()) return curve.last()

        for (i in 0 until REFERENCE_FREQUENCIES_HZ.lastIndex) {
            val f1 = REFERENCE_FREQUENCIES_HZ[i].toFloat()
            val f2 = REFERENCE_FREQUENCIES_HZ[i + 1].toFloat()
            if (target <= f2) {
                val span = ln(f2 / f1)
                val t = if (span == 0f) 0f else ln(target / f1) / span
                return curve[i] + (curve[i + 1] - curve[i]) * t
            }
        }
        return curve.last()
    }
}
