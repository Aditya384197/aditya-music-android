package com.aditya.music.media.player

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.PresetReverb
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Per-player-session equalizer controller.
 *
 * The previous implementation created the Android AudioFX objects from the player's
 * initial audioSessionId. ExoPlayer does not have a valid session id at that point on
 * every device, so the effect could silently remain disconnected. This implementation
 * is created only after ExoPlayer reports a real session id and can be recreated when
 * Android changes that id.
 */
data class EqualizerState(
    val supported: Boolean = false,
    val enabled: Boolean = false,
    val preset: String = PRESET_FLAT,
    val bassStrength: Int = 0,
    val clarityStrength: Int = 0,
    val room: String = ROOM_NONE,
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
const val ROOM_NONE = "None"
const val ROOM_SMALL = "Small room"
const val ROOM_MEDIUM = "Medium room"
const val ROOM_LARGE = "Large room"

private const val PREFS_NAME = "aditya_music_equalizer"
private const val KEY_ENABLED = "enabled"
private const val KEY_PRESET = "preset"
private const val KEY_BASS = "bass"
private const val KEY_CLARITY = "clarity"
private const val KEY_ROOM = "room"
private const val KEY_BANDS = "bands"

private val PRESET_CURVES_DB = mapOf(
    PRESET_FLAT to floatArrayOf(0f, 0f, 0f, 0f, 0f),
    PRESET_POP to floatArrayOf(-1.0f, 2.0f, 4.0f, 2.0f, -1.0f),
    PRESET_ROCK to floatArrayOf(4.0f, 3.0f, -1.0f, 3.0f, 4.0f),
    PRESET_DANCE to floatArrayOf(5.0f, 2.5f, 0.0f, 2.5f, 4.5f),
    PRESET_CLASSICAL to floatArrayOf(0.0f, 0.0f, 0.0f, 2.0f, 3.0f),
    PRESET_VOCAL to floatArrayOf(-2.0f, -1.0f, 3.0f, 3.0f, 0.0f)
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

    fun setBassStrength(strength: Int) {
        manager?.setBassBoostStrength(strength.coerceIn(0, 1000))
        sync()
    }

    fun setClarityStrength(strength: Int) { manager?.setClarityStrength(strength.coerceIn(0, 1000)); sync() }

    fun setRoom(room: String) { manager?.setRoom(room); sync() }

    fun setBandLevel(index: Int, levelMb: Int) {
        manager?.setBandLevel(index, levelMb.toShort())
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
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var reverb: PresetReverb? = null
    private var supported = false
    private var enabled = true
    private var preset = PRESET_FLAT
    private var bassStrength = 0
    private var clarityStrength = 0
    private var room = ROOM_NONE
    private var bandLevelMinMbInternal = -1500
    private var bandLevelMaxMbInternal = 1500

    private val bandFrequenciesHzInternal = mutableListOf<Int>()
    private val bandLevelsMbInternal = mutableListOf<Short>()

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
                bandFrequenciesHzInternal += (equalizer?.getCenterFreq(band.toShort()) ?: 0) / 1000
                bandLevelsMbInternal += equalizer?.getBandLevel(band.toShort()) ?: 0
            }

            try { bassBoost = BassBoost(0, audioSessionId) } catch (_: Throwable) { bassBoost = null }
            try { reverb = PresetReverb(0, audioSessionId) } catch (_: Throwable) { reverb = null }

            loadPersistedSettings()
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
        bassStrength = bassStrength,
        clarityStrength = clarityStrength,
        room = room,
        bandLevelsMb = bandLevelsMbInternal.toList(),
        bandFrequenciesHz = bandFrequenciesHzInternal.toList(),
        bandLevelMinMb = bandLevelMinMbInternal,
        bandLevelMaxMb = bandLevelMaxMbInternal
    )

    fun setEnabled(value: Boolean) {
        enabled = value
        applyEnabledState()
        persist()
    }

    fun applyPreset(requestedPreset: String) {
        val canonical = when (requestedPreset) {
            PRESET_FLAT, PRESET_POP, PRESET_ROCK, PRESET_DANCE,
            PRESET_CLASSICAL, PRESET_VOCAL -> requestedPreset
            else -> PRESET_FLAT
        }

        val curve = PRESET_CURVES_DB[canonical] ?: PRESET_CURVES_DB.getValue(PRESET_FLAT)
        for (index in bandLevelsMbInternal.indices) {
            val frequency = bandFrequenciesHzInternal[index].coerceAtLeast(1)
            val db = interpolateCurveDb(frequency, curve)
            setBandLevelInternal(index, dbToMillibel(db))
        }

        bassStrength = when (canonical) {
            PRESET_POP -> 180
            PRESET_ROCK -> 220
            PRESET_DANCE -> 420
            PRESET_CLASSICAL -> 80
            PRESET_VOCAL -> 60
            else -> 0
        }
        setBassBoostStrengthInternal(bassStrength)
        setRoom(room)
        preset = canonical
        persist()
    }

    fun setBandLevel(band: Int, level: Short) {
        if (band !in bandLevelsMbInternal.indices) return
        val range = safeBandLevelRange()
        val clamped = level.toInt().coerceIn(range.first, range.second).toShort()
        setBandLevelInternal(band, clamped)
        preset = PRESET_CUSTOM
        persist()
    }

    fun setBassBoostStrength(strength: Int) {
        bassStrength = strength.coerceIn(0, 1000)
        setBassBoostStrengthInternal(bassStrength)
        preset = PRESET_CUSTOM
        persist()
    }

    fun setClarityStrength(strength: Int) {
        clarityStrength = strength.coerceIn(0, 1000)
        applyClarity()
        preset = PRESET_CUSTOM
        persist()
    }

    fun setRoom(requested: String) {
        room = when (requested) { ROOM_SMALL, ROOM_MEDIUM, ROOM_LARGE -> requested; else -> ROOM_NONE }
        try {
            reverb?.preset = when (room) {
                ROOM_SMALL -> PresetReverb.PRESET_SMALLROOM
                ROOM_MEDIUM -> PresetReverb.PRESET_MEDIUMROOM
                ROOM_LARGE -> PresetReverb.PRESET_LARGEROOM
                else -> PresetReverb.PRESET_NONE
            }
            reverb?.enabled = enabled && room != ROOM_NONE
        } catch (_: Throwable) {}
        persist()
    }

    private fun applyClarity() {
        if (bandLevelsMbInternal.isEmpty()) return
        val range = safeBandLevelRange()
        val amount = clarityStrength / 1000f
        bandLevelsMbInternal.indices.forEach { index ->
            val hz = bandFrequenciesHzInternal[index]
            val boost = when { hz in 1500..6000 -> (450f * amount).toInt(); hz in 700..9000 -> (180f * amount).toInt(); else -> 0 }
            val base = bandLevelsMbInternal[index].toInt()
            setBandLevelInternal(index, (base + boost).coerceIn(range.first, range.second).toShort())
        }
    }

    private fun setBandLevelInternal(band: Int, level: Short) {
        try {
            equalizer?.setBandLevel(band.toShort(), level)
            if (band in bandLevelsMbInternal.indices) {
                bandLevelsMbInternal[band] = level
            }
        } catch (_: Throwable) {
            // Some vendor implementations report fewer usable bands than advertised.
        }
    }

    private fun setBassBoostStrengthInternal(strength: Int) {
        try {
            bassBoost?.setStrength(strength.toShort())
        } catch (_: Throwable) {
            // BassBoost is optional and vendor-dependent.
        }
    }

    private fun applyEnabledState() {
        try {
            equalizer?.enabled = enabled
        } catch (_: Throwable) {
        }
        try { bassBoost?.enabled = enabled && bassStrength > 0 } catch (_: Throwable) {}
        try { reverb?.enabled = enabled && room != ROOM_NONE } catch (_: Throwable) {}
    }

    private fun loadPersistedSettings() {
        if (!persistenceEnabled) return

        enabled = prefs.getBoolean(KEY_ENABLED, true)
        preset = prefs.getString(KEY_PRESET, PRESET_FLAT) ?: PRESET_FLAT
        bassStrength = prefs.getInt(KEY_BASS, 0).coerceIn(0, 1000)
        clarityStrength = prefs.getInt(KEY_CLARITY, 0).coerceIn(0, 1000)
        room = prefs.getString(KEY_ROOM, ROOM_NONE) ?: ROOM_NONE

        val savedBands = prefs.getString(KEY_BANDS, null)
            ?.split(',')
            ?.mapNotNull { it.toIntOrNull() }

        if (!savedBands.isNullOrEmpty() && savedBands.size == bandLevelsMbInternal.size) {
            savedBands.forEachIndexed { index, value ->
                val range = safeBandLevelRange()
                setBandLevelInternal(index, value.coerceIn(range.first, range.second).toShort())
            }
        } else {
            applyPreset(preset)
        }

        setBassBoostStrengthInternal(bassStrength)
        applyClarity()
        setRoom(room)
        applyEnabledState()
    }

    private fun persist() {
        if (!persistenceEnabled) return
        prefs.edit()
            .putBoolean(KEY_ENABLED, enabled)
            .putString(KEY_PRESET, preset)
            .putInt(KEY_BASS, bassStrength)
            .putInt(KEY_CLARITY, clarityStrength)
            .putString(KEY_ROOM, room)
            .putString(KEY_BANDS, bandLevelsMbInternal.joinToString(",") { it.toString() })
            .apply()
    }

    private fun safeBandLevelRange(): Pair<Int, Int> {
        return bandLevelMinMbInternal to bandLevelMaxMbInternal
    }

    private fun releaseEffects() {
        try {
            equalizer?.release()
        } catch (_: Throwable) {
        }
        try {
            reverb?.release()
        } catch (_: Throwable) {}
        reverb = null
        try {
            bassBoost?.release()
        } catch (_: Throwable) {
        }
        equalizer = null
        bassBoost = null
    }

    fun release() {
        releaseEffects()
    }

    private fun interpolateCurveDb(frequencyHz: Int, curve: FloatArray): Float {
        val anchors = floatArrayOf(60f, 230f, 910f, 3600f, 14000f)
        val frequency = frequencyHz.coerceIn(anchors.first().toInt(), anchors.last().toInt()).toFloat()

        for (i in 0 until anchors.lastIndex) {
            if (frequency <= anchors[i + 1]) {
                val low = ln(anchors[i])
                val high = ln(anchors[i + 1])
                val position = ((ln(frequency) - low) / (high - low)).coerceIn(0f, 1f)
                return curve[i] + (curve[i + 1] - curve[i]) * position
            }
        }
        return curve.last()
    }

    private fun dbToMillibel(db: Float): Short = (db * 100f).roundToInt().toShort()
}
