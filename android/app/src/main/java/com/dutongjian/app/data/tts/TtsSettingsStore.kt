package com.dutongjian.app.data.tts

import android.content.Context
import com.dutongjian.app.domain.model.TtsEngineType

class TtsSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("tts_settings", Context.MODE_PRIVATE)

    fun read(): TtsEngineType = runCatching {
        TtsEngineType.valueOf(preferences.getString(KEY_ENGINE, TtsEngineType.EDGE.name).orEmpty())
    }.getOrDefault(TtsEngineType.EDGE)

    fun write(engine: TtsEngineType) {
        preferences.edit().putString(KEY_ENGINE, engine.name).apply()
    }

    private companion object { const val KEY_ENGINE = "engine" }
}
