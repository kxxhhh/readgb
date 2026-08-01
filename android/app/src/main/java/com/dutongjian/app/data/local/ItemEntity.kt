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
    isFavorite = isFavorite,
    lastOpenedAt = lastOpenedAt,
)
