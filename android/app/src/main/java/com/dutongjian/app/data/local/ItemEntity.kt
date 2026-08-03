package com.dutongjian.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.dutongjian.app.domain.model.ReadingItem

@Entity(tableName = "reading_items")
data class ItemEntity(
    @PrimaryKey val id: String,
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
    val sortOrder: Int = 0,
    val original: String = "",
    val translation: String = "",
    val notes: String = "",
    val tags: String = "",
    val isFavorite: Boolean,
    val lastOpenedAt: Long?,
)

data class ItemLocalState(
    val id: String,
    val isFavorite: Boolean,
    val lastOpenedAt: Long?,
)

data class ItemSummaryEntity(
    val id: String,
    val title: String,
    val category: String,
    val dynasty: String,
    val summary: String,
    val sourceUrl: String,
    val updatedAt: String,
    val section: String,
    val volumeId: String?,
    val yearId: String?,
    val sortOrder: Int,
    val tags: String,
    val isFavorite: Boolean,
    val lastOpenedAt: Long?,
)

fun ItemEntity.toDomain() = ReadingItem(
    id = id,
    title = title,
    category = category,
    dynasty = dynasty,
    summary = summary,
    content = content,
    sourceUrl = sourceUrl,
    updatedAt = updatedAt,
    section = section,
    volumeId = volumeId,
    yearId = yearId,
    sortOrder = sortOrder,
    original = original,
    translation = translation,
    notes = notes,
    tags = tags.split("|").filter(String::isNotBlank),
    isFavorite = isFavorite,
    lastOpenedAt = lastOpenedAt,
)

fun ItemSummaryEntity.toDomain() = ReadingItem(
    id = id,
    title = title,
    category = category,
    dynasty = dynasty,
    summary = summary,
    content = "",
    sourceUrl = sourceUrl,
    updatedAt = updatedAt,
    section = section,
    volumeId = volumeId,
    yearId = yearId,
    sortOrder = sortOrder,
    tags = tags.split("|").filter(String::isNotBlank),
    isFavorite = isFavorite,
    lastOpenedAt = lastOpenedAt,
)
