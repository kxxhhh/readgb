package com.dutongjian.app.ui

import com.dutongjian.app.domain.model.HomeFeed
import com.dutongjian.app.domain.model.KnowledgeEntry
import com.dutongjian.app.domain.model.LibrarySection
import com.dutongjian.app.domain.model.ReadingItem
import com.dutongjian.app.domain.model.ReadingYear
import com.dutongjian.app.domain.model.Volume
import com.dutongjian.app.domain.repository.ReadingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReadingViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun searchTracksServerMatchesAndClearsForShortQuery() = runTest {
        val match = item("match", "三家分晋")
        val other = item("other", "赤壁之战")
        val viewModel = ReadingViewModel(FakeRepository(listOf(match, other), listOf(match)))

        viewModel.search("三家")

        assertEquals(setOf("match"), viewModel.state.value.searchResultIds)
        viewModel.search(" ")
        assertEquals(null, viewModel.state.value.searchResultIds)
    }

    @Test
    fun uiStateStartsWithOfflineReadingContent() {
        val state = ReadingUiState()

        assertEquals("三家分晋", state.items.first().title)
        assertTrue(state.sections.isNotEmpty())
        assertTrue(state.knowledge.isNotEmpty())
    }

    private fun item(id: String, title: String) = ReadingItem(
        id = id,
        title = title,
        category = "资治通鉴",
        dynasty = "周纪",
        summary = "摘要",
        content = "正文",
        sourceUrl = "https://example.com/$id",
        updatedAt = "2026-08-02",
    )
}

private class FakeRepository(
    initialItems: List<ReadingItem>,
    private val searchResults: List<ReadingItem>,
) : ReadingRepository {
    private val items = MutableStateFlow(initialItems)

    override fun observeItems(): Flow<List<ReadingItem>> = items

    override suspend fun refreshHome() = Result.success(HomeFeed(items.value, listOf("资治通鉴")))

    override suspend fun search(query: String) = Result.success(searchResults)

    override suspend fun setFavorite(itemId: String, favorite: Boolean) = Unit

    override suspend fun recordOpened(itemId: String) = Unit

    override suspend fun loadSections() = Result.success(emptyList<LibrarySection>())

    override suspend fun loadVolumes(sectionId: String) = Result.success(emptyList<Volume>())

    override suspend fun loadYears(volumeId: String) = Result.success(emptyList<ReadingYear>())

    override suspend fun loadYearItems(yearId: String) = Result.success(emptyList<ReadingItem>())

    override suspend fun loadKnowledge(query: String?, category: String?) = Result.success(emptyList<KnowledgeEntry>())
}
