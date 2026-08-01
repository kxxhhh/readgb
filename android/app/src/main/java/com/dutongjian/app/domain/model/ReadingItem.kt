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
    val section: String = "资治通鉴",
    val volumeId: String? = null,
    val yearId: String? = null,
    val original: String = "",
    val translation: String = "",
    val notes: String = "",
    val tags: List<String> = emptyList(),
    val isFavorite: Boolean = false,
    val lastOpenedAt: Long? = null,
)

data class HomeFeed(
    val items: List<ReadingItem>,
    val categories: List<String>,
)

data class LibrarySection(
    val id: String,
    val title: String,
    val description: String,
    val sourceUrl: String,
    val sortOrder: Int,
)

data class Volume(
    val id: String,
    val sectionId: String,
    val title: String,
    val dynasty: String,
    val sortOrder: Int,
)

data class ReadingYear(
    val id: String,
    val volumeId: String,
    val title: String,
    val era: String,
    val sortOrder: Int,
)

data class KnowledgeEntry(
    val id: String,
    val title: String,
    val category: String,
    val summary: String,
    val content: String,
    val sourceUrl: String,
    val updatedAt: String,
)
