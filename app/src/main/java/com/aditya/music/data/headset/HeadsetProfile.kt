package com.aditya.music.data.headset

import org.json.JSONArray
import org.json.JSONObject

/**
 * Offline headphone/earbud correction profile generated from measured data.
 * Gains are fixed-band dB values at the profile's frequency points.
 */
data class HeadsetProfile(
    val id: String,
    val name: String,
    val type: String,
    val source: String,
    val preampDb: Float,
    val frequenciesHz: List<Int>,
    val gainsDb: List<Float>
) {
    fun matches(query: String): Boolean {
        val q = normalize(query)
        if (q.isBlank()) return true

        val candidates = buildList {
            add(name)
            add(source)
            // Common brand spelling differences used by users when searching offline.
            add(name.replace("Zebronics", "ZEB", ignoreCase = true))
            add(name.replace("boAt", "Boat", ignoreCase = true))
            add(name.replace("B&O", "Bang & Olufsen", ignoreCase = true))
        }
        return candidates.any { normalize(it).contains(q) }
    }

    private fun normalize(value: String): String = value
        .trim()
        .lowercase()
        .replace("&", "and")
        .replace("zebronics", "zeb")
        .replace("boat", "boat")
        .replace(Regex("[^a-z0-9]+"), "")

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("type", type)
        put("source", source)
        put("preampDb", preampDb.toDouble())
        put("frequenciesHz", JSONArray(frequenciesHz))
        put("gainsDb", JSONArray(gainsDb.map { it.toDouble() }))
    }

    companion object {
        fun fromJson(obj: JSONObject): HeadsetProfile? = runCatching {
            val frequencies = obj.optJSONArray("frequenciesHz") ?: return null
            val gains = obj.optJSONArray("gainsDb") ?: return null
            if (frequencies.length() != gains.length() || frequencies.length() < 2) return null

            val hz = List(frequencies.length()) { frequencies.getInt(it) }
            val db = List(gains.length()) { gains.getDouble(it).toFloat() }
            HeadsetProfile(
                id = obj.optString("id").ifBlank { obj.optString("name").lowercase() },
                name = obj.optString("name").ifBlank { return null },
                type = obj.optString("type", TYPE_HEADPHONES),
                source = obj.optString("source", "AutoEq"),
                preampDb = obj.optDouble("preampDb", 0.0).toFloat(),
                frequenciesHz = hz,
                gainsDb = db
            )
        }.getOrNull()

        const val TYPE_EARBUDS = "earbuds"
        const val TYPE_NECKBAND = "neckband"
        const val TYPE_HEADPHONES = "headphones"
    }
}
