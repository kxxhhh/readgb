package com.dutongjian.app.domain.repository

import com.dutongjian.app.domain.model.HomeFeed
import com.dutongjian.app.domain.model.ReadingItem
import kotlinx.coroutines.flow.Flow

interface ReadingRepository {
    fun observeItems(): Flow<List<ReadingItem>>
    suspend fun refreshHome(): Result<HomeFeed>
    suspend fun search(query: String): Result<List<ReadingItem>>
    suspend fun setFavorite(itemId: String, favorite: Boolean)
    suspend fun recordOpened(itemId: String)
}
