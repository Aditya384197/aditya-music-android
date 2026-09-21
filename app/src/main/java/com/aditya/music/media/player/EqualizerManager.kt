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
 * Quality-first per-player equalizer.
 *
 * The default state is bypassed: with the EQ off, the app does not add BassBoost,
 * reverb, loudness, clarity or any other extra DSP. When the user enables the EQ,
 * all tone shaping is done through the Android Equalizer attached to the current
 * ExoPlayer audio session.
 */
data class EqualizerState(
    val supported: Boolean = false,
    val enabled: Boolean = false,
    val preset: String = PRESET_FLAT,
    val bassDb: Float = 0f,
    val midDb: Float = 0f,
    val trebleDb: Float = 0f,
    val bandLevelsMb: List<Short> = emptyList(),
    val bandFrequenciesHz: List<Int> = emptyList(),
    val bandLevelMinMb: Int = -1500,
    val bandLevelMaxMb: Int = 1500
)

const val PRESET_FLAT = "Flat"
const val PRESET_POP = "Pop"
const val PRESET_ROCK = "Rock"
const val PRESET_DANCE = "Dance"
const val PRESET_CLASSICAL = "Classical"
const val PRESET_VOCAL = "Vocal"
const val PRESET_CUSTOM = "Custom"

private const val PREFS_NAME = "aditya_music_equalizer"
private const val KEY_ENABLED = "enabled"
private const val KEY_PRESET = "preset"
private const val KEY_BASS_DB = "bass_db"
private const val KEY_MID_DB = "mid_db"
private const val KEY_TREBLE_DB = "treble_db"
private const val KEY_BANDS = "bands"
private const val KEY_SCHEMA = "schema"
private const val CURRENT_SCHEMA = 2

private const val MIN_TONE_DB = -6f
private const val MAX_TONE_DB = 6f

/** Base preset values at roughly 60 Hz, 250 Hz, 1 kHz, 4 kHz and 16 kHz. */
private val PRESET_CURVES_DB = mapOf(
    PRESET_FLAT to floatArrayOf(0f, 0f, 0f, 0f, 0f),
    PRESET_POP to floatArrayOf(-1.0f, 2.0f, 1.5f, 3.0f, -1.0f),
    PRESET_ROCK to floatArrayOf(3.5f, 2.5f, -1.0f, 2.5f, 3.5f),
    PRESET_DANCE to floatArrayOf(4.5f, 2.0f, 0.0f, 2.0f, 3.5f),
    PRESET_CLASSICAL to floatArrayOf(0.0f, 0.0f, 0.0f, 2.0f, 2.5f),
    PRESET_VOCAL to floatArrayOf(-2.0f, -1.0f, 2.5f, 3.5f, 0.5f)
)

private val PRESET_TONES_DB = mapOf(
    PRESET_FLAT to floatArrayOf(0f, 0f, 0f),
    PRESET_POP to floatArrayOf(1.0f, 0.5f, 1.5f),
    PRESET_ROCK to floatArrayOf(1.5f, 0f, 1.0f),
    PRESET_DANCE to floatArrayOf(2.0f, 0f, 1.0f),
    PRESET_CLASSICAL to floatArrayOf(0f, 0.5f, 0.5f),
    PRESET_VOCAL to floatArrayOf(-0.5f, 1.5f, 1.0f)
)

/** App-process bridge between the playback service and Compose UI. */
object EqualizerController {
    private var manager: EqualizerManager? = null
    private val _state = MutableStateFlow(EqualizerState())
    val state: StateFlow<EqualizerState> = _state.asStateFlow()

    fun attach(newManager: EqualizerManager) {
        manager = newManager
        _state.value = newManager.snapshot()
    }

    fun detach(target: EqualizerManager? = null) {
        if (target == null || manager === target) {
            manager = null
            _state.value = EqualizerState()
        }
    }

    fun setEnabled(enabled: Boolean) {
        manager?.setEnabled(enabled)
        sync()
    }

    fun applyPreset(preset: String) {
        manager?.applyPreset(preset)
        sync()
    }

    fun setBassDb(db: Float) {
        manager?.setBassDb(db)
        sync()
    }

    fun setMidDb(db: Float) {
        manager?.setMidDb(db)
        sync()
    }

    fun setTrebleDb(db: Float) {
        manager?.setTrebleDb(db)
        sync()
    }

    fun setBandLevel(index: Int, levelMb: Int) {
        manager?.setBandLevel(index, levelMb.toShort())
        sync()
    }

