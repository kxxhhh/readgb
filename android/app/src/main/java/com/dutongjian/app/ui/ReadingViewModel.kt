package com.dutongjian.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dutongjian.app.domain.model.KnowledgeEntry
import com.dutongjian.app.domain.model.HistoricalPlace
import com.dutongjian.app.domain.model.AiSettings
import com.dutongjian.app.domain.model.AiResult
import com.dutongjian.app.domain.model.AiTask
import com.dutongjian.app.domain.model.AiConversationTurn
import com.dutongjian.app.domain.model.LibrarySection
import com.dutongjian.app.domain.model.OfflineSeed
import com.dutongjian.app.domain.model.ReadingItem
import com.dutongjian.app.domain.model.ReadingYear
import com.dutongjian.app.domain.model.ReadingStats
import com.dutongjian.app.domain.model.Note
import com.dutongjian.app.domain.model.TtsEngineType
import com.dutongjian.app.domain.model.Volume
import com.dutongjian.app.domain.repository.ReadingRepository
import com.dutongjian.app.domain.repository.AiRepository
import com.dutongjian.app.domain.tts.TtsPlaybackState
import com.dutongjian.app.domain.tts.TtsPlayer
import com.dutongjian.app.data.ReadingStatsRecorder
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

enum class LibraryTab { FAVORITES, HISTORY, NOTES }
enum class CatalogLevel { SECTIONS, VOLUMES, YEARS, ITEMS }

data class AiUiState(
    val settings: AiSettings = AiSettings(),
    val baseUrl: String = settings.baseUrl,
    val model: String = settings.model,
    val apiKeyInput: String = "",
    val isSaving: Boolean = false,
    val isGenerating: Boolean = false,
    val task: AiTask? = null,
    val resultItemId: String? = null,
    val resultId: String? = null,
    val result: String? = null,
    val conversation: List<AiConversationTurn> = emptyList(),
    val error: String? = null,
    val notice: String? = null,
)

data class ReadingUiState(
    val items: List<ReadingItem> = OfflineSeed.items,
    val query: String = "",
    val searchResultIds: Set<String>? = null,
    val selectedCategory: String? = null,
    val featuredOffset: Int = 0,
    val categories: List<String> = OfflineSeed.categories,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val libraryTab: LibraryTab = LibraryTab.FAVORITES,
    val sections: List<LibrarySection> = OfflineSeed.sections,
    val volumes: List<Volume> = emptyList(),
    val years: List<ReadingYear> = emptyList(),
    val timelineYears: List<ReadingYear> = OfflineSeed.years,
    val catalogItems: List<ReadingItem> = emptyList(),
    val catalogLevel: CatalogLevel = CatalogLevel.SECTIONS,
    val selectedSectionId: String? = null,
    val selectedVolumeId: String? = null,
    val selectedYearId: String? = null,
    val knowledge: List<KnowledgeEntry> = OfflineSeed.knowledge,
    val knowledgeCategories: List<String> = OfflineSeed.knowledge.map(KnowledgeEntry::category).distinct().sorted(),
    val knowledgeCategoryCounts: Map<String, Int> = OfflineSeed.knowledge.groupingBy(KnowledgeEntry::category).eachCount(),
    val knowledgeQuery: String = "",
    val selectedKnowledgeCategory: String? = null,
    val notes: List<Note> = emptyList(),
    val aiResults: List<AiResult> = emptyList(),
    val places: List<HistoricalPlace> = emptyList(),
    val stats: ReadingStats = ReadingStats(),
    val isCatalogLoading: Boolean = false,
    val isKnowledgeLoading: Boolean = false,
    val tts: TtsPlaybackState = TtsPlaybackState(),
    val ai: AiUiState = AiUiState(),
)

