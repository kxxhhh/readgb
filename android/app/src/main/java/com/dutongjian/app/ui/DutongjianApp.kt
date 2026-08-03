@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.dutongjian.app.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.togetherWith
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import android.widget.Toast
import com.dutongjian.app.domain.model.AiTask
import com.dutongjian.app.domain.model.AiResult
import com.dutongjian.app.domain.model.HistoricalPlace
import com.dutongjian.app.domain.model.KnowledgeEntry
import com.dutongjian.app.domain.model.LibrarySection
import com.dutongjian.app.domain.model.Note
import com.dutongjian.app.domain.model.ReadingItem
import com.dutongjian.app.domain.model.ReadingYear
import com.dutongjian.app.domain.model.TextScript
import com.dutongjian.app.domain.model.TtsEngineType
import com.dutongjian.app.domain.model.Volume
import com.dutongjian.app.domain.text.ClassicalScriptMapper
import com.dutongjian.app.domain.text.ClassicalGlossary
import com.dutongjian.app.domain.tts.TtsPlaybackState
import kotlinx.coroutines.delay
private enum class AppTab(val label: String) {
    HOME("首页"),
    CATALOG("目录"),
    KNOWLEDGE("百科"),
    TIMELINE("年表"),
    STUDY("研读"),
    LIBRARY("书架"),
}

private enum class ReadingMode(val label: String) {
    PARALLEL("对照"),
    ORIGINAL("原文"),
    TRANSLATION("白话"),
}

private data class SentenceSegment(
    val index: Int,
    val start: Int,
    val end: Int,
    val text: String,
)

private fun sentenceSegments(text: String): List<SentenceSegment> =
    Regex("[^。；？！]*[。；？！]|[^。；？！]+")
        .findAll(text)
        .mapIndexedNotNull { index, match ->
            val raw = match.value
            val leading = raw.indexOfFirst { !it.isWhitespace() }
            if (leading < 0) return@mapIndexedNotNull null
            val trailing = raw.indexOfLast { !it.isWhitespace() } + 1
            SentenceSegment(
                index = index,
                start = match.range.first + leading,
                end = match.range.first + trailing,
                text = raw.substring(leading, trailing),
            )
        }
        .toList()

