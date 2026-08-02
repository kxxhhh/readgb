package com.dutongjian.app.ui

import com.dutongjian.app.domain.model.AiSettings
import com.dutongjian.app.domain.model.AiTask
import com.dutongjian.app.domain.model.HomeFeed
import com.dutongjian.app.domain.model.HistoricalPlace
import com.dutongjian.app.domain.model.KnowledgeEntry
import com.dutongjian.app.domain.model.LibrarySection
import com.dutongjian.app.domain.model.Note
import com.dutongjian.app.domain.model.ReadingItem
import com.dutongjian.app.domain.model.ReadingYear
import com.dutongjian.app.domain.model.ReadingStats
import com.dutongjian.app.domain.model.Volume
import com.dutongjian.app.data.ReadingStatsRecorder
import com.dutongjian.app.domain.repository.ReadingRepository
import com.dutongjian.app.domain.repository.AiRepository
import com.dutongjian.app.domain.tts.TtsPlaybackState
import com.dutongjian.app.domain.tts.TtsPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        val viewModel = ReadingViewModel(FakeRepository(listOf(match, other), listOf(match)), FakeAiRepository(), FakeTtsPlayer(), FakeReadingStatsRecorder())

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

    @Test
    fun catalogDoesNotExposeSeedVolumesWhileRealCatalogIsLoading() = runTest {
        val gate = CompletableDeferred<List<Volume>>()
        val realVolume = Volume("real-volume", "zizhi", "卷第一", "周纪", 1)
        val viewModel = ReadingViewModel(FakeRepository(listOf(item("item", "正文")), emptyList(), gate), FakeAiRepository(), FakeTtsPlayer(), FakeReadingStatsRecorder())

        viewModel.selectSection(com.dutongjian.app.domain.model.OfflineSeed.sections.first())

        assertTrue(viewModel.state.value.isCatalogLoading)
        assertTrue(viewModel.state.value.volumes.isEmpty())

        gate.complete(listOf(realVolume))
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isCatalogLoading)
        assertEquals(listOf(realVolume), viewModel.state.value.volumes)
    }

    @Test
    fun cycleFeaturedMovesTheHomeWindowWithoutChangingTheItemFeed() = runTest {
        val items = (0 until 6).map { index -> item("item-$index", "条目$index") }
        val viewModel = ReadingViewModel(FakeRepository(items, emptyList()), FakeAiRepository(), FakeTtsPlayer(), FakeReadingStatsRecorder())

        viewModel.cycleFeatured()

        assertEquals(5, viewModel.state.value.featuredOffset)
        assertEquals(items, viewModel.state.value.items)
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
    private val volumesGate: CompletableDeferred<List<Volume>>? = null,
) : ReadingRepository {
    private val items = MutableStateFlow(initialItems)

    override fun observeItems(): Flow<List<ReadingItem>> = items

    override suspend fun refreshHome() = Result.success(HomeFeed(items.value, listOf("资治通鉴")))

    override suspend fun search(query: String) = Result.success(searchResults)

    override suspend fun setFavorite(itemId: String, favorite: Boolean) = Unit

    override suspend fun recordOpened(itemId: String) = Unit

    override suspend fun loadSections() = Result.success(emptyList<LibrarySection>())

    override suspend fun loadVolumes(sectionId: String) = Result.success(volumesGate?.await() ?: emptyList())

    override suspend fun loadYears(volumeId: String) = Result.success(emptyList<ReadingYear>())

    override suspend fun loadAllYears() = Result.success(emptyList<ReadingYear>())

    override suspend fun loadYearItems(yearId: String) = Result.success(emptyList<ReadingItem>())

    override suspend fun loadKnowledge(query: String?, category: String?) = Result.success(emptyList<KnowledgeEntry>())

    override fun observeNotes(): Flow<List<Note>> = MutableStateFlow(emptyList())

    override suspend fun saveNote(note: Note) = Unit

    override suspend fun deleteNote(note: Note) = Unit

    override fun observePlaces(): Flow<List<HistoricalPlace>> = MutableStateFlow(emptyList())
}

private class FakeAiRepository : AiRepository {
    override suspend fun loadSettings() = Result.success(AiSettings())

    override suspend fun saveSettings(baseUrl: String, model: String, apiKey: String?) = Result.success(AiSettings(baseUrl, model, apiKey != null))

    override suspend fun clearApiKey() = Result.success(AiSettings())

    override suspend fun generate(item: ReadingItem, task: AiTask) = Result.success("AI result")
}

private class FakeTtsPlayer : TtsPlayer {
    private val _state = MutableStateFlow(TtsPlaybackState())
    override val state: StateFlow<TtsPlaybackState> = _state
    override fun selectEngine(type: com.dutongjian.app.domain.model.TtsEngineType) = Unit
    override fun speak(items: List<ReadingItem>, item: ReadingItem) = Unit
    override fun pause() = Unit
    override fun resume() = Unit
    override fun stop() = Unit
    override fun startSleepTimer(minutes: Int) = Unit
    override fun stopAfterCurrentItem() = Unit
    override fun cancelSleepTimer() = Unit
    override fun release() = Unit
}

private class FakeReadingStatsRecorder : ReadingStatsRecorder {
    private var current = ReadingStats()
    override fun snapshot() = current
    override fun open(item: ReadingItem): ReadingStats {
        current = current.copy(
            openedItems = current.openedItems + 1,
            readCharacters = current.readCharacters + item.content.length,
        )
        return current
    }
    override fun close() = current
}
