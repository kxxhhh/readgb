package com.dutongjian.app.domain.model

data class ReadingItem(
    val id: String,
    val title: String,
    val category: String,
    val dynasty: String,
    val summary: String,
    val content: String,
    val sourceUrl: String,
    val updatedAt: String,
    val isFavorite: Boolean = false,
    val lastOpenedAt: Long? = null,
)

data class HomeFeed(
    val items: List<ReadingItem>,
    val categories: List<String>,
)