    fun reset() {
        manager?.reset()
        sync()
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
    private var supported = false
    private var enabled = false
    private var preset = PRESET_FLAT
    private var bassDb = 0f
    private var midDb = 0f
    private var trebleDb = 0f
    private var bandLevelMinMbInternal = -1500
    private var bandLevelMaxMbInternal = 1500

    private val bandFrequenciesHzInternal = mutableListOf<Int>()
    private val bandLevelsMbInternal = mutableListOf<Short>()
    private val baseBandLevelsMbInternal = mutableListOf<Short>()

    init {
        initializeEffects()
    }

    private fun initializeEffects() {
        if (audioSessionId <= 0) return

        try {
            equalizer = Equalizer(0, audioSessionId)
            supported = true

            val levelRange = equalizer?.bandLevelRange
            if (levelRange != null && levelRange.size >= 2) {
                bandLevelMinMbInternal = levelRange[0].toInt()
                bandLevelMaxMbInternal = levelRange[1].toInt()
            }

            val bandCount = equalizer?.numberOfBands?.toInt() ?: 0
            repeat(bandCount) { band ->
                val frequency = ((equalizer?.getCenterFreq(band.toShort()) ?: 0) / 1000)
                    .coerceAtLeast(1)
                val level = equalizer?.getBandLevel(band.toShort()) ?: 0.toShort()
                bandFrequenciesHzInternal += frequency
                bandLevelsMbInternal += level
                baseBandLevelsMbInternal += level
            }

            loadPersistedSettings()
            applyAllBandLevels()
            applyEnabledState()
        } catch (_: Throwable) {
            release()
            supported = false
        }
    }

    fun snapshot(): EqualizerState = EqualizerState(
        supported = supported,
        enabled = enabled,
        preset = preset,
        bassDb = bassDb,
        midDb = midDb,
        trebleDb = trebleDb,
        bandLevelsMb = bandLevelsMbInternal.toList(),
        bandFrequenciesHz = bandFrequenciesHzInternal.toList(),
        bandLevelMinMb = bandLevelMinMbInternal,
        bandLevelMaxMb = bandLevelMaxMbInternal
    )

    fun setEnabled(value: Boolean) {
        enabled = value && supported
        applyEnabledState()
        persist()
    }

    fun applyPreset(requestedPreset: String) {
        val canonical = when (requestedPreset) {
            PRESET_FLAT, PRESET_POP, PRESET_ROCK,
            PRESET_DANCE, PRESET_CLASSICAL, PRESET_VOCAL -> requestedPreset
            else -> PRESET_FLAT
        }

        val curve = PRESET_CURVES_DB[canonical] ?: PRESET_CURVES_DB.getValue(PRESET_FLAT)
        for (index in baseBandLevelsMbInternal.indices) {
            val frequency = bandFrequenciesHzInternal[index].coerceAtLeast(1)
            baseBandLevelsMbInternal[index] = dbToMillibel(
                interpolateCurveDb(frequency, curve)
            )
        }

        val tones = PRESET_TONES_DB[canonical] ?: floatArrayOf(0f, 0f, 0f)
        bassDb = tones[0]
        midDb = tones[1]
        trebleDb = tones[2]
        preset = canonical
        enabled = canonical != PRESET_FLAT && supported

        applyAllBandLevels()
        applyEnabledState()
        persist()
    }

    fun setBassDb(value: Float) {
        bassDb = value.coerceIn(MIN_TONE_DB, MAX_TONE_DB)
        preset = PRESET_CUSTOM
        enabled = supported
        applyAllBandLevels()
        applyEnabledState()
        persist()
    }

    fun setMidDb(value: Float) {
        midDb = value.coerceIn(MIN_TONE_DB, MAX_TONE_DB)
        preset = PRESET_CUSTOM
        enabled = supported
        applyAllBandLevels()
        applyEnabledState()
        persist()
    }

    fun setTrebleDb(value: Float) {
        trebleDb = value.coerceIn(MIN_TONE_DB, MAX_TONE_DB)
        preset = PRESET_CUSTOM
        enabled = supported
        applyAllBandLevels()
        applyEnabledState()
        persist()
    }

    fun setBandLevel(band: Int, level: Short) {
        if (band !in bandLevelsMbInternal.indices) return
        val range = safeBandLevelRange()
        val clamped = level.toInt().coerceIn(range.first, range.second).toShort()
        baseBandLevelsMbInternal[band] = clamped
        preset = PRESET_CUSTOM
        enabled = supported
        applyAllBandLevels()
        applyEnabledState()
        persist()
    }

    fun reset() {
        baseBandLevelsMbInternal.indices.forEach { index ->
            baseBandLevelsMbInternal[index] = 0
                .coerceIn(bandLevelMinMbInternal, bandLevelMaxMbInternal)
                .toShort()
        }
        bassDb = 0f
        midDb = 0f
        trebleDb = 0f
        preset = PRESET_FLAT
        enabled = false
        applyAllBandLevels()
        applyEnabledState()
        persist()
    }

    private fun applyAllBandLevels() {
        if (baseBandLevelsMbInternal.isEmpty()) return
        val range = safeBandLevelRange()

        baseBandLevelsMbInternal.indices.forEach { index ->
            val hz = bandFrequenciesHzInternal.getOrElse(index) { 1000 }
            val toneOffset = bassContributionDb(hz, bassDb) +
                midContributionDb(hz, midDb) +
                trebleContributionDb(hz, trebleDb)
            val finalLevel = (baseBandLevelsMbInternal[index].toInt() + dbToMillibel(toneOffset))
                .coerceIn(range.first, range.second)
                .toShort()
            bandLevelsMbInternal[index] = finalLevel
            try {
                equalizer?.setBandLevel(index.toShort(), finalLevel)
            } catch (_: Throwable) {
                // Some vendor equalizers expose read-only/unsupported bands. Keep the UI usable.
            }
        }
    }

    private fun applyEnabledState() {
        try {
            equalizer?.enabled = enabled && supported
        } catch (_: Throwable) {
        }
    }

    private fun loadPersistedSettings() {
        if (!persistenceEnabled) return

        // The old release stored BassBoost/clarity/reverb settings. Do not silently carry
        // those processing choices into the quality-first EQ implementation.
        if (prefs.getInt(KEY_SCHEMA, 0) < CURRENT_SCHEMA) {
            enabled = false
            preset = PRESET_FLAT
            bassDb = 0f
            midDb = 0f
            trebleDb = 0f
            baseBandLevelsMbInternal.fill(0.toShort())
            persist()
            return
        }

        enabled = prefs.getBoolean(KEY_ENABLED, false) && supported
        preset = prefs.getString(KEY_PRESET, PRESET_FLAT) ?: PRESET_FLAT
        bassDb = prefs.getFloat(KEY_BASS_DB, 0f).coerceIn(MIN_TONE_DB, MAX_TONE_DB)
        midDb = prefs.getFloat(KEY_MID_DB, 0f).coerceIn(MIN_TONE_DB, MAX_TONE_DB)
        trebleDb = prefs.getFloat(KEY_TREBLE_DB, 0f).coerceIn(MIN_TONE_DB, MAX_TONE_DB)

        val savedBands = prefs.getString(KEY_BANDS, null)
            ?.split(',')
            ?.mapNotNull { it.toIntOrNull() }
        if (savedBands != null && savedBands.size == baseBandLevelsMbInternal.size) {
            savedBands.forEachIndexed { index, level ->
                baseBandLevelsMbInternal[index] = level
                    .coerceIn(bandLevelMinMbInternal, bandLevelMaxMbInternal)
                    .toShort()
            }
        }
    }

    private fun persist() {
        if (!persistenceEnabled) return
        prefs.edit()
            .putBoolean(KEY_ENABLED, enabled)
            .putString(KEY_PRESET, preset)
            .putFloat(KEY_BASS_DB, bassDb)
            .putFloat(KEY_MID_DB, midDb)
            .putFloat(KEY_TREBLE_DB, trebleDb)
            .putString(KEY_BANDS, baseBandLevelsMbInternal.joinToString(",") { it.toString() })
            .putInt(KEY_SCHEMA, CURRENT_SCHEMA)
            .apply()
    }

    private fun safeBandLevelRange(): Pair<Int, Int> =
        bandLevelMinMbInternal to bandLevelMaxMbInternal

    fun release() {
        try { equalizer?.enabled = false } catch (_: Throwable) {}
        try { equalizer?.release() } catch (_: Throwable) {}
        equalizer = null
    }

    private fun dbToMillibel(db: Float): Short =
        (db * 100f).roundToInt()
            .coerceIn(bandLevelMinMbInternal, bandLevelMaxMbInternal)
            .toShort()

    private fun bassContributionDb(hz: Int, amountDb: Float): Float {
        val x = ln(hz.coerceIn(40, 4000) / 60f) / ln(1000f / 60f)
        return amountDb * (1f - x.coerceIn(0f, 1f))
    }

    private fun midContributionDb(hz: Int, amountDb: Float): Float {
        val distance = kotlin.math.abs(ln(hz.coerceIn(80, 12000).toFloat() / 1000f))
        return amountDb * (1f - (distance / ln(8f)).coerceIn(0f, 1f))
    }

    private fun trebleContributionDb(hz: Int, amountDb: Float): Float {
        val x = ln(hz.coerceIn(1000, 20000).toFloat() / 1000f) / ln(16000f / 1000f)
        return amountDb * x.coerceIn(0f, 1f)
    }

    private fun interpolateCurveDb(hz: Int, curve: FloatArray): Float {
        val points = floatArrayOf(60f, 250f, 1000f, 4000f, 16000f)
        val clampedHz = hz.toFloat().coerceIn(points.first(), points.last())
        for (i in 0 until points.lastIndex) {
            if (clampedHz <= points[i + 1]) {
                val span = ln(points[i + 1] / points[i])
                val t = if (span <= 0f) 0f else ln(clampedHz / points[i]) / span
                return curve[i] + (curve[i + 1] - curve[i]) * t
            }
        }
        return curve.last()
    }
}