@Composable
fun DutongjianApp(
    state: ReadingUiState,
    onRefresh: () -> Unit,
    onSearch: (String) -> Unit,
    onCategorySelected: (String?) -> Unit,
    onCycleFeatured: () -> Unit,
    onLibraryTabSelected: (LibraryTab) -> Unit,
    onFavoriteToggle: (ReadingItem) -> Unit,
    onOpen: (ReadingItem) -> Unit,
    onSectionSelected: (LibrarySection) -> Unit,
    onVolumeSelected: (Volume) -> Unit,
    onYearSelected: (ReadingYear) -> Unit,
    onCatalogBack: () -> Unit,
    onKnowledgeSearch: (String) -> Unit,
    onKnowledgeCategorySelected: (String?) -> Unit,
    aiState: AiUiState,
    onAiBaseUrlChanged: (String) -> Unit,
    onAiModelChanged: (String) -> Unit,
    onAiApiKeyChanged: (String) -> Unit,
    onAiSettingsSave: () -> Unit,
    onAiApiKeyClear: () -> Unit,
    onAiGenerate: (ReadingItem, AiTask) -> Unit,
    onAiResultClear: () -> Unit,
    onAiResultOpen: (AiResult) -> Unit,
    onAiResultDelete: (AiResult) -> Unit,
    onTtsEngineSelected: (TtsEngineType) -> Unit,
    onSpeak: (ReadingItem) -> Unit,
    onPauseTts: () -> Unit,
    onResumeTts: () -> Unit,
    onStopTts: () -> Unit,
    onStartTtsSleepTimer: (Int) -> Unit,
    onStopTtsAfterCurrentItem: () -> Unit,
    onCancelTtsSleepTimer: () -> Unit,
    onStopReadingSession: () -> Unit,
    onSaveNote: (Note) -> Unit,
    onDeleteNote: (Note) -> Unit,
    onDarkModeToggle: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(AppTab.HOME) }
    var selectedItem by remember { mutableStateOf<ReadingItem?>(null) }
    var selectedKnowledge by remember { mutableStateOf<KnowledgeEntry?>(null) }
    var selectedNoteId by remember { mutableStateOf<String?>(null) }
    var showAiSettings by rememberSaveable { mutableStateOf(false) }
    var exitArmed by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val selectedItemId = selectedItem?.id
    val selectedKnowledgeId = selectedKnowledge?.id

    LaunchedEffect(state.tts.currentItemId, state.tts.isPlaying) {
        if (state.tts.isPlaying) {
            state.tts.currentItemId?.let { currentId ->
                state.items.firstOrNull { it.id == currentId }?.let { currentItem ->
                    if (selectedItem?.id != currentItem.id) {
                        selectedItem = currentItem
                        selectedKnowledge = null
                        selectedNoteId = null
                    }
                }
            }
        }
    }
    LaunchedEffect(exitArmed) {
        if (exitArmed) {
            delay(2000)
            exitArmed = false
        }
    }
    BackHandler {
        when {
            showAiSettings -> showAiSettings = false
            selectedItem != null -> {
                onStopReadingSession()
                selectedItem = null
                selectedNoteId = null
            }
            selectedKnowledge != null -> selectedKnowledge = null
            tab != AppTab.HOME -> {
                tab = AppTab.HOME
                exitArmed = false
            }
            exitArmed -> (context as? Activity)?.finish()
            else -> {
                exitArmed = true
                Toast.makeText(context, "再按一次返回桌面", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (showAiSettings) {
            AiSettingsScreen(
                aiState = aiState,
                onBack = { showAiSettings = false },
                onBaseUrlChanged = onAiBaseUrlChanged,
                onModelChanged = onAiModelChanged,
                onApiKeyChanged = onAiApiKeyChanged,
                onSave = onAiSettingsSave,
                onClearApiKey = onAiApiKeyClear,
                ttsEngine = state.tts.engine,
                onTtsEngineSelected = onTtsEngineSelected,
            )
        } else AnimatedContent(
        targetState = selectedItemId ?: selectedKnowledgeId,
        transitionSpec = {
            if (targetState == null && initialState != null) {
                slideInHorizontally { -it / 3 } + fadeIn() togetherWith slideOutHorizontally { it / 3 } + fadeOut()
            } else {
                slideInHorizontally { it / 3 } + fadeIn() togetherWith slideOutHorizontally { -it / 3 } + fadeOut()
            }
        },
        label = "detail-transition",
    ) { targetId ->
        val targetItem = targetId?.let { id ->
            state.items.firstOrNull { it.id == id } ?: selectedItem?.takeIf { it.id == id }
        }
        val targetKnowledge = selectedKnowledge?.takeIf { it.id == targetId }
        when {
            targetItem != null -> {
                DetailScreen(
                    item = targetItem,
                    onBack = { onStopReadingSession(); selectedItem = null },
                    onFavoriteToggle = { onFavoriteToggle(targetItem) },
                    aiState = aiState,
                    onAiGenerate = onAiGenerate,
                    onAiResultClear = onAiResultClear,
                    savedAiResults = state.aiResults.filter { it.itemId == targetItem.id },
                    onAiResultOpen = onAiResultOpen,
                    onAiResultDelete = onAiResultDelete,
                    places = state.places,
                    savedNotes = state.notes.filter { it.articleId == targetItem.id },
                    highlightedSavedNoteId = selectedNoteId,
                    ttsState = state.tts,
                    onSpeak = { onSpeak(targetItem) },
                    onPauseTts = onPauseTts,
                    onResumeTts = onResumeTts,
                    onStopTts = onStopTts,
                    onStartTtsSleepTimer = onStartTtsSleepTimer,
                    onStopTtsAfterCurrentItem = onStopTtsAfterCurrentItem,
                    onCancelTtsSleepTimer = onCancelTtsSleepTimer,
                    onSaveNote = onSaveNote,
                    onDeleteNote = onDeleteNote,
                )
            }
            targetKnowledge != null -> {
                KnowledgeDetailScreen(entry = targetKnowledge, onBack = { selectedKnowledge = null })
            }
            else -> {
                Scaffold(
                modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text("读通鉴", fontWeight = FontWeight.Bold)
                                Text("从阅读到体验", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        },
                        actions = {
                            IconButton(onClick = { showAiSettings = true }) {
                                Icon(Icons.Default.Settings, contentDescription = "AI 设置")
                            }
                            IconButton(onClick = onDarkModeToggle) {
                                Icon(Icons.Default.DarkMode, contentDescription = "切换深色模式")
                            }
                            IconButton(onClick = onRefresh) {
                                Icon(Icons.Default.Refresh, contentDescription = "刷新内容")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    )
                },
                bottomBar = {
                    NavigationBar(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
                        NavigationBarItem(
                            selected = tab == AppTab.HOME,
                            onClick = { tab = AppTab.HOME },
                            icon = { Icon(Icons.Default.Home, contentDescription = null) },
                            label = { Text(AppTab.HOME.label) },
                        )
                        NavigationBarItem(
                            selected = tab == AppTab.TIMELINE,
                            onClick = { tab = AppTab.TIMELINE },
                            icon = { Icon(Icons.Default.Timeline, contentDescription = null) },
                            label = { Text(AppTab.TIMELINE.label) },
                        )
                        NavigationBarItem(
                            selected = tab == AppTab.STUDY,
                            onClick = { tab = AppTab.STUDY },
                            icon = { Icon(Icons.Default.History, contentDescription = null) },
                            label = { Text(AppTab.STUDY.label) },
                        )
                        NavigationBarItem(
                            selected = tab == AppTab.CATALOG,
                            onClick = { tab = AppTab.CATALOG },
                            icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) },
                            label = { Text(AppTab.CATALOG.label) },
                        )
                        NavigationBarItem(
                            selected = tab == AppTab.KNOWLEDGE,
                            onClick = { tab = AppTab.KNOWLEDGE },
                            icon = { Icon(Icons.Default.Search, contentDescription = null) },
                            label = { Text(AppTab.KNOWLEDGE.label) },
                        )
                        NavigationBarItem(
                            selected = tab == AppTab.LIBRARY,
                            onClick = { tab = AppTab.LIBRARY },
                            icon = { Icon(Icons.Default.Bookmark, contentDescription = null) },
                            label = { Text(AppTab.LIBRARY.label) },
                        )
                    }
                },
                ) { padding ->
                    Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                        AnimatedContent(
                            targetState = tab,
                            transitionSpec = {
                                if (targetState.ordinal >= initialState.ordinal) {
                                    (slideInHorizontally { it / 5 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 5 } + fadeOut())
                                } else {
                                    (slideInHorizontally { -it / 5 } + fadeIn()) togetherWith (slideOutHorizontally { it / 5 } + fadeOut())
                                }
                            },
                            label = "tab-transition",
                        ) { selectedTab ->
                        when (selectedTab) {
                                AppTab.HOME -> HomeScreen(
                                    state = state,
                                    onSearch = onSearch,
                                    onCategorySelected = onCategorySelected,
                                    onCycleFeatured = onCycleFeatured,
                                    onFavoriteToggle = onFavoriteToggle,
                                    onOpen = { item -> onOpen(item); selectedItem = item; selectedNoteId = null },
                                )
                                AppTab.CATALOG -> CatalogScreen(
                                    state = state,
                                    onSectionSelected = onSectionSelected,
                                    onVolumeSelected = onVolumeSelected,
                                    onYearSelected = onYearSelected,
                                    onBack = onCatalogBack,
                                    onFavoriteToggle = onFavoriteToggle,
                                    onOpen = { item -> onOpen(item); selectedItem = item; selectedNoteId = null },
                                )
                                AppTab.KNOWLEDGE -> KnowledgeScreen(
                                    state = state,
                                    onSearch = onKnowledgeSearch,
                                    onCategorySelected = onKnowledgeCategorySelected,
                                    onOpen = { selectedKnowledge = it },
                                )
                                AppTab.TIMELINE -> TimelineScreen(
                                    items = state.items,
                                    catalogYears = state.timelineYears,
                                    onOpen = { item -> onOpen(item); selectedItem = item; selectedNoteId = null },
                                )
                                AppTab.STUDY -> StudyScreen(
                                    state = state,
                                    onOpen = { item -> onOpen(item); selectedItem = item; selectedNoteId = null },
                                )
                                AppTab.LIBRARY -> LibraryScreen(
                                    state = state,
                                    onTabSelected = onLibraryTabSelected,
                                    onFavoriteToggle = onFavoriteToggle,
                                    onOpen = { item -> onOpen(item); selectedItem = item; selectedNoteId = null },
                                    onOpenNote = { item, note -> onOpen(item); selectedItem = item; selectedNoteId = note.id },
                                    onDeleteNote = onDeleteNote,
                                )
                        }
                        }
                    }
                }
            }
        }
        }
        if (state.tts.isPlaying || state.tts.isPaused) {
            FloatingTtsBall(
                title = state.tts.currentItemId?.let { id -> state.items.firstOrNull { it.id == id }?.title } ?: "正在朗读",
                isPaused = state.tts.isPaused,
                onPause = onPauseTts,
                onResume = onResumeTts,
                onStop = onStopTts,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 88.dp),
            )
        }
    }
}

