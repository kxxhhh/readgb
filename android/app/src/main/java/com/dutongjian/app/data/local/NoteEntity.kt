package com.dutongjian.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.dutongjian.app.domain.model.Note

@Entity(
    tableName = "reading_notes",
    indices = [Index(value = ["articleId"]), Index(value = ["createdAt"])],
)
data class NoteEntity(
    @PrimaryKey val id: String,
    val articleId: String,
    val startIndex: Int,
    val endIndex: Int,
    val selectedText: String,
    val memo: String,
    val color: String,
    val createdAt: Long,
)

fun NoteEntity.toDomain() = Note(id, articleId, startIndex, endIndex, selectedText, memo, color, createdAt)

fun Note.toEntity() = NoteEntity(id, articleId, startIndex, endIndex, selectedText, memo, color, createdAt)
