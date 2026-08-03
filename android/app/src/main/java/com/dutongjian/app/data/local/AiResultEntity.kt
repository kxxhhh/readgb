package com.dutongjian.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.dutongjian.app.domain.model.AiResult
import com.dutongjian.app.domain.model.AiTask

@Entity(
    tableName = "ai_results",
    indices = [Index(value = ["itemId"]), Index(value = ["createdAt"])],
)
data class AiResultEntity(
    @PrimaryKey val id: String,
    val itemId: String,
    val task: String,
    val result: String,
    val createdAt: Long,
)

fun AiResultEntity.toDomain() = AiResult(
    id = id,
    itemId = itemId,
    task = AiTask.entries.firstOrNull { it.name == task } ?: AiTask.SUMMARY,
    result = result,
    createdAt = createdAt,
)

fun AiResult.toEntity() = AiResultEntity(
    id = id,
    itemId = itemId,
    task = task.name,
    result = result,
    createdAt = createdAt,
)