@Composable
private fun HomeScreen(
    state: ReadingUiState,
    onSearch: (String) -> Unit,
    onCategorySelected: (String?) -> Unit,
    onCycleFeatured: () -> Unit,
    onFavoriteToggle: (ReadingItem) -> Unit,
    onOpen: (ReadingItem) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf(state.query) }
    val visibleItems = state.items.filter { item ->
        (state.selectedCategory == null || item.category == state.selectedCategory) &&
            (state.searchResultIds == null || item.id in state.searchResultIds)
    }
    val resumeItem = state.items
        .filter { it.lastOpenedAt != null }
        .maxByOrNull { it.lastOpenedAt ?: 0L }
    val featuredItems = if (visibleItems.isEmpty()) {
        emptyList()
    } else {
        visibleItems.take(minOf(5, visibleItems.size)).mapIndexed { index, _ ->
            visibleItems[(state.featuredOffset + index) % visibleItems.size]
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("今日读史", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("在原文与白话之间，重新进入历史现场。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; onSearch(it) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text("搜索人物、事件或卷名") },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { query = ""; onSearch("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "清除搜索")
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
            )
        }
        resumeItem?.let { resumeEntry ->
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(resumeEntry) },
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("继续阅读", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(resumeEntry.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("从最近读过的条目重新进入", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            CategoryRow(
                categories = state.categories,
                selected = state.selectedCategory,
                onSelected = onCategorySelected,
            )
        }
        item {
            state.error?.let { message ->
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(message, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                        TextButton(onClick = { }) { Text("已缓存") }
                    }
                }
            }
        }
        if (state.isLoading) {
            item { LoadingState() }
        } else if (visibleItems.isEmpty()) {
            item { EmptyState("没有匹配的条目", "换个关键词或清除分类筛选") }
        } else {
            item {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("精选条目", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text("${visibleItems.size} 篇", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    TextButton(onClick = onCycleFeatured, enabled = visibleItems.size > 1) { Text("换一批") }
                }
            }
            items(featuredItems, key = { it.id }) { item ->
                ReadingCard(item, onFavoriteToggle, onOpen)
            }
        }
    }
}

@Composable
private fun CategoryRow(categories: List<String>, selected: String?, onSelected: (String?) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(selected = selected == null, onClick = { onSelected(null) }, label = { Text("全部") })
        }
        items(categories, key = { it }) { category ->
            FilterChip(selected = selected == category, onClick = { onSelected(if (selected == category) null else category) }, label = { Text(category) })
        }
    }
}

