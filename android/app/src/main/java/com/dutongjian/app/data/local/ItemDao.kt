package com.dutongjian.app.data.local

import androidx.room.Dao
import androidx.room.RawQuery
import androidx.room.Query
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Query("SELECT * FROM reading_items ORDER BY updatedAt DESC, title ASC")
    fun observeAll(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM reading_items WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): ItemEntity?

    @Query("SELECT * FROM reading_items WHERE isFavorite = 1 ORDER BY title ASC")
    fun observeFavorites(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM reading_items WHERE lastOpenedAt IS NOT NULL ORDER BY lastOpenedAt DESC")
    fun observeHistory(): Flow<List<ItemEntity>>

    @Query("SELECT COUNT(*) FROM reading_items WHERE id LIKE 'zztj-%'")
    suspend fun fullContentCount(): Int

    @RawQuery
    suspend fun searchFts(query: SupportSQLiteQuery): List<ItemEntity>

    @Upsert
    suspend fun upsertAll(items: List<ItemEntity>)

    @Query("UPDATE reading_items SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    @Query("UPDATE reading_items SET lastOpenedAt = :timestamp WHERE id = :id")
    suspend fun recordOpened(id: String, timestamp: Long)
}
