package com.dutongjian.app.data

import android.content.Context
import com.dutongjian.app.data.local.ItemDao
import com.dutongjian.app.data.local.ItemEntity
import com.dutongjian.app.data.local.toDomain
import com.dutongjian.app.data.network.DutongjianApi
import com.dutongjian.app.data.network.ItemDto
import com.dutongjian.app.domain.model.HomeFeed
import com.dutongjian.app.domain.model.KnowledgeEntry
import com.dutongjian.app.domain.model.LibrarySection
import com.dutongjian.app.domain.model.OfflineSeed
import com.dutongjian.app.domain.model.ReadingItem
import com.dutongjian.app.domain.model.ReadingYear
import com.dutongjian.app.domain.model.Volume
import com.dutongjian.app.domain.repository.ReadingRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.zip.GZIPInputStream
import javax.inject.Inject

class ReadingRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
    private val api: DutongjianApi,
    private val dao: ItemDao,
) : ReadingRepository {
    private val localContentMutex = Mutex()
    private var localContentReady = false

    override fun observeItems(): Flow<List<ReadingItem>> = flow {
        ensureLocalSeed()
        emitAll(dao.observeAll().map { items -> items.map(ItemEntity::toDomain) })
    }

    override suspend fun refreshHome(): Result<HomeFeed> = withOfflineFallback(
        remote = {
            val response = api.home()
            require(response.code == 0) { response.message }
            val data = requireNotNull(response.data) { "empty home response" }
            val existing = dao.observeAllOnce()
            val incoming = data.items.map { it.toEntity(existing[it.id]) }
            dao.upsertAll(incoming)
            HomeFeed(incoming.map(ItemEntity::toDomain), data.categories)
        },
        fallback = {
            val items = localItems()
            HomeFeed(items, items.map(ReadingItem::category).distinct().sorted())
        },
    )

    override suspend fun search(query: String): Result<List<ReadingItem>> = withOfflineFallback(
        remote = {
            val response = api.search(query)
            require(response.code == 0) { response.message }
            response.data?.items.orEmpty().map { it.toEntity() }.also { dao.upsertAll(it) }.map(ItemEntity::toDomain)
        },
        fallback = { localItems(query = query) },
    )

    override suspend fun setFavorite(itemId: String, favorite: Boolean) = dao.setFavorite(itemId, favorite)

    override suspend fun recordOpened(itemId: String) = dao.recordOpened(itemId, System.currentTimeMillis())

    override suspend fun loadSections(): Result<List<LibrarySection>> = withOfflineFallback(
        remote = {
            val response = api.sections()
            require(response.code == 0) { response.message }
            response.data?.sections.orEmpty().map { LibrarySection(it.id, it.title, it.description, it.source_url, it.sort_order) }
        },
        fallback = { OfflineSeed.sections },
    )

    override suspend fun loadVolumes(sectionId: String): Result<List<Volume>> = withOfflineFallback(
        remote = {
            val response = api.volumes(sectionId)
            require(response.code == 0) { response.message }
            response.data?.volumes.orEmpty().map { Volume(it.id, it.section_id, it.title, it.dynasty, it.sort_order) }
        },
        fallback = { OfflineSeed.volumes.filter { it.sectionId == sectionId } },
    )

    override suspend fun loadYears(volumeId: String): Result<List<ReadingYear>> = withOfflineFallback(
        remote = {
            val response = api.years(volumeId)
            require(response.code == 0) { response.message }
            response.data?.years.orEmpty().map { ReadingYear(it.id, it.volume_id, it.title, it.era, it.sort_order) }
        },
        fallback = { OfflineSeed.years.filter { it.volumeId == volumeId } },
    )

    override suspend fun loadYearItems(yearId: String): Result<List<ReadingItem>> = withOfflineFallback(
        remote = {
            val response = api.yearItems(yearId)
            require(response.code == 0) { response.message }
            val existing = dao.observeAllOnce()
            response.data?.items.orEmpty().map { it.toEntity(existing[it.id]) }.also { dao.upsertAll(it) }.map(ItemEntity::toDomain)
        },
        fallback = { localItems(yearId = yearId) },
    )

    override suspend fun loadKnowledge(query: String?, category: String?): Result<List<KnowledgeEntry>> = withOfflineFallback(
        remote = {
            val response = api.knowledge(query, category)
            require(response.code == 0) { response.message }
            response.data?.items.orEmpty().map {
                KnowledgeEntry(it.id, it.title, it.category, it.summary, it.content, it.source_url, it.updated_at)
            }
        },
        fallback = {
            val needle = query?.trim()?.lowercase()?.takeIf(String::isNotBlank)
            OfflineSeed.knowledge.filter { entry ->
                (category == null || entry.category == category) &&
                    (needle == null || listOf(entry.title, entry.summary, entry.content).any { it.lowercase().contains(needle) })
            }
        },
    )

    private suspend fun <T> withOfflineFallback(
        remote: suspend () -> T,
        fallback: suspend () -> T,
    ): Result<T> = try {
        Result.success(remote())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        Result.success(fallback())
    }

    private suspend fun ensureLocalSeed() {
        localContentMutex.withLock {
            if (localContentReady) return@withLock
            val existing = dao.observeAllOnce()
            val missing = OfflineSeed.items.filterNot { it.id in existing }
            if (missing.isNotEmpty()) {
                dao.upsertAll(missing.map { it.toEntity() })
            }
            importBundledContent()
            localContentReady = true
        }
    }

    private suspend fun importBundledContent() {
        if (dao.fullContentCount() >= FULL_CONTENT_COUNT) return
        try {
            withContext(Dispatchers.IO) {
                context.assets.open(FULL_CONTENT_ASSET).use { input ->
                    GZIPInputStream(input).bufferedReader().use { reader ->
                        val batch = ArrayList<ItemEntity>(ASSET_BATCH_SIZE)
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isBlank()) continue
                            batch += json.decodeFromString<ItemDto>(line).toEntity()
                            if (batch.size == ASSET_BATCH_SIZE) {
                                dao.upsertAll(batch.toList())
                                batch.clear()
                            }
                        }
                        if (batch.isNotEmpty()) dao.upsertAll(batch)
                    }
                }
            }
        } catch (_: Exception) {
            // A build without the generated asset remains usable through OfflineSeed and Room cache.
        }
    }

    private suspend fun localItems(query: String? = null, yearId: String? = null): List<ReadingItem> {
        ensureLocalSeed()
        val needle = query?.trim()?.lowercase()?.takeIf(String::isNotBlank)
        return dao.observeAll().first()
            .map(ItemEntity::toDomain)
            .filter { item ->
                (yearId == null || item.yearId == yearId) &&
                    (needle == null || listOf(item.title, item.summary, item.content, item.dynasty).any { it.lowercase().contains(needle) })
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

    private fun ReadingItem.toEntity(previous: ItemEntity? = null) = ItemEntity(
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
        original = original,
        translation = translation,
        notes = notes,
        tags = tags.joinToString("|"),
        isFavorite = previous?.isFavorite ?: isFavorite,
        lastOpenedAt = previous?.lastOpenedAt ?: lastOpenedAt,
    )
}

private const val FULL_CONTENT_ASSET = "offline_content.ndjson.gz"
private const val FULL_CONTENT_COUNT = 30_989
private const val ASSET_BATCH_SIZE = 500
