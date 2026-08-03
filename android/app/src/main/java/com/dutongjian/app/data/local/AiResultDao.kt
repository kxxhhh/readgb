package com.dutongjian.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AiResultDao {
    @Query("SELECT * FROM ai_results ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<AiResultEntity>>

    @Upsert
    suspend fun upsert(result: AiResultEntity)

    @Query("DELETE FROM ai_results WHERE id = :id")
    suspend fun deleteById(id: String)
}
