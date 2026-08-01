package com.dutongjian.app.data

import com.dutongjian.app.data.local.ItemDao
import com.dutongjian.app.data.local.ItemEntity
import com.dutongjian.app.data.local.toDomain
import com.dutongjian.app.data.network.DutongjianApi
import com.dutongjian.app.data.network.ItemDto
import com.dutongjian.app.domain.model.HomeFeed
import com.dutongjian.app.domain.model.KnowledgeEntry
import com.dutongjian.app.domain.model.LibrarySection
import com.dutongjian.app.domain.model.ReadingItem
import com.dutongjian.app.domain.model.ReadingYear
import com.dutongjian.app.domain.model.Volume
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

    override suspend fun loadSections(): Result<List<LibrarySection>> = runCatching {
        val response = api.sections()
        require(response.code == 0) { response.message }
        response.data?.sections.orEmpty().map { LibrarySection(it.id, it.title, it.description, it.source_url, it.sort_order) }
    }

    override suspend fun loadVolumes(sectionId: String): Result<List<Volume>> = runCatching {
        val response = api.volumes(sectionId)
        require(response.code == 0) { response.message }
        response.data?.volumes.orEmpty().map { Volume(it.id, it.section_id, it.title, it.dynasty, it.sort_order) }
    }

    override suspend fun loadYears(volumeId: String): Result<List<ReadingYear>> = runCatching {
        val response = api.years(volumeId)
        require(response.code == 0) { response.message }
        response.data?.years.orEmpty().map { ReadingYear(it.id, it.volume_id, it.title, it.era, it.sort_order) }
    }

    override suspend fun loadYearItems(yearId: String): Result<List<ReadingItem>> = runCatching {
        val response = api.yearItems(yearId)
        require(response.code == 0) { response.message }
        val existing = dao.observeAllOnce()
        response.data?.items.orEmpty().map { it.toEntity(existing[it.id]) }.also { dao.upsertAll(it) }.map(ItemEntity::toDomain)
    }

    override suspend fun loadKnowledge(query: String?, category: String?): Result<List<KnowledgeEntry>> = runCatching {
        val response = api.knowledge(query, category)
        require(response.code == 0) { response.message }
        response.data?.items.orEmpty().map {
            KnowledgeEntry(it.id, it.title, it.category, it.summary, it.content, it.source_url, it.updated_at)
        }
    }

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
        section = section,
        volumeId = volume_id,
        yearId = year_id,
        original = original,
        translation = translation,
        notes = notes,
        tags = tags.joinToString("|"),
        isFavorite = previous?.isFavorite ?: false,
        lastOpenedAt = previous?.lastOpenedAt,
    )
}
