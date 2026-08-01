package com.dutongjian.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dutongjian.app.domain.model.ReadingItem
import com.dutongjian.app.domain.repository.ReadingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LibraryTab { FAVORITES, HISTORY }

data class ReadingUiState(
    val items: List<ReadingItem> = emptyList(),
    val query: String = "",
    val selectedCategory: String? = null,
    val categories: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val libraryTab: LibraryTab = LibraryTab.FAVORITES,
)

private data class ReadingStateParts(
    val items: List<ReadingItem>,
    val query: String,
    val selectedCategory: String?,
    val categories: List<String>,
    val isRefreshing: Boolean,
)

@HiltViewModel
class ReadingViewModel @Inject constructor(
    private val repository: ReadingRepository,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val selectedCategory = MutableStateFlow<String?>(null)
    private val categories = MutableStateFlow<List<String>>(emptyList())
    private val isRefreshing = MutableStateFlow(false)
    private val isLoading = MutableStateFlow(true)
    private val error = MutableStateFlow<String?>(null)
    private val libraryTab = MutableStateFlow(LibraryTab.FAVORITES)

    val state: StateFlow<ReadingUiState> = combine(
        repository.observeItems(), query, selectedCategory, categories, isRefreshing,
    ) { items, queryValue, selectedCategoryValue, categoryValues, refreshing ->
        ReadingStateParts(
            items = items,
            query = queryValue,
            selectedCategory = selectedCategoryValue,
            categories = categoryValues,
            isRefreshing = refreshing,
        )
    }.combine(isLoading) { parts, loading ->
        ReadingUiState(
            items = parts.items,
            query = parts.query,
            selectedCategory = parts.selectedCategory,
            categories = parts.categories,
            isRefreshing = parts.isRefreshing,
            isLoading = loading,
        )
    }.combine(error) { current, message -> current.copy(error = message) }
        .combine(libraryTab) { current, tab -> current.copy(libraryTab = tab) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReadingUiState())

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        isRefreshing.value = true
        repository.refreshHome().onSuccess {
            categories.value = it.categories
            error.value = null
        }.onFailure { error.value = it.message ?: "同步失败，已显示本地缓存" }
        isLoading.value = false
        isRefreshing.value = false
    }

    fun search(value: String) {
        query.value = value
        if (value.trim().length < 2) return
        viewModelScope.launch {
            repository.search(value.trim()).onFailure { error.value = it.message ?: "搜索失败" }
        }
    }

    fun selectCategory(category: String?) { selectedCategory.value = category }
    fun selectLibraryTab(tab: LibraryTab) { libraryTab.value = tab }

    fun toggleFavorite(item: ReadingItem) = viewModelScope.launch {
        repository.setFavorite(item.id, !item.isFavorite)
    }

    fun open(item: ReadingItem) = viewModelScope.launch { repository.recordOpened(item.id) }
}
