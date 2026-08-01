package com.dutongjian.app.data

import com.dutongjian.app.data.local.ItemDao
import com.dutongjian.app.data.local.ItemEntity
import com.dutongjian.app.data.local.toDomain
import com.dutongjian.app.data.network.DutongjianApi
import com.dutongjian.app.data.network.ItemDto
import com.dutongjian.app.domain.model.HomeFeed
import com.dutongjian.app.domain.model.ReadingItem
import com.dutongjian.app.domain.repository.ReadingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ReadingRepositoryImpl @Inject constructor(
    private val api: DutongjianApi,
    private val dao: ItemDao,
) : ReadingRepository {
    override fun observeItems(): Flow<List<ReadingItem>> = dao.observeAll().map { items -> items.map(ItemEntity::toDomain) }

    override suspend fun refreshHome(): Result<HomeFeed> = runCatching {
        val response = api.home()
        require(response.code == 0) { response.message }
        val data = requireNotNull(response.data) { "empty home response" }
        val existing = dao.observeAllOnce()
        val incoming = data.items.map { it.toEntity(existing[it.id]) }
        dao.upsertAll(incoming)
        HomeFeed(incoming.map(ItemEntity::toDomain), data.categories)
    }

    override suspend fun search(query: String): Result<List<ReadingItem>> = runCatching {
        val response = api.search(query)
        require(response.code == 0) { response.message }
        response.data?.items.orEmpty().map { it.toEntity() }.also { dao.upsertAll(it) }.map(ItemEntity::toDomain)
    }

    override suspend fun setFavorite(itemId: String, favorite: Boolean) = dao.setFavorite(itemId, favorite)

    override suspend fun recordOpened(itemId: String) = dao.recordOpened(itemId, System.currentTimeMillis())

    private suspend fun ItemDao.observeAllOnce(): Map<String, ItemEntity> = observeAll().first().associateBy { it.id }

    private fun ItemDto.toEntity(previous: ItemEntity? = null) = ItemEntity(
        id = id,
        title = title,
        category = category,
        dynasty = dynasty,
        summary = summary,
        content = content,
        sourceUrl = source_url,
        updatedAt = updated_at,
        isFavorite = previous?.isFavorite ?: false,
        lastOpenedAt = previous?.lastOpenedAt,
    )
}
