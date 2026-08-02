package com.dutongjian.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dutongjian.app.domain.model.KnowledgeEntry
import com.dutongjian.app.domain.model.AiSettings
import com.dutongjian.app.domain.model.AiTask
import com.dutongjian.app.domain.model.LibrarySection
import com.dutongjian.app.domain.model.OfflineSeed
import com.dutongjian.app.domain.model.ReadingItem
import com.dutongjian.app.domain.model.ReadingYear
import com.dutongjian.app.domain.model.Volume
import com.dutongjian.app.domain.repository.ReadingRepository
import com.dutongjian.app.domain.repository.AiRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LibraryTab { FAVORITES, HISTORY }
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
    val result: String? = null,
    val error: String? = null,
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
    val isCatalogLoading: Boolean = false,
    val isKnowledgeLoading: Boolean = false,
    val ai: AiUiState = AiUiState(),
)

@HiltViewModel
class ReadingViewModel @Inject constructor(
    private val repository: ReadingRepository,
    private val aiRepository: AiRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(
        ReadingUiState(
            items = OfflineSeed.items,
            categories = OfflineSeed.categories,
            sections = OfflineSeed.sections,
            knowledge = OfflineSeed.knowledge,
            knowledgeCategories = OfflineSeed.knowledge.map(KnowledgeEntry::category).distinct().sorted(),
            knowledgeCategoryCounts = OfflineSeed.knowledge.groupingBy(KnowledgeEntry::category).eachCount(),
            isLoading = true,
        ),
    )
    val state: StateFlow<ReadingUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeItems().collectLatest { items ->
                _state.update {
                    it.copy(
                        items = items,
                        isLoading = false,
                        featuredOffset = if (items.isEmpty()) 0 else it.featuredOffset % items.size,
                    )
                }
            }
        }
        refresh()
        loadSections()
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

    fun open(item: ReadingItem) = viewModelScope.launch { repository.recordOpened(item.id) }

    fun updateAiBaseUrl(value: String) { _state.update { it.copy(ai = it.ai.copy(baseUrl = value, error = null)) } }

    fun updateAiModel(value: String) { _state.update { it.copy(ai = it.ai.copy(model = value, error = null)) } }

    fun updateAiApiKey(value: String) { _state.update { it.copy(ai = it.ai.copy(apiKeyInput = value, error = null)) } }

    fun saveAiSettings() = viewModelScope.launch {
        val ai = _state.value.ai
        _state.update { it.copy(ai = ai.copy(isSaving = true, error = null)) }
        aiRepository.saveSettings(ai.baseUrl, ai.model, ai.apiKeyInput.takeIf(String::isNotBlank))
            .onSuccess { settings ->
                _state.update { it.copy(ai = it.ai.copy(settings = settings, baseUrl = settings.baseUrl, model = settings.model, apiKeyInput = "", isSaving = false, error = null)) }
            }
            .onFailure { failure ->
                _state.update { it.copy(ai = it.ai.copy(isSaving = false, error = failure.message ?: "AI 设置保存失败")) }
            }
    }

    fun clearAiApiKey() = viewModelScope.launch {
        aiRepository.clearApiKey()
            .onSuccess { settings -> _state.update { it.copy(ai = it.ai.copy(settings = settings, apiKeyInput = "", error = null)) } }
            .onFailure { failure -> _state.update { it.copy(ai = it.ai.copy(error = failure.message ?: "API Key 清除失败")) } }
    }

    fun generateAi(item: ReadingItem, task: AiTask) = viewModelScope.launch {
        _state.update { it.copy(ai = it.ai.copy(isGenerating = true, task = task, resultItemId = item.id, result = null, error = null)) }
        aiRepository.generate(item, task)
            .onSuccess { result -> _state.update { it.copy(ai = it.ai.copy(isGenerating = false, result = result, error = null)) } }
            .onFailure { failure -> _state.update { it.copy(ai = it.ai.copy(isGenerating = false, error = failure.message ?: "AI 生成失败")) } }
    }

    fun clearAiResult() {
        _state.update { it.copy(ai = it.ai.copy(task = null, resultItemId = null, result = null, error = null)) }
    }

    private fun loadAiSettings() = viewModelScope.launch {
        aiRepository.loadSettings()
            .onSuccess { settings -> _state.update { it.copy(ai = it.ai.copy(settings = settings, baseUrl = settings.baseUrl, model = settings.model)) } }
            .onFailure { failure -> _state.update { it.copy(ai = it.ai.copy(error = failure.message ?: "AI 设置读取失败")) } }
    }

    fun loadSections() = viewModelScope.launch {
        _state.update { it.copy(isCatalogLoading = true) }
        repository.loadSections().onSuccess { sections ->
            _state.update { it.copy(sections = sections, isCatalogLoading = false, error = null) }
        }.onFailure { failure ->
            _state.update { it.copy(isCatalogLoading = false, error = failure.message ?: "目录加载失败") }
        }
    }

    fun selectSection(section: LibrarySection) = viewModelScope.launch {
        _state.update {
            it.copy(
                selectedSectionId = section.id,
                selectedVolumeId = null,
                selectedYearId = null,
                volumes = emptyList(),
                years = emptyList(),
                catalogItems = emptyList(),
                catalogLevel = CatalogLevel.VOLUMES,
                isCatalogLoading = true,
            )
        }
        repository.loadVolumes(section.id).onSuccess { volumes ->
            _state.update { it.copy(volumes = volumes, isCatalogLoading = false) }
        }.onFailure { failure ->
            _state.update { it.copy(isCatalogLoading = false, error = failure.message ?: "卷目录加载失败") }
        }
    }

    fun selectVolume(volume: Volume) = viewModelScope.launch {
        _state.update {
            it.copy(
                selectedVolumeId = volume.id,
                selectedYearId = null,
                years = emptyList(),
                catalogItems = emptyList(),
                catalogLevel = CatalogLevel.YEARS,
                isCatalogLoading = true,
            )
        }
        repository.loadYears(volume.id).onSuccess { years ->
            _state.update { it.copy(years = years, isCatalogLoading = false) }
        }.onFailure { failure ->
            _state.update { it.copy(isCatalogLoading = false, error = failure.message ?: "年份目录加载失败") }
        }
    }

    fun selectYear(year: ReadingYear) = viewModelScope.launch {
        _state.update {
            it.copy(
                selectedYearId = year.id,
                catalogItems = emptyList(),
                catalogLevel = CatalogLevel.ITEMS,
                isCatalogLoading = true,
            )
        }
        repository.loadYearItems(year.id).onSuccess { items ->
            _state.update { it.copy(catalogItems = items, isCatalogLoading = false) }
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
        _state.update { it.copy(knowledgeQuery = value) }
        loadKnowledge(value.trim().ifBlank { null }, _state.value.selectedKnowledgeCategory)
    }

    fun selectKnowledgeCategory(category: String?) {
        _state.update { it.copy(selectedKnowledgeCategory = category) }
        loadKnowledge(_state.value.knowledgeQuery.trim().ifBlank { null }, category)
    }

    private fun loadKnowledge(query: String? = null, category: String? = null) = viewModelScope.launch {
        _state.update { it.copy(isKnowledgeLoading = true) }
        repository.loadKnowledge(query, category).onSuccess { entries ->
            _state.update {
                it.copy(
                    knowledge = entries,
                    knowledgeCategories = entries.map(KnowledgeEntry::category).distinct().sorted(),
                    knowledgeCategoryCounts = entries.groupingBy(KnowledgeEntry::category).eachCount(),
                    isKnowledgeLoading = false,
                )
            }
        }.onFailure { failure ->
            _state.update { it.copy(isKnowledgeLoading = false, error = failure.message ?: "百科加载失败") }
        }
    }
}
