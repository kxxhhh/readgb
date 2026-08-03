package com.dutongjian.app.data

import android.content.Context
import com.dutongjian.app.domain.model.AppThemeMode
import com.dutongjian.app.domain.model.ReadingMode
import com.dutongjian.app.domain.model.ReadingPreferences
import com.dutongjian.app.domain.model.TextScript

class AppSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    fun readThemeMode(): AppThemeMode = AppThemeMode.fromName(preferences.getString(KEY_THEME_MODE, null))

    fun writeThemeMode(mode: AppThemeMode) {
        preferences.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun readReadingPreferences(): ReadingPreferences = ReadingPreferences(
        mode = runCatching { ReadingMode.valueOf(preferences.getString(KEY_READING_MODE, ReadingMode.PARALLEL.name).orEmpty()) }
            .getOrDefault(ReadingMode.PARALLEL),
        script = runCatching { TextScript.valueOf(preferences.getString(KEY_TEXT_SCRIPT, TextScript.SIMPLIFIED.name).orEmpty()) }
            .getOrDefault(TextScript.SIMPLIFIED),
        fontPercent = preferences.getInt(KEY_FONT_PERCENT, 100).coerceIn(80, 140),
        lineSpacingPercent = preferences.getInt(KEY_LINE_SPACING_PERCENT, 100).coerceIn(90, 140),
        motionEnabled = preferences.getBoolean(KEY_MOTION_ENABLED, true),
    )

    fun writeReadingPreferences(value: ReadingPreferences) {
        preferences.edit()
            .putString(KEY_READING_MODE, value.mode.name)
            .putString(KEY_TEXT_SCRIPT, value.script.name)
            .putInt(KEY_FONT_PERCENT, value.fontPercent.coerceIn(80, 140))
            .putInt(KEY_LINE_SPACING_PERCENT, value.lineSpacingPercent.coerceIn(90, 140))
            .putBoolean(KEY_MOTION_ENABLED, value.motionEnabled)
            .apply()
    }

    private companion object {
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_READING_MODE = "reading_mode"
        const val KEY_TEXT_SCRIPT = "text_script"
        const val KEY_FONT_PERCENT = "font_percent"
        const val KEY_LINE_SPACING_PERCENT = "line_spacing_percent"
        const val KEY_MOTION_ENABLED = "motion_enabled"
    }
}
