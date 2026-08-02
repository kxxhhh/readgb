package com.dutongjian.app.data.tts

import android.content.Context
import com.dutongjian.app.domain.model.TtsEngineType

class TtsSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("tts_settings", Context.MODE_PRIVATE)

    fun read(): TtsEngineType {
        if (!preferences.getBoolean(KEY_DEFAULT_MIGRATED, false)) {
            preferences.edit()
                .putString(KEY_ENGINE, TtsEngineType.LOCAL.name)
                .putBoolean(KEY_DEFAULT_MIGRATED, true)
                .apply()
        }
        return runCatching {
            TtsEngineType.valueOf(preferences.getString(KEY_ENGINE, TtsEngineType.LOCAL.name).orEmpty())
        }.getOrDefault(TtsEngineType.LOCAL)
    }

    fun write(engine: TtsEngineType) {
        preferences.edit().putString(KEY_ENGINE, engine.name).putBoolean(KEY_DEFAULT_MIGRATED, true).apply()
    }

    private companion object {
        const val KEY_ENGINE = "engine"
        const val KEY_DEFAULT_MIGRATED = "default_migrated"
    }
}
