package com.dutongjian.app.domain.model

enum class TtsEngineType(val label: String, val description: String) {
    LOCAL("Android 本地 TTS", "离线 · 使用系统引擎"),
    EDGE("微软 Edge-TTS", "在线 · 高音质"),
}

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