@HiltViewModel
class ReadingViewModel @Inject constructor(
    private val repository: ReadingRepository,
    private val aiRepository: AiRepository,
    private val ttsController: TtsPlayer,
    private val statsStore: ReadingStatsRecorder,
) : ViewModel() {
    private var knowledgeCache = OfflineSeed.knowledge
    private var knowledgeRequest: Job? = null
    private val sectionCache = mutableListOf<LibrarySection>()
    private val volumeCache = mutableMapOf<String, List<Volume>>()
    private val yearCache = mutableMapOf<String, List<ReadingYear>>()
    private val itemCache = mutableMapOf<String, List<ReadingItem>>()
    private val loadedItemDetails = mutableMapOf<String, ReadingItem>()
    private val _state = MutableStateFlow(
        ReadingUiState(
            items = OfflineSeed.items,
            categories = OfflineSeed.categories,
            sections = OfflineSeed.sections,
            knowledge = OfflineSeed.knowledge,
            knowledgeCategories = OfflineSeed.knowledge.map(KnowledgeEntry::category).distinct().sorted(),
            knowledgeCategoryCounts = OfflineSeed.knowledge.groupingBy(KnowledgeEntry::category).eachCount(),
            timelineYears = OfflineSeed.years,
            isLoading = true,
        ),
    )
    val state: StateFlow<ReadingUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeItems().collectLatest { items ->
                val mergedItems = items.map(::mergeLoadedDetail)
                _state.update {
                    it.copy(
                        items = mergedItems,
                        isLoading = false,
                        featuredOffset = if (mergedItems.isEmpty()) 0 else it.featuredOffset % mergedItems.size,
                    )
                }
            }
        }
        viewModelScope.launch {
            repository.observeNotes().collectLatest { notes -> _state.update { it.copy(notes = notes) } }
        }
        viewModelScope.launch {
            repository.observeAiResults().collectLatest { results -> _state.update { it.copy(aiResults = results) } }
        }
        viewModelScope.launch {
            repository.observePlaces().collectLatest { places -> _state.update { it.copy(places = places) } }
        }
        viewModelScope.launch {
            ttsController.state.collectLatest { tts -> _state.update { it.copy(tts = tts) } }
        }
        _state.update { it.copy(stats = statsStore.snapshot()) }
        refresh()
        loadSections()
        viewModelScope.launch {
            repository.loadAllYears().onSuccess { years ->
                _state.update { it.copy(timelineYears = years) }
            }
        }
        loadKnowledge()
        loadAiSettings()
    }

    fun refresh() = viewModelScope.launch {
        _state.update { it.copy(isRefreshing = true, isLoading = true) }
        repository.refreshHome().onSuccess { feed ->
            _state.update { it.copy(categories = feed.categories, error = null, isRefreshing = false) }
        }.onFailure { failure ->
            _state.update { it.copy(error = failure.message ?: "同步失败，已显示本地缓存", isLoading = false, isRefreshing = false) }
        }
    }

    fun search(value: String) {
        val query = value.trim()
        _state.update {
            it.copy(
                query = value,
                searchResultIds = if (query.length < 2) null else it.searchResultIds,
            )
        }
        if (query.length < 2) return
        viewModelScope.launch {
            repository.search(query)
                .onSuccess { results ->
                    _state.update {
                        it.copy(
                            searchResultIds = results.map(ReadingItem::id).toSet(),
                            error = null,
                        )
                    }
                }
                .onFailure { failure ->
                    _state.update { it.copy(error = failure.message ?: "搜索失败") }
                }
        }
    }

    fun selectCategory(category: String?) { _state.update { it.copy(selectedCategory = category) } }
    fun selectLibraryTab(tab: LibraryTab) { _state.update { it.copy(libraryTab = tab) } }

    fun cycleFeatured() {
        _state.update { current ->
            if (current.items.size <= 1) current
            else current.copy(featuredOffset = (current.featuredOffset + 5) % current.items.size)
        }
    }

    fun toggleFavorite(item: ReadingItem) = viewModelScope.launch {
        repository.setFavorite(item.id, !item.isFavorite)
    }

    fun open(item: ReadingItem) = viewModelScope.launch {
        val detail = repository.loadItem(item.id).getOrNull() ?: item
        val current = _state.value.items.firstOrNull { it.id == detail.id }
        val loaded = detail.copy(
            isFavorite = current?.isFavorite ?: detail.isFavorite,
            lastOpenedAt = current?.lastOpenedAt ?: detail.lastOpenedAt,
        )
        loadedItemDetails[loaded.id] = loaded
        _state.update { state ->
            state.copy(
                items = state.items.map { existing ->
                    if (existing.id == loaded.id) loaded else existing
                },
                stats = statsStore.open(loaded),
            )
        }
        recordOpenedSafely(item.id)
    }

    private suspend fun recordOpenedSafely(itemId: String) {
        try {
            repository.recordOpened(itemId)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Reading should remain available when a legacy/seed row has no local state yet.
        }
    }

    fun stopReadingSession() {
        _state.update { it.copy(stats = statsStore.close()) }
    }

    private fun mergeLoadedDetail(item: ReadingItem): ReadingItem {
        val detail = loadedItemDetails[item.id] ?: return item
        return detail.copy(
            title = item.title,
            category = item.category,
            dynasty = item.dynasty,
            summary = item.summary,
            sourceUrl = item.sourceUrl,
            updatedAt = item.updatedAt,
            section = item.section,
            volumeId = item.volumeId,
            yearId = item.yearId,
            tags = item.tags,
            isFavorite = item.isFavorite,
            lastOpenedAt = item.lastOpenedAt,
        )
    }

    fun selectTtsEngine(engine: TtsEngineType) = ttsController.selectEngine(engine)

    fun speak(item: ReadingItem) = ttsController.speak(_state.value.items, item)

    fun pauseTts() = ttsController.pause()

    fun resumeTts() = ttsController.resume()

    fun stopTts() = ttsController.stop()

    fun startTtsSleepTimer(minutes: Int) = ttsController.startSleepTimer(minutes)

    fun stopTtsAfterCurrentItem() = ttsController.stopAfterCurrentItem()

    fun cancelTtsSleepTimer() = ttsController.cancelSleepTimer()

    fun saveNote(note: Note) = viewModelScope.launch { repository.saveNote(note) }

    fun deleteNote(note: Note) = viewModelScope.launch { repository.deleteNote(note) }

    fun updateAiBaseUrl(value: String) { _state.update { it.copy(ai = it.ai.copy(baseUrl = value, error = null, notice = null)) } }

    fun updateAiModel(value: String) { _state.update { it.copy(ai = it.ai.copy(model = value, error = null, notice = null)) } }

    fun updateAiApiKey(value: String) { _state.update { it.copy(ai = it.ai.copy(apiKeyInput = value, error = null, notice = null)) } }

    fun saveAiSettings() = viewModelScope.launch {
        val ai = _state.value.ai
        _state.update { it.copy(ai = ai.copy(isSaving = true, error = null, notice = null)) }
        aiRepository.saveSettings(ai.baseUrl, ai.model, ai.apiKeyInput.takeIf(String::isNotBlank))
            .onSuccess { settings ->
                _state.update { it.copy(ai = it.ai.copy(settings = settings, baseUrl = settings.baseUrl, model = settings.model, apiKeyInput = "", isSaving = false, error = null, notice = "AI 设置已保存")) }
            }
            .onFailure { failure ->
                _state.update { it.copy(ai = it.ai.copy(isSaving = false, error = failure.message ?: "AI 设置保存失败", notice = null)) }
            }
    }

    fun clearAiApiKey() = viewModelScope.launch {
        aiRepository.clearApiKey()
            .onSuccess { settings -> _state.update { it.copy(ai = it.ai.copy(settings = settings, apiKeyInput = "", error = null, notice = "API Key 已清除")) } }
            .onFailure { failure -> _state.update { it.copy(ai = it.ai.copy(error = failure.message ?: "API Key 清除失败", notice = null)) } }
    }

    fun generateAi(item: ReadingItem, task: AiTask) = viewModelScope.launch {
        _state.update {
            it.copy(
                ai = it.ai.copy(
                    isGenerating = true,
                    task = task,
                    resultItemId = item.id,
                    resultId = null,
                    result = null,
                    conversation = emptyList(),
                    error = null,
                ),
            )
        }
        aiRepository.generate(item, task)
            .onSuccess { result ->
                val saved = AiResult(
                    id = UUID.randomUUID().toString(),
                    itemId = item.id,
                    task = task,
                    result = result,
                    createdAt = System.currentTimeMillis(),
                )
                repository.saveAiResult(saved)
                _state.update {
                    it.copy(
                        ai = it.ai.copy(
                            isGenerating = false,
                            task = task,
                            resultItemId = item.id,
                            resultId = saved.id,
                            result = result,
                            conversation = if (task == AiTask.ROLE_DIALOGUE) {
                                listOf(AiConversationTurn("首轮角色回答", result))
                            } else {
                                emptyList()
                            },
                            error = null,
                        ),
                    )
                }
            }
            .onFailure { failure -> _state.update { it.copy(ai = it.ai.copy(isGenerating = false, error = failure.message ?: "AI 生成失败")) } }
    }

    fun continueAi(item: ReadingItem, question: String) = viewModelScope.launch {
        val current = _state.value.ai
        val trimmed = question.trim()
        if (current.task != AiTask.ROLE_DIALOGUE || current.resultItemId != item.id) {
            _state.update { it.copy(ai = it.ai.copy(error = "请先生成历史角色首轮回答")) }
            return@launch
        }
        if (trimmed.isBlank()) {
            _state.update { it.copy(ai = it.ai.copy(error = "追问不能为空")) }
            return@launch
        }
        _state.update { it.copy(ai = it.ai.copy(isGenerating = true, error = null)) }
        aiRepository.generate(item, AiTask.ROLE_DIALOGUE, current.conversation, trimmed)
            .onSuccess { response ->
                val turns = current.conversation + AiConversationTurn(trimmed, response)
                val saved = AiResult(
                    id = current.resultId ?: UUID.randomUUID().toString(),
                    itemId = item.id,
                    task = AiTask.ROLE_DIALOGUE,
                    result = renderConversation(turns),
                    createdAt = System.currentTimeMillis(),
                )
                repository.saveAiResult(saved)
                _state.update {
                    it.copy(
                        ai = it.ai.copy(
                            isGenerating = false,
                            resultId = saved.id,
                            result = saved.result,
                            conversation = turns,
                            error = null,
                        ),
                    )
                }
            }
            .onFailure { failure -> _state.update { it.copy(ai = it.ai.copy(isGenerating = false, error = failure.message ?: "AI 追问失败")) } }
    }

    fun clearAiResult() {
        _state.update { it.copy(ai = it.ai.copy(task = null, resultItemId = null, resultId = null, result = null, conversation = emptyList(), error = null)) }
    }

    fun openAiResult(result: AiResult) {
        _state.update {
            it.copy(
                ai = it.ai.copy(
                    task = result.task,
                    resultItemId = result.itemId,
                    resultId = result.id,
                    result = result.result,
                    conversation = if (result.task == AiTask.ROLE_DIALOGUE) {
                        listOf(AiConversationTurn("已保存的角色回答", result.result))
                    } else {
                        emptyList()
                    },
                    error = null,
                ),
            )
        }
    }

    fun deleteAiResult(result: AiResult) = viewModelScope.launch {
        repository.deleteAiResult(result)
        if (_state.value.ai.resultId == result.id) clearAiResult()
    }

    private fun loadAiSettings() = viewModelScope.launch {
        aiRepository.loadSettings()
            .onSuccess { settings -> _state.update { it.copy(ai = it.ai.copy(settings = settings, baseUrl = settings.baseUrl, model = settings.model)) } }
            .onFailure { failure -> _state.update { it.copy(ai = it.ai.copy(error = failure.message ?: "AI 设置读取失败")) } }
    }

    fun loadSections() = viewModelScope.launch {
        if (sectionCache.isNotEmpty()) {
            _state.update { it.copy(sections = sectionCache.toList(), isCatalogLoading = false) }
        } else {
            _state.update { it.copy(isCatalogLoading = false) }
        }
        repository.loadSections().onSuccess { sections ->
            sectionCache.clear()
            sectionCache.addAll(sections)
            _state.update { it.copy(sections = sections, isCatalogLoading = false, error = null) }
        }.onFailure { failure ->
            _state.update { it.copy(isCatalogLoading = false, error = failure.message ?: "目录加载失败") }
        }
    }

    fun selectSection(section: LibrarySection) = viewModelScope.launch {
        val cached = volumeCache[section.id]
        _state.update {
            it.copy(
                selectedSectionId = section.id,
                selectedVolumeId = null,
                selectedYearId = null,
                volumes = cached.orEmpty(),
                years = emptyList(),
                catalogItems = emptyList(),
                catalogLevel = CatalogLevel.VOLUMES,
                isCatalogLoading = cached == null,
            )
        }
        repository.loadVolumes(section.id).onSuccess { volumes ->
            volumeCache[section.id] = volumes
            _state.update { current ->
                if (current.selectedSectionId == section.id) current.copy(volumes = volumes, isCatalogLoading = false) else current
            }
        }.onFailure { failure ->
            _state.update { it.copy(isCatalogLoading = false, error = failure.message ?: "卷目录加载失败") }
        }
    }

    fun selectVolume(volume: Volume) = viewModelScope.launch {
        val cached = yearCache[volume.id]
        _state.update {
            it.copy(
                selectedVolumeId = volume.id,
                selectedYearId = null,
                years = cached.orEmpty(),
                catalogItems = emptyList(),
                catalogLevel = CatalogLevel.YEARS,
                isCatalogLoading = cached == null,
            )
        }
        repository.loadYears(volume.id).onSuccess { years ->
            yearCache[volume.id] = years
            _state.update { current ->
                if (current.selectedVolumeId == volume.id) current.copy(years = years, isCatalogLoading = false) else current
            }
        }.onFailure { failure ->
            _state.update { it.copy(isCatalogLoading = false, error = failure.message ?: "年份目录加载失败") }
        }
    }

    fun selectYear(year: ReadingYear) = viewModelScope.launch {
        val cached = itemCache[year.id]
        _state.update {
            it.copy(
                selectedYearId = year.id,
                catalogItems = cached.orEmpty(),
                catalogLevel = CatalogLevel.ITEMS,
                isCatalogLoading = cached == null,
            )
        }
        repository.loadYearItems(year.id).onSuccess { items ->
            itemCache[year.id] = items
            _state.update { current ->
                if (current.selectedYearId == year.id) current.copy(catalogItems = items, isCatalogLoading = false) else current
            }
        }.onFailure { failure ->
            _state.update { it.copy(isCatalogLoading = false, error = failure.message ?: "条目加载失败") }
        }
    }

    fun catalogBack() {
        _state.update {
            when (it.catalogLevel) {
                CatalogLevel.ITEMS -> it.copy(catalogLevel = CatalogLevel.YEARS, catalogItems = emptyList(), selectedYearId = null)
                CatalogLevel.YEARS -> it.copy(catalogLevel = CatalogLevel.VOLUMES, years = emptyList(), selectedVolumeId = null)
                CatalogLevel.VOLUMES -> it.copy(catalogLevel = CatalogLevel.SECTIONS, volumes = emptyList(), selectedSectionId = null)
                CatalogLevel.SECTIONS -> it
            }
        }
    }

    fun searchKnowledge(value: String) {
        val category = _state.value.selectedKnowledgeCategory
        _state.update { current ->
            current.copy(
                knowledgeQuery = value,
                knowledge = filterKnowledge(knowledgeCache, value, category),
                knowledgeCategories = knowledgeCache.map(KnowledgeEntry::category).distinct().sorted(),
                knowledgeCategoryCounts = knowledgeCache.groupingBy(KnowledgeEntry::category).eachCount(),
                isKnowledgeLoading = false,
            )
        }
        scheduleKnowledgeRequest(value.trim().ifBlank { null }, category)
    }

    fun selectKnowledgeCategory(category: String?) {
        val query = _state.value.knowledgeQuery
        _state.update { current ->
            current.copy(
                selectedKnowledgeCategory = category,
                knowledge = filterKnowledge(knowledgeCache, query, category),
                isKnowledgeLoading = false,
            )
        }
        scheduleKnowledgeRequest(query.trim().ifBlank { null }, category)
    }

    private fun loadKnowledge(query: String? = null, category: String? = null) = viewModelScope.launch {
        _state.update { it.copy(isKnowledgeLoading = true) }
        repository.loadKnowledge(query, category).onSuccess { entries ->
            if (query == null && category == null) knowledgeCache = entries
            _state.update {
                it.copy(
                    knowledge = entries,
                    knowledgeCategories = knowledgeCache.map(KnowledgeEntry::category).distinct().sorted(),
                    knowledgeCategoryCounts = knowledgeCache.groupingBy(KnowledgeEntry::category).eachCount(),
                    isKnowledgeLoading = false,
                )
            }
        }.onFailure { failure ->
            _state.update { it.copy(isKnowledgeLoading = false, error = failure.message ?: "百科加载失败") }
        }
    }

    private fun scheduleKnowledgeRequest(query: String?, category: String?) {
        knowledgeRequest?.cancel()
        knowledgeRequest = viewModelScope.launch {
            delay(250)
            _state.update { it.copy(isKnowledgeLoading = true) }
            repository.loadKnowledge(query, category).onSuccess { entries ->
                if (query == null && category == null) knowledgeCache = entries
                _state.update { current -> current.copy(knowledge = entries, isKnowledgeLoading = false) }
            }.onFailure { failure ->
                _state.update { it.copy(isKnowledgeLoading = false, error = failure.message ?: "百科加载失败") }
            }
        }
    }

    private fun filterKnowledge(source: List<KnowledgeEntry>, query: String, category: String?): List<KnowledgeEntry> {
        val needle = query.trim().lowercase()
        return source.filter { entry ->
            (category == null || entry.category == category) &&
                (needle.isBlank() || listOf(entry.title, entry.summary, entry.content).any { it.lowercase().contains(needle) })
        }
    }

    override fun onCleared() {
        statsStore.close()
        ttsController.stop()
        ttsController.release()
        super.onCleared()
    }
}

private fun renderConversation(turns: List<AiConversationTurn>): String = turns.joinToString("\n\n") { turn ->
    "追问：${turn.userMessage}\n角色：${turn.assistantMessage}"
}
