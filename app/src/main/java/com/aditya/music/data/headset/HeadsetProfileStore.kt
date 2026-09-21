package com.aditya.music.data.headset

import android.content.Context
import org.json.JSONArray

object HeadsetProfileStore {
    private const val ASSET_NAME = "headset_profiles.json"

    fun load(context: Context): List<HeadsetProfile> {
        return runCatching {
            val json = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
            val array = JSONArray(json)
            buildList(array.length()) {
                for (i in 0 until array.length()) {
                    HeadsetProfile.fromJson(array.getJSONObject(i))?.let(::add)
                }
            }
        }.getOrElse { emptyList() }
    }
}
