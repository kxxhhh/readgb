package com.dutongjian.app.data

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.dutongjian.app.data.local.AppDatabase
import com.dutongjian.app.data.local.ItemDao
import com.dutongjian.app.data.local.ItemEntity
import com.dutongjian.app.data.local.ItemLocalState
import com.dutongjian.app.data.local.ItemSummaryEntity
import com.dutongjian.app.data.local.AiResultDao
import com.dutongjian.app.data.local.NoteDao
import com.dutongjian.app.data.local.PlaceDao
import com.dutongjian.app.data.local.toDomain
import com.dutongjian.app.data.local.toEntity
import com.dutongjian.app.data.network.DutongjianApi
import com.dutongjian.app.data.network.ItemDto
import com.dutongjian.app.data.network.KnowledgeDto
import com.dutongjian.app.data.network.SectionDto
import com.dutongjian.app.data.network.VolumeDto
import com.dutongjian.app.data.network.YearDto
import com.dutongjian.app.domain.model.HomeFeed
import com.dutongjian.app.domain.model.AiResult
import com.dutongjian.app.domain.model.HistoricalPlace
import com.dutongjian.app.domain.model.KnowledgeEntry
import com.dutongjian.app.domain.model.LibrarySection
import com.dutongjian.app.domain.model.OfflineSeed
import com.dutongjian.app.domain.model.Note
import com.dutongjian.app.domain.model.PlaceCatalog
import com.dutongjian.app.domain.model.ReadingItem
import com.dutongjian.app.domain.model.ReadingYear
import com.dutongjian.app.domain.model.Volume
import com.dutongjian.app.domain.repository.ReadingRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.sqlite.db.SimpleSQLiteQuery
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
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.decodeToSequence
import java.io.IOException
import javax.inject.Inject

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
class ReadingRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
    private val api: DutongjianApi,
    private val database: AppDatabase,
    private val dao: ItemDao,
    private val placeDao: PlaceDao,
    private val noteDao: NoteDao,
    private val aiResultDao: AiResultDao,
) : ReadingRepository {
    private val localContentMutex = Mutex()
    private val catalogMutex = Mutex()
    private val knowledgeMutex = Mutex()
    private var localContentReady = false
    private var bundledCatalog: OfflineCatalogAsset? = null
    private var bundledKnowledge: List<KnowledgeEntry>? = null
    private val assetPreferences = context.getSharedPreferences(OFFLINE_ASSET_PREFERENCES, Context.MODE_PRIVATE)

    override fun observeItems(): Flow<List<ReadingItem>> = flow {
        ensureLocalSeed()
        emitAll(dao.observeSummaries().map { items -> items.map(ItemSummaryEntity::toDomain) })
    }

    override suspend fun refreshHome(): Result<HomeFeed> = withOfflineFallback(
        remote = {
            val response = api.home()
            require(response.code == 0) { response.message }
            val data = requireNotNull(response.data) { "empty home response" }
            val existing = dao.importStatesOnce()
            val incoming = data.items.map { it.toImportedEntity(existing[it.id]) }
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

    override suspend fun loadItem(itemId: String): Result<ReadingItem> = runCatching {
        requireNotNull(dao.findById(itemId)?.toDomain()) { "本地条目不存在：$itemId" }
    }

    override suspend fun loadSections(): Result<List<LibrarySection>> = runCatching {
        bundledCatalog().sections.map { it.toDomain() }.ifEmpty { OfflineSeed.sections }
    }

    override suspend fun loadVolumes(sectionId: String): Result<List<Volume>> = runCatching {
        offlineVolumes(sectionId)
    }

    override suspend fun loadYears(volumeId: String): Result<List<ReadingYear>> = runCatching {
        offlineYears(volumeId)
    }

    override suspend fun loadAllYears(): Result<List<ReadingYear>> = runCatching {
        offlineAllYears()
    }

    override suspend fun loadYearItems(yearId: String): Result<List<ReadingItem>> {
        val cachedItems = localItems(yearId = yearId)
        if (cachedItems.isNotEmpty()) return Result.success(cachedItems)
        val seedItems = OfflineSeed.items.filter { it.yearId == yearId }
        if (seedItems.isNotEmpty()) return Result.success(seedItems)
        return withOfflineFallback(
            remote = {
                val response = api.yearItems(yearId)
                require(response.code == 0) { response.message }
                val existing = dao.importStatesOnce()
                response.data?.items.orEmpty().map { it.toImportedEntity(existing[it.id]) }.also { dao.upsertAll(it) }.map(ItemEntity::toDomain)
            },
            fallback = { localItems(yearId = yearId) },
            isUsable = { it.isNotEmpty() },
        )
    }

    override suspend fun loadKnowledge(query: String?, category: String?): Result<List<KnowledgeEntry>> {
        val cachedEntries = offlineKnowledge(query, category)
        if (cachedEntries.isNotEmpty()) return Result.success(cachedEntries)
        return withOfflineFallback(
            remote = {
                val response = api.knowledge(query, category)
                require(response.code == 0) { response.message }
                response.data?.items.orEmpty().map {
                    KnowledgeEntry(it.id, it.title, it.category, it.summary, it.content, it.source_url, it.updated_at)
                }
            },
            fallback = { offlineKnowledge(query, category) },
            isUsable = { it.isNotEmpty() },
        )
    }

    override fun observeNotes(): Flow<List<Note>> = noteDao.observeAll().map { notes -> notes.map { it.toDomain() } }

    override suspend fun saveNote(note: Note) = noteDao.upsert(note.toEntity())

    override suspend fun deleteNote(note: Note) = noteDao.delete(note.toEntity())

    override fun observeAiResults(): Flow<List<AiResult>> = aiResultDao.observeAll().map { results ->
        results.map { it.toDomain() }
    }

    override suspend fun saveAiResult(result: AiResult) = aiResultDao.upsert(result.toEntity())

    override suspend fun deleteAiResult(result: AiResult) = aiResultDao.deleteById(result.id)

    override fun observePlaces(): Flow<List<HistoricalPlace>> = flow {
        ensurePlaces()
        emitAll(placeDao.observeAll().map { places -> places.map { it.toDomain() } })
    }

    private suspend fun <T> withOfflineFallback(
        remote: suspend () -> T,
        fallback: suspend () -> T,
        isUsable: (T) -> Boolean = { true },
    ): Result<T> = try {
        val value = remote()
        if (isUsable(value)) Result.success(value) else Result.success(fallback())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        Result.success(fallback())
    }

    private suspend fun ensureLocalSeed() {
        localContentMutex.withLock {
            if (localContentReady) return@withLock
            val hasBundledContent = hasBundledContentAsset()
            val existingIds = dao.existingIds().toSet()
            val missing = OfflineSeed.items
                .filterNot { it.id in existingIds }
                .filter { !hasBundledContent || it.section != "资治通鉴" }
            if (missing.isNotEmpty()) {
                dao.upsertAll(missing.map { it.toEntity() })
            }
            val importedVersion = assetPreferences.getString(OFFLINE_ASSET_VERSION_KEY, null)
            if (hasBundledContent && (importedVersion != OFFLINE_ASSET_VERSION || dao.fullContentCount() < OFFLINE_CONTENT_RECORD_COUNT)) {
                if (importBundledContent()) {
                    assetPreferences.edit().putString(OFFLINE_ASSET_VERSION_KEY, OFFLINE_ASSET_VERSION).apply()
                }
            }
            localContentReady = true
        }
        ensurePlaces()
    }

    private suspend fun ensurePlaces() {
        // Upsert on startup so catalog additions reach already-installed databases.
        placeDao.upsertAll(PlaceCatalog.entries.map { it.toEntity() })
    }

    private suspend fun importBundledContent(): Boolean {
        try {
            withContext(Dispatchers.IO) {
                // Validate the complete stream before touching Room. This catches
                // truncated assets without replacing a working local corpus.
                streamBundledContent { }
                val previous = dao.importStatesOnce()
                database.withTransaction {
                    dao.deleteImportedContent()
                    streamBundledContent { batch ->
                        dao.upsertAll(batch.map { it.toImportedEntity(previous[it.id]) })
                    }
                }
            }
            return true
        } catch (error: IOException) {
            Log.e(LOG_TAG, "Offline content asset is unavailable; keeping Room fallback", error)
        } catch (error: Exception) {
            Log.e(LOG_TAG, "Offline content asset import failed; keeping Room fallback", error)
        }
        return false
    }

    private fun openBundledContentAsset() = try {
        context.assets.open(CONTENT_ASSET)
    } catch (_: IOException) {
        context.assets.open(LEGACY_CONTENT_ASSET)
    }

    private fun hasBundledContentAsset(): Boolean = try {
        context.assets.open(CONTENT_ASSET).use { }
        true
    } catch (_: IOException) {
        try {
            context.assets.open(LEGACY_CONTENT_ASSET).use { }
            true
        } catch (_: IOException) {
            false
        }
    }

    private suspend fun bundledCatalog(): OfflineCatalogAsset = catalogMutex.withLock {
        bundledCatalog ?: run {
            val loaded = try {
                withContext(Dispatchers.IO) {
                    context.assets.open(OFFLINE_CATALOG_ASSET).bufferedReader().use { reader ->
                        json.decodeFromString<OfflineCatalogAsset>(reader.readText())
                    }
                }
            } catch (_: Exception) {
                OfflineCatalogAsset(
                    sections = OfflineSeed.sections.map { SectionDto(it.id, it.title, it.description, it.sourceUrl, it.sortOrder) },
                    volumes = OfflineSeed.volumes.map { VolumeDto(it.id, it.sectionId, it.title, it.dynasty, it.sortOrder) },
                    years = OfflineSeed.years.map { YearDto(it.id, it.volumeId, it.title, it.era, it.sortOrder, it.yearInt) },
                )
            }
            bundledCatalog = loaded
            loaded
        }
    }

    private suspend fun offlineVolumes(sectionId: String): List<Volume> {
        val bundled = bundledCatalog().volumes
            .filter { it.section_id == sectionId }
            .map { it.toDomain() }
        return if (bundled.isNotEmpty()) {
            bundled
        } else {
            OfflineSeed.volumes.filter { it.sectionId == sectionId }.sortedBy { it.sortOrder }
        }
    }

    private suspend fun offlineYears(volumeId: String): List<ReadingYear> {
        val bundled = bundledCatalog().years
            .filter { it.volume_id == volumeId }
            .map { it.toDomain() }
        return if (bundled.isNotEmpty()) {
            bundled
        } else {
            OfflineSeed.years.filter { it.volumeId == volumeId }.sortedBy { it.sortOrder }
        }
    }

    private suspend fun offlineAllYears(): List<ReadingYear> {
        val bundled = bundledCatalog().years.map { it.toDomain() }
        val seed = OfflineSeed.years.filterNot { it.volumeId.startsWith("zizhi-") }
        return (bundled + seed)
            .distinctBy(ReadingYear::id)
            .sortedBy { it.yearInt ?: Int.MAX_VALUE }
    }

    private suspend fun bundledKnowledgeEntries(): List<KnowledgeEntry> = knowledgeMutex.withLock {
        bundledKnowledge ?: run {
            val loaded = try {
                withContext(Dispatchers.IO) {
                    context.assets.open(OFFLINE_KNOWLEDGE_ASSET).use { input ->
                        json.decodeToSequence<KnowledgeDto>(input, DecodeSequenceMode.ARRAY_WRAPPED)
                            .map { it.toDomain() }
                            .toList()
                    }
                }
            } catch (error: Exception) {
                Log.w(LOG_TAG, "Offline knowledge asset is unavailable; using seed entries", error)
                emptyList()
            }
            bundledKnowledge = loaded
            loaded
        }
    }

    private suspend fun offlineKnowledge(query: String?, category: String?): List<KnowledgeEntry> {
        val needle = query?.trim()?.lowercase()?.takeIf(String::isNotBlank)
        val source = bundledKnowledgeEntries().ifEmpty { OfflineSeed.knowledge }
        return source.filter { entry ->
            (category == null || entry.category == category) &&
                (needle == null || listOf(entry.title, entry.summary, entry.content).any { it.lowercase().contains(needle) })
        }
    }

    private suspend fun localItems(query: String? = null, yearId: String? = null): List<ReadingItem> {
        ensureLocalSeed()
        val needle = query?.trim()?.lowercase()?.takeIf(String::isNotBlank)
        val indexed = if (needle == null) {
            emptyList()
        } else {
            runCatching {
                dao.searchFtsSummaries(
                    SimpleSQLiteQuery(
                        "SELECT reading_items.id, reading_items.title, reading_items.category, reading_items.dynasty, " +
                            "reading_items.summary, reading_items.sourceUrl, reading_items.updatedAt, reading_items.section, " +
                            "reading_items.volumeId, reading_items.yearId, reading_items.tags, reading_items.isFavorite, " +
                            "reading_items.lastOpenedAt FROM reading_items JOIN reading_items_fts ON reading_items_fts.id = reading_items.id " +
                            "WHERE reading_items_fts MATCH ? ORDER BY reading_items.updatedAt DESC, reading_items.title ASC LIMIT 200",
                        arrayOf(ftsQuery(needle)),
                    ),
                )
            }.getOrDefault(emptyList())
        }
        val source = if (indexed.isNotEmpty()) {
            indexed
        } else if (yearId != null) {
            dao.findSummariesByYear(yearId)
        } else {
            dao.observeSummaries().first()
        }
        val localItems = source.map(ItemSummaryEntity::toDomain)
        val seedItems = yearId?.let { requestedYearId ->
            OfflineSeed.items.filter { it.yearId == requestedYearId }
        }.orEmpty()
        return (localItems + seedItems)
            .distinctBy(ReadingItem::id)
            .filter { item ->
                (yearId == null || item.yearId == yearId) &&
                    (needle == null || listOf(
                        item.title,
                        item.summary,
                        item.dynasty,
                        item.tags.joinToString(" "),
                    ).any { it.lowercase().contains(needle) })
            }
    }

    private fun ftsQuery(value: String): String = value
        .split(Regex("\\s+"))
        .filter(String::isNotBlank)
        .joinToString(" AND ") { token ->
            val clean = token.filter { it.isLetterOrDigit() }
            "$clean*"
        }

    private suspend fun ItemDao.importStatesOnce(): Map<String, ItemLocalState> = importStates().associateBy { it.id }

    private suspend fun streamBundledContent(onBatch: suspend (List<ItemDto>) -> Unit) {
        var count = 0
        val seenIds = HashSet<String>(OFFLINE_CONTENT_RECORD_COUNT)
        val batch = ArrayList<ItemDto>(ASSET_BATCH_SIZE)
        openBundledContentAsset().use { input ->
            contentAssetReader(input).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) continue
                    val dto = json.decodeFromString<ItemDto>(line)
                    require(seenIds.add(dto.id)) { "duplicate offline content id: ${dto.id}" }
                    count += 1
                    batch += dto
                    if (batch.size == ASSET_BATCH_SIZE) {
                        onBatch(batch.toList())
                        batch.clear()
                    }
                }
            }
        }
        if (batch.isNotEmpty()) onBatch(batch.toList())
        require(count == OFFLINE_CONTENT_RECORD_COUNT) {
            "offline content record count mismatch: expected $OFFLINE_CONTENT_RECORD_COUNT, got $count"
        }
    }

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

    private fun ItemDto.toImportedEntity(previous: ItemLocalState? = null) = ItemEntity(
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

    private fun SectionDto.toDomain() = LibrarySection(id, title, description, source_url, sort_order)

    private fun VolumeDto.toDomain() = Volume(id, section_id, title, dynasty, sort_order)

    private fun YearDto.toDomain() = ReadingYear(id, volume_id, title, era, sort_order, year_int)

    private fun KnowledgeDto.toDomain() = KnowledgeEntry(id, title, category, summary, content, source_url, updated_at)

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

private const val CONTENT_ASSET = "offline_content.ndjson"
private const val LEGACY_CONTENT_ASSET = "offline_content.ndjson.gz"
private const val OFFLINE_CATALOG_ASSET = "offline_catalog.json"
private const val OFFLINE_KNOWLEDGE_ASSET = "offline_knowledge.json"
private const val OFFLINE_CONTENT_RECORD_COUNT = 48_126
private const val OFFLINE_ASSET_VERSION = "2026-08-04-extended-crawled-912-topics-2"
private const val OFFLINE_ASSET_PREFERENCES = "offline_assets"
private const val OFFLINE_ASSET_VERSION_KEY = "content_version"
private const val ASSET_BATCH_SIZE = 500
private const val LOG_TAG = "ReadingRepository"

@kotlinx.serialization.Serializable
private data class OfflineCatalogAsset(
    val sections: List<com.dutongjian.app.data.network.SectionDto> = emptyList(),
    val volumes: List<com.dutongjian.app.data.network.VolumeDto> = emptyList(),
    val years: List<com.dutongjian.app.data.network.YearDto> = emptyList(),
)
