package com.dutongjian.app.domain.model

enum class AppThemeMode(val label: String, val description: String) {
    SYSTEM("跟随系统", "自动匹配设备的浅色或深色设置"),
    LIGHT("浅色", "纸张质感，更适合白天阅读"),
    DARK("深色", "降低亮度，更适合夜间阅读");

    fun next(): AppThemeMode = when (this) {
        SYSTEM -> DARK
        DARK -> LIGHT
        LIGHT -> SYSTEM
    }

    companion object {
        fun fromName(value: String?): AppThemeMode = entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

enum class ReadingMode(val label: String) {
    PARALLEL("对照"),
    ORIGINAL("原文"),
    TRANSLATION("白话"),
}

data class ReadingPreferences(
    val mode: ReadingMode = ReadingMode.PARALLEL,
    val script: TextScript = TextScript.SIMPLIFIED,
    val fontPercent: Int = 100,
    val lineSpacingPercent: Int = 100,
    val motionEnabled: Boolean = true,
)

enum class TtsEngineType(val label: String, val description: String) {
    LOCAL("Android 本地 TTS", "离线 · 使用系统引擎"),
    EDGE("微软 Edge-TTS", "在线 · 高音质"),
}

enum class TextScript(val label: String, val description: String) {
    SIMPLIFIED("简", "简体视图"),
    TRADITIONAL("繁", "繁体视图"),
    VARIANT("异体", "异体字视图"),
}

data class ReadingStats(
    val totalSeconds: Long = 0L,
    val readCharacters: Long = 0L,
    val openedItems: Int = 0,
    val coveredVolumes: Int = 0,
    val totalVolumes: Int = 294,
    val dailySeconds: Map<String, Long> = emptyMap(),
)

data class TimelineEvent(
    val item: ReadingItem,
    val yearLabel: String,
    val era: String,
    val sortKey: String,
    val yearInt: Int? = null,
)

data class HistoricalPlace(
    val ancientName: String,
    val modernName: String,
    val latitude: Double,
    val longitude: Double,
    val description: String,
)

data class Note(
    val id: String,
    val articleId: String,
    val startIndex: Int,
    val endIndex: Int,
    val selectedText: String,
    val memo: String,
    val color: String,
    val createdAt: Long,
)