@Composable
private fun LibraryScreen(
    state: ReadingUiState,
    onTabSelected: (LibraryTab) -> Unit,
    onFavoriteToggle: (ReadingItem) -> Unit,
    onOpen: (ReadingItem) -> Unit,
    onOpenNote: (ReadingItem, Note) -> Unit,
    onDeleteNote: (Note) -> Unit,
) {
    val items = when (state.libraryTab) {
        LibraryTab.FAVORITES -> state.items.filter { it.isFavorite }
        LibraryTab.HISTORY -> state.items.filter { it.lastOpenedAt != null }.sortedByDescending { it.lastOpenedAt }
        LibraryTab.NOTES -> emptyList()
    }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text("我的书架", modifier = Modifier.padding(top = 16.dp), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Row(modifier = Modifier.padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.libraryTab == LibraryTab.FAVORITES,
                onClick = { onTabSelected(LibraryTab.FAVORITES) },
                label = { Text("收藏") },
                leadingIcon = { Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
            FilterChip(
                selected = state.libraryTab == LibraryTab.HISTORY,
                onClick = { onTabSelected(LibraryTab.HISTORY) },
                label = { Text("最近阅读") },
                leadingIcon = { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
            FilterChip(
                selected = state.libraryTab == LibraryTab.NOTES,
                onClick = { onTabSelected(LibraryTab.NOTES) },
                label = { Text("笔记") },
            )
        }
        if (state.libraryTab == LibraryTab.NOTES) {
            NotesLibrary(state.notes, state.items, onOpenNote, onDeleteNote)
        } else if (items.isEmpty()) {
            EmptyState(
                title = if (state.libraryTab == LibraryTab.FAVORITES) "还没有收藏" else "还没有阅读记录",
                subtitle = "在条目右上角保存你的阅读线索",
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(items, key = { it.id }) { item -> ReadingCard(item, onFavoriteToggle, onOpen) }
            }
        }
    }
}

@Composable
private fun CatalogScreen(
    state: ReadingUiState,
    onSectionSelected: (LibrarySection) -> Unit,
    onVolumeSelected: (Volume) -> Unit,
    onYearSelected: (ReadingYear) -> Unit,
    onBack: () -> Unit,
    onFavoriteToggle: (ReadingItem) -> Unit,
    onOpen: (ReadingItem) -> Unit,
) {
    var catalogQuery by rememberSaveable { mutableStateOf("") }
    val title = when (state.catalogLevel) {
        CatalogLevel.SECTIONS -> "阅读目录"
        CatalogLevel.VOLUMES -> "选择卷册"
        CatalogLevel.YEARS -> "选择年代"
        CatalogLevel.ITEMS -> "年代条目"
    }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            if (state.catalogLevel != CatalogLevel.SECTIONS) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回上一级目录")
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("主站的卷、纪、年阅读结构", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (state.catalogLevel != CatalogLevel.SECTIONS) {
            OutlinedTextField(
                value = catalogQuery,
                onValueChange = { catalogQuery = it },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (catalogQuery.isNotBlank()) {
                        IconButton(onClick = { catalogQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "清除目录搜索")
                        }
                    }
                },
                placeholder = { Text("筛选当前卷册、年代或条目") },
                shape = RoundedCornerShape(16.dp),
            )
        }
        val hasCatalogContent = when (state.catalogLevel) {
            CatalogLevel.SECTIONS -> state.sections.isNotEmpty()
            CatalogLevel.VOLUMES -> state.volumes.isNotEmpty()
            CatalogLevel.YEARS -> state.years.isNotEmpty()
            CatalogLevel.ITEMS -> state.catalogItems.isNotEmpty()
        }
        if (state.isCatalogLoading && !hasCatalogContent) {
            LoadingState()
        } else {
            when (state.catalogLevel) {
                CatalogLevel.SECTIONS -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
                        items(state.sections, key = { it.id }) { section ->
                            ElevatedCard(
                                modifier = Modifier.fillMaxWidth().clickable { onSectionSelected(section) },
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(section.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    Text(section.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("进入目录", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
                CatalogLevel.VOLUMES -> {
                    CatalogList(
                        values = state.volumes,
                        title = { it.title },
                        subtitle = { it.dynasty },
                        query = catalogQuery,
                        onClick = onVolumeSelected,
                        key = { it.id },
                    )
                }
                CatalogLevel.YEARS -> {
                    CatalogList(
                        values = state.years,
                        title = { it.title },
                        subtitle = { formatPublicYear(it.yearInt, it.era) },
                        query = catalogQuery,
                        onClick = onYearSelected,
                        key = { it.id },
                    )
                }
                CatalogLevel.ITEMS -> {
                    if (state.catalogItems.isEmpty()) {
                        EmptyState("这一年还没有条目", "同步服务接入真实公开内容后会显示在这里")
                    } else {
                        val filteredItems = remember(state.catalogItems, catalogQuery) {
                            state.catalogItems.filter { item ->
                                catalogQuery.isBlank() || item.title.contains(catalogQuery, ignoreCase = true) || item.summary.contains(catalogQuery, ignoreCase = true)
                            }
                        }
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
                            items(filteredItems, key = { it.id }) { item ->
                                ReadingCard(item, onFavoriteToggle, onOpen)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> CatalogList(
    values: List<T>,
    title: (T) -> String,
    subtitle: (T) -> String,
    query: String,
    onClick: (T) -> Unit,
    key: (T) -> Any,
) {
    val filteredValues = remember(values, query) {
        values.filter { value ->
            query.isBlank() || title(value).contains(query, ignoreCase = true) || subtitle(value).contains(query, ignoreCase = true)
        }
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
        items(filteredValues, key = { value -> key(value) }) { value ->
            ElevatedCard(
                modifier = Modifier.fillMaxWidth().clickable { onClick(value) },
                shape = RoundedCornerShape(14.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(title(value), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(subtitle(value), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("进入", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun KnowledgeScreen(
    state: ReadingUiState,
    onSearch: (String) -> Unit,
    onCategorySelected: (String?) -> Unit,
    onOpen: (KnowledgeEntry) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf(state.knowledgeQuery) }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text("通鉴百科", modifier = Modifier.padding(top = 16.dp), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("人物、战争、地点、政权与典故", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; onSearch(it) },
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text("搜索百科条目") },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = { query = ""; onSearch("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "清除百科搜索")
                    }
                }
            },
            shape = RoundedCornerShape(16.dp),
        )
        Text("${state.knowledge.size} 条结果", modifier = Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 12.dp)) {
            item {
                FilterChip(selected = state.selectedKnowledgeCategory == null, onClick = { onCategorySelected(null) }, label = { Text("全部") })
            }
            items(state.knowledgeCategories, key = { it }) { category ->
                FilterChip(
                    selected = state.selectedKnowledgeCategory == category,
                    onClick = { onCategorySelected(category) },
                    label = { Text("$category ${state.knowledgeCategoryCounts[category] ?: 0}") },
                )
            }
        }
        if (state.isKnowledgeLoading && state.knowledge.isEmpty()) {
            LoadingState()
        } else if (state.knowledge.isEmpty()) {
            EmptyState("没有找到百科条目", "换一个人物、事件或地点关键词")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                items(state.knowledge, key = { it.id }) { entry ->
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth().clickable { onOpen(entry) },
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text(entry.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(entry.category, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text(entry.summary, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadingCard(item: ReadingItem, onFavoriteToggle: (ReadingItem) -> Unit, onOpen: (ReadingItem) -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = { onOpen(item) }),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(3.dp))
                    Text("${item.category} · ${item.dynasty}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { onFavoriteToggle(item) }) {
                    Icon(
                        imageVector = if (item.isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = if (item.isFavorite) "取消收藏" else "收藏",
                        tint = if (item.isFavorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(item.summary, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 22.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { onOpen(item) }, label = { Text("阅读") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp)) })
            }
        }
    }
}

@Composable
private fun DetailScreen(
    item: ReadingItem,
    onBack: () -> Unit,
    onFavoriteToggle: () -> Unit,
    aiState: AiUiState,
    onAiGenerate: (ReadingItem, AiTask) -> Unit,
    onAiResultClear: () -> Unit,
    savedAiResults: List<AiResult>,
    onAiResultOpen: (AiResult) -> Unit,
    onAiResultDelete: (AiResult) -> Unit,
    places: List<HistoricalPlace>,
    savedNotes: List<Note>,
    highlightedSavedNoteId: String?,
    ttsState: TtsPlaybackState,
    onSpeak: () -> Unit,
    onPauseTts: () -> Unit,
    onResumeTts: () -> Unit,
    onStopTts: () -> Unit,
    onStartTtsSleepTimer: (Int) -> Unit,
    onStopTtsAfterCurrentItem: () -> Unit,
    onCancelTtsSleepTimer: () -> Unit,
    onSaveNote: (Note) -> Unit,
    onDeleteNote: (Note) -> Unit,
) {
    var mode by rememberSaveable(item.id) { mutableStateOf(ReadingMode.PARALLEL) }
    var script by rememberSaveable(item.id) { mutableStateOf(TextScript.SIMPLIFIED) }
    var fontPercent by rememberSaveable(item.id) { androidx.compose.runtime.mutableIntStateOf(100) }
    var showTools by rememberSaveable(item.id) { mutableStateOf(false) }
    var showHistoricalNotes by rememberSaveable(item.id) { mutableStateOf(false) }
    var showGlossary by rememberSaveable(item.id) { mutableStateOf(false) }
    var showSandbox by rememberSaveable { mutableStateOf(false) }
    var showDecisionCard by rememberSaveable { mutableStateOf(false) }
    var showOriginalEdition by rememberSaveable { mutableStateOf(false) }
    var selectedDecision by rememberSaveable(item.id) { androidx.compose.runtime.mutableIntStateOf(0) }
    var selectedPlace by remember { mutableStateOf<HistoricalPlace?>(null) }
    var showMap by rememberSaveable(item.id) { mutableStateOf(false) }
    var showNoteEditor by rememberSaveable(item.id) { mutableStateOf(false) }
    var notePendingDeletion by remember { mutableStateOf<Note?>(null) }
    var selectedHistoricalNote by remember { mutableStateOf<ReadableHistoricalNote?>(null) }
    var swipeOffset by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var swipeBack by rememberSaveable(item.id) { mutableStateOf(false) }
    val animatedSwipeOffset by animateFloatAsState(
        targetValue = if (swipeBack) 360f else swipeOffset,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "detail-swipe",
    )
    LaunchedEffect(swipeBack) {
        if (swipeBack) {
            delay(180)
            onBack()
        }
    }
    val context = LocalContext.current
    val fontScale = fontPercent / 100f
    val original = item.original.ifBlank { item.content }
    val translation = item.translation.ifBlank { item.content }
    val displayOriginal = remember(original, script) { ClassicalScriptMapper.transform(original, script) }
    val displayTranslation = remember(translation, script) { ClassicalScriptMapper.transform(translation, script) }
    val originalSegments = remember(displayOriginal) {
        sentenceSegments(displayOriginal).ifEmpty {
            listOf(SentenceSegment(0, 0, displayOriginal.length, displayOriginal))
        }
    }
    val translationSegments = remember(displayTranslation) {
        sentenceSegments(displayTranslation).ifEmpty {
            listOf(SentenceSegment(0, 0, displayTranslation.length, displayTranslation))
        }
    }
    val visibleSegments = remember(mode, originalSegments, translationSegments) {
        if (mode == ReadingMode.TRANSLATION) translationSegments else originalSegments
    }
    val historicalNotes = remember(item.notes, displayOriginal) { formatHistoricalNotes(item.notes) }
    val glossaryEntries = remember(displayOriginal) { ClassicalGlossary.find(displayOriginal).take(8) }
    val detailListState = rememberLazyListState()
    LaunchedEffect(item.id, ttsState.currentItemId, ttsState.currentSentence, originalSegments.size) {
        if (ttsState.currentItemId == item.id && ttsState.currentSentence in originalSegments.indices) {
            detailListState.animateScrollToItem(3 + ttsState.currentSentence)
        }
    }
    val localContext = parseHistoricalContext(item.notes)
    val decisionOptions = localDecisionOptions(item, localContext)
    val currentText = when (mode) {
        ReadingMode.PARALLEL -> "原文\n$displayOriginal\n\n白话\n$displayTranslation"
        ReadingMode.ORIGINAL -> displayOriginal
        ReadingMode.TRANSLATION -> displayTranslation
    }
    val clipboardSelection = remember(showNoteEditor, original) {
        if (!showNoteEditor) {
            ""
        } else {
            val clipboardText = context.getSystemService(ClipboardManager::class.java)
                ?.primaryClip
                ?.getItemAt(0)
                ?.coerceToText(context)
                ?.toString()
                .orEmpty()
            clipboardText.takeIf { it.isNotBlank() && it != original && original.contains(it) }.orEmpty()
        }
    }
    val clipboardSelectionStart = remember(original, clipboardSelection) {
        original.indexOf(clipboardSelection).coerceAtLeast(0)
    }
    val bodyFontSize = (18f * fontScale).sp
    val bodyLineHeight = (30f * fontScale).sp
    Scaffold(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        topBar = {
            TopAppBar(
                title = { Text("正文", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") } },
                actions = {
                    IconButton(onClick = onFavoriteToggle) {
                        Icon(
                            if (item.isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = if (item.isFavorite) "取消收藏" else "收藏",
                        )
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 4.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = {
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        clipboard?.setPrimaryClip(ClipData.newPlainText(item.title, currentText))
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "复制正文")
                    }
                    IconButton(onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, item.title)
                            putExtra(Intent.EXTRA_TEXT, currentText)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "分享史料"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "分享正文")
                    }
                    val currentTts = ttsState.currentItemId == item.id
                    Button(
                        modifier = Modifier.weight(1f).height(44.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        onClick = {
                            when {
                                currentTts && ttsState.isPaused -> onResumeTts()
                                currentTts && ttsState.isPlaying -> onPauseTts()
                                else -> onSpeak()
                            }
                        },
                    ) {
                        Icon(
                            imageVector = when {
                                currentTts && ttsState.isPlaying -> Icons.Default.Pause
                                else -> Icons.Default.PlayArrow
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            when {
                                currentTts && ttsState.isPaused -> "继续"
                                currentTts && ttsState.isPlaying -> "暂停"
                                else -> "朗读"
                            },
                            maxLines = 1,
                        )
                    }
                    IconButton(onClick = { showNoteEditor = true }) {
                        Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = "记笔记")
                    }
                    IconButton(onClick = { showTools = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多阅读工具")
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            state = detailListState,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .graphicsLayer {
                    translationX = animatedSwipeOffset
                    alpha = (1f - (animatedSwipeOffset / 900f)).coerceIn(.72f, 1f)
                }
                .pointerInput(item.id) {
                    var startedAtEdge = false
                    detectHorizontalDragGestures(
                        onDragStart = { startPoint ->
                            startedAtEdge = startPoint.x <= 72.dp.toPx()
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            if (startedAtEdge && !swipeBack && dragAmount > 0f) {
                                swipeOffset = (swipeOffset + dragAmount).coerceAtMost(360f)
                            }
                        },
                        onDragEnd = {
                            if (startedAtEdge && swipeOffset > 120f) swipeBack = true else swipeOffset = 0f
                            startedAtEdge = false
                        },
                        onDragCancel = {
                            startedAtEdge = false
                            swipeOffset = 0f
                        },
                    )
                },
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${item.category}  ·  ${item.dynasty}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    Text(item.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(item.summary, style = MaterialTheme.typography.bodyLarge, lineHeight = 25.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (item.tags.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(item.tags.take(6), key = { it }) { tag ->
                                Text("#$tag", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("阅读方式", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(ReadingMode.entries.toList(), key = { it.name }) { candidate ->
                                FilterChip(selected = mode == candidate, onClick = { mode = candidate }, label = { Text(candidate.label) })
                            }
                        }
                        HorizontalDivider()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("字形", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(12.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(TextScript.entries.toList(), key = { it.name }) { candidate ->
                                    FilterChip(selected = script == candidate, onClick = { script = candidate }, label = { Text(candidate.label) })
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("字号", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { fontPercent = (fontPercent - 10).coerceAtLeast(80) }, enabled = fontPercent > 80) {
                                Icon(Icons.Default.Remove, contentDescription = "减小字号")
                            }
                            Text("${fontPercent}%", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(48.dp))
                            IconButton(onClick = { fontPercent = (fontPercent + 10).coerceAtMost(160) }, enabled = fontPercent < 160) {
                                Icon(Icons.Default.Add, contentDescription = "增大字号")
                            }
                        }
                        Slider(
                            value = fontPercent.toFloat(),
                            onValueChange = { fontPercent = it.toInt() },
                            valueRange = 80f..160f,
                            steps = 7,
                        )
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            when (mode) {
                                ReadingMode.PARALLEL -> "原文与白话"
                                ReadingMode.ORIGINAL -> "原文"
                                ReadingMode.TRANSLATION -> "白话"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text("${visibleSegments.size} 段", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(visibleSegments, key = { segment -> "${item.id}-sentence-${segment.index}" }) { segment ->
                val originalSegment = originalSegments.getOrNull(segment.index)
                val segmentNotes = if (mode == ReadingMode.TRANSLATION || originalSegment == null) {
                    emptyList()
                } else {
                    historicalNotes
                        .filter { it.position in originalSegment.start until originalSegment.end }
                        .map { it.copy(position = it.position - originalSegment.start) }
                }
                val segmentSavedNotes = if (mode == ReadingMode.TRANSLATION || originalSegment == null) {
                    emptyList()
                } else {
                    savedNotes
                        .filter { it.startIndex < originalSegment.end && it.endIndex > originalSegment.start }
                        .map { note ->
                            note.copy(
                                startIndex = (note.startIndex - originalSegment.start).coerceAtLeast(0),
                                endIndex = (note.endIndex - originalSegment.start).coerceAtMost(originalSegment.text.length),
                            )
                        }
                }
                val active = ttsState.currentItemId == item.id && ttsState.currentSentence == segment.index && ttsState.isPlaying
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (active) {
                                Modifier
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f), RoundedCornerShape(12.dp))
                                    .padding(10.dp)
                            } else {
                                Modifier.padding(vertical = 6.dp)
                            },
                        ),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = (segment.index + 1).toString().padStart(2, '0'),
                        modifier = Modifier.width(24.dp).padding(top = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        if (mode == ReadingMode.TRANSLATION) {
                            SelectionContainer {
                                Text(segment.text, fontSize = bodyFontSize, lineHeight = bodyLineHeight)
                            }
                        } else {
                            SelectionContainer {
                                ReadingAnnotatedText(
                                    text = segment.text,
                                    places = places,
                                    historicalNotes = segmentNotes,
                                    savedNotes = segmentSavedNotes,
                                    fontSize = bodyFontSize,
                                    lineHeight = bodyLineHeight,
                                    highlightedSavedNoteId = highlightedSavedNoteId,
                                    onPlaceClick = { place -> selectedPlace = place },
                                    onSavedNoteClick = { note -> notePendingDeletion = note },
                                    onHistoricalNoteClick = { note -> selectedHistoricalNote = note },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            if (mode != ReadingMode.ORIGINAL && displayTranslation.isNotBlank()) {
                                if (segment.index == 0) {
                                    Text("白话", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                                Text(
                                    translationSegments.getOrNull(segment.index)?.text.orEmpty(),
                                    fontSize = (bodyFontSize.value - 1f).coerceAtLeast(14f).sp,
                                    lineHeight = bodyLineHeight,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            if (historicalNotes.isNotEmpty()) {
                item {
                    HorizontalDivider()
                    DetailSectionHeader(
                        title = "注释",
                        count = historicalNotes.size,
                        expanded = showHistoricalNotes,
                        onToggle = { showHistoricalNotes = !showHistoricalNotes },
                    )
                    if (showHistoricalNotes) {
                        HistoricalNotesList(historicalNotes) { selectedHistoricalNote = it }
                    }
                }
            }
            if (glossaryEntries.isNotEmpty()) {
                item {
                    HorizontalDivider()
                    DetailSectionHeader(
                        title = "字词提示",
                        count = glossaryEntries.size,
                        expanded = showGlossary,
                        onToggle = { showGlossary = !showGlossary },
                    )
                    if (showGlossary) {
                        glossaryEntries.forEach { entry ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                                Text(entry.term, modifier = Modifier.width(56.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(entry.explanation)
                                    Text(entry.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            if (showOriginalEdition) {
                item {
                    HorizontalDivider()
                    Text("原始底本", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("繁体原文保留自公开抓取字段，视图转换不会改写它。", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SelectionContainer { Text(original, fontSize = bodyFontSize, lineHeight = bodyLineHeight) }
                }
            }
            if (showSandbox) {
                item {
                    Text("沙盘态势", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("事件主线", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(item.summary, fontSize = (16f * fontScale).sp, lineHeight = (25f * fontScale).sp)
                    ContextSection("关键人物", localContext.people)
                    ContextSection("相关地点", localContext.places)
                    ContextSection("官职与军政", localContext.officials)
                }
            }
            if (showDecisionCard) {
                item {
                    Text("决策卡", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("选择一个策略，查看它对应的史料阅读角度。", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(decisionOptions.indices.toList()) { index ->
                            FilterChip(selected = selectedDecision == index, onClick = { selectedDecision = index }, label = { Text(decisionOptions[index].title) })
                        }
                    }
                    Text(decisionOptions[selectedDecision.coerceIn(decisionOptions.indices)].detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (savedNotes.isNotEmpty()) {
                item {
                    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(16.dp)) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("我的划线", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            savedNotes.take(5).forEach { note ->
                                Text("“${note.selectedText}”${note.memo.takeIf(String::isNotBlank)?.let { "：$it" }.orEmpty()}", color = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                        }
                    }
                }
            }
            if (aiState.isGenerating) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("AI 正在整理这篇史料…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (aiState.resultItemId == item.id && !aiState.result.isNullOrBlank()) {
                item {
                    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(16.dp)) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(aiState.task?.label ?: "AI结果", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                Text("已保存", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                TextButton(onClick = onAiResultClear) { Text("隐藏") }
                            }
                            Text(aiState.result.orEmpty(), fontSize = bodyFontSize, lineHeight = bodyLineHeight)
                        }
                    }
                }
            }
            val archivedAiResults = savedAiResults.filter { it.id != aiState.resultId }
            if (archivedAiResults.isNotEmpty()) {
                item {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp)) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("已保存的 AI 结果", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            archivedAiResults.forEach { saved ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(saved.task.label, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            saved.result,
                                            maxLines = 4,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    TextButton(onClick = { onAiResultOpen(saved) }) { Text("重开") }
                                    IconButton(onClick = { onAiResultDelete(saved) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "删除 AI 结果")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (aiState.resultItemId == item.id && !aiState.error.isNullOrBlank()) {
                item {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(16.dp)) {
                        Text(aiState.error.orEmpty(), modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
            item {
                Text("来源标记：${item.sourceUrl}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (showTools) {
        ModalBottomSheet(onDismissRequest = { showTools = false }) {
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Text("阅读工具", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("正文保持连续，辅助功能集中在这里。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item {
                    HorizontalDivider()
                    TtsControlRow(
                        isPlaying = ttsState.currentItemId == item.id && ttsState.isPlaying,
                        isPaused = ttsState.currentItemId == item.id && ttsState.isPaused,
                        onSpeak = onSpeak,
                        onPause = onPauseTts,
                        onResume = onResumeTts,
                        onStop = onStopTts,
                    )
                    if (ttsState.currentItemId == item.id && ttsState.isPlaying) {
                        Text("第 ${ttsState.currentSentence + 1} / ${ttsState.sentenceCount} 句 · ${ttsState.progress}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TtsSleepTimerRow(ttsState, onStartTtsSleepTimer, onStopTtsAfterCurrentItem, onCancelTtsSleepTimer)
                    ttsState.error?.takeIf { ttsState.currentItemId == item.id }?.let { error -> Text(error, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
                }
                item {
                    HorizontalDivider()
                    Text("深度工具", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { AssistChip(onClick = { showSandbox = !showSandbox; showTools = false }, label = { Text(if (showSandbox) "收起沙盘" else "沙盘态势") }) }
                        item { AssistChip(onClick = { showDecisionCard = !showDecisionCard; showTools = false }, label = { Text(if (showDecisionCard) "收起决策" else "决策卡") }) }
                        item { AssistChip(onClick = { showOriginalEdition = !showOriginalEdition; showTools = false }, label = { Text(if (showOriginalEdition) "收起底本" else "原始底本") }) }
                    }
                }
                item {
                    Text("AI 辅助", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { AssistChip(onClick = { onAiGenerate(item, AiTask.SUMMARY); showTools = false }, label = { Text("AI总结") }) }
                        item { AssistChip(onClick = { onAiGenerate(item, AiTask.CLASSICAL_TRANSLATION); showTools = false }, label = { Text("逐句对照") }) }
                        item { AssistChip(onClick = { onAiGenerate(item, AiTask.WORD_GLOSSARY); showTools = false }, label = { Text("词语对照") }) }
                        item { AssistChip(onClick = { onAiGenerate(item, AiTask.GRAMMAR_ANALYSIS); showTools = false }, label = { Text("语法拆解") }) }
                        item { AssistChip(onClick = { onAiGenerate(item, AiTask.ROLE_DIALOGUE); showTools = false }, label = { Text("历史角色") }) }
                        item { AssistChip(onClick = { onAiGenerate(item, AiTask.COUNTERFACTUAL); showTools = false }, label = { Text("反事实") }) }
                    }
                }
            }
        }
    }
    selectedPlace?.let { place ->
        if (showMap) MapSheet(places, place) { showMap = false; selectedPlace = null }
        else PlaceBottomSheet(place, onDismiss = { selectedPlace = null }, onShowMap = { showMap = true })
    }
    selectedHistoricalNote?.let { note ->
        AlertDialog(
            onDismissRequest = { selectedHistoricalNote = null },
            title = { Text("原文注释") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("原文位置 ${note.position}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(note.text, style = MaterialTheme.typography.bodyLarge, lineHeight = 26.sp)
                    val links = (note.people + note.places).distinct()
                    if (links.isNotEmpty()) {
                        Text("关联：${links.joinToString("、")}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selectedHistoricalNote = null }) { Text("知道了") } },
        )
    }
    if (showNoteEditor) {
        NoteEditorDialog(
            articleId = item.id,
            articleText = original,
            selectedText = clipboardSelection,
            startIndex = clipboardSelectionStart,
            onDismiss = { showNoteEditor = false },
            onSave = onSaveNote,
        )
    }
    notePendingDeletion?.let { note ->
        AlertDialog(
            onDismissRequest = { notePendingDeletion = null },
            title = { Text("删除划线？") },
            text = { Text("将删除“${note.selectedText.take(80)}”及其笔记。") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteNote(note)
                    notePendingDeletion = null
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { notePendingDeletion = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun DetailSectionHeader(
    title: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$title · $count", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Icon(
            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (expanded) "收起$title" else "展开$title",
        )
    }
}

@Composable
private fun ContextSection(title: String, values: List<String>) {
    if (values.isNotEmpty()) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(values.take(8).joinToString("、"), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AiSettingsScreen(
    aiState: AiUiState,
    onBack: () -> Unit,
    onBaseUrlChanged: (String) -> Unit,
    onModelChanged: (String) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onSave: () -> Unit,
    onClearApiKey: () -> Unit,
    ttsEngine: TtsEngineType,
    onTtsEngineSelected: (TtsEngineType) -> Unit,
) {
    Scaffold(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        topBar = {
            TopAppBar(
                title = { Text("AI 设置") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text("AI 与朗读设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("可以填写 OpenAI、兼容网关或本机模型服务的 URL 与模型名。API Key 仅加密保存在本机。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Text("朗读引擎", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("朗读按句排队，播放完当前条目后自动进入下一条。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    TtsEngineType.entries.forEach { engine ->
                        FilterChip(
                            selected = ttsEngine == engine,
                            onClick = { onTtsEngineSelected(engine) },
                            label = { Text("${engine.label}（${engine.description}）") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Text("默认使用 Android 本地 TTS；Edge-TTS 需要网络，连接失败时请使用本地引擎。引擎切换后立即生效。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
            item {
                OutlinedTextField(
                    value = aiState.baseUrl,
                    onValueChange = onBaseUrlChanged,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("API URL") },
                    placeholder = { Text("https://api.openai.com/v1") },
                    supportingText = { Text("可填写到 /v1，也可直接填写 /chat/completions") },
                )
            }
            item {
                OutlinedTextField(
                    value = aiState.model,
                    onValueChange = onModelChanged,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("模型") },
                    placeholder = { Text("gpt-4o-mini") },
                )
            }
            item {
                OutlinedTextField(
                    value = aiState.apiKeyInput,
                    onValueChange = onApiKeyChanged,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("API Key") },
                    placeholder = { Text(if (aiState.settings.hasApiKey) "已保存，留空表示保持不变" else "本机输入，不写入源码") },
                    visualTransformation = PasswordVisualTransformation(),
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onSave, enabled = !aiState.isSaving) {
                        if (aiState.isSaving) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text("保存设置")
                    }
                    if (aiState.settings.hasApiKey) TextButton(onClick = onClearApiKey) { Text("清除 Key") }
                }
            }
            aiState.error?.takeIf(String::isNotBlank)?.let { message ->
                item {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp)) {
                        Text(message, modifier = Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }
    }
}

@Composable
private fun KnowledgeDetailScreen(entry: KnowledgeEntry, onBack: () -> Unit) {
    Scaffold(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        topBar = {
            TopAppBar(
                title = { Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(entry.category, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Text(entry.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
            item { Text(entry.summary, style = MaterialTheme.typography.titleMedium, lineHeight = 26.sp) }
            item {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), shape = RoundedCornerShape(16.dp)) {
                    Text(entry.content, modifier = Modifier.padding(18.dp), style = MaterialTheme.typography.bodyLarge, lineHeight = 30.sp)
                }
            }
            item { Text("来源标记：${entry.sourceUrl}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text("正在整理今日阅读", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun EmptyState(title: String, subtitle: String) {
    Box(modifier = Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
