@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.dutongjian.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dutongjian.app.domain.model.ReadingItem

private enum class AppTab(val label: String) {
    HOME("首页"),
    LIBRARY("书架"),
}

@Composable
fun DutongjianApp(
    state: ReadingUiState,
    onRefresh: () -> Unit,
    onSearch: (String) -> Unit,
    onCategorySelected: (String?) -> Unit,
    onLibraryTabSelected: (LibraryTab) -> Unit,
    onFavoriteToggle: (ReadingItem) -> Unit,
    onOpen: (ReadingItem) -> Unit,
    onDarkModeToggle: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(AppTab.HOME) }
    var selectedItem by remember { mutableStateOf<ReadingItem?>(null) }

    AnimatedContent(
        targetState = selectedItem,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "detail-transition",
    ) { detail ->
        if (detail != null) {
            DetailScreen(
                item = detail,
                onBack = { selectedItem = null },
                onFavoriteToggle = { onFavoriteToggle(detail) },
            )
        } else {
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
                            selected = tab == AppTab.LIBRARY,
                            onClick = { tab = AppTab.LIBRARY },
                            icon = { Icon(Icons.Default.Bookmark, contentDescription = null) },
                            label = { Text(AppTab.LIBRARY.label) },
                        )
                    }
                },
            ) { padding ->
                Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                    AnimatedContent(targetState = tab, label = "tab-transition") { currentTab ->
                        when (currentTab) {
                            AppTab.HOME -> HomeScreen(
                                state = state,
                                onSearch = onSearch,
                                onCategorySelected = onCategorySelected,
                                onFavoriteToggle = onFavoriteToggle,
                                onOpen = { item -> onOpen(item); selectedItem = item },
                            )
                            AppTab.LIBRARY -> LibraryScreen(
                                state = state,
                                onTabSelected = onLibraryTabSelected,
                                onFavoriteToggle = onFavoriteToggle,
                                onOpen = { item -> onOpen(item); selectedItem = item },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    state: ReadingUiState,
    onSearch: (String) -> Unit,
    onCategorySelected: (String?) -> Unit,
    onFavoriteToggle: (ReadingItem) -> Unit,
    onOpen: (ReadingItem) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf(state.query) }
    val visibleItems = state.items.filter { item ->
        state.selectedCategory == null || item.category == state.selectedCategory
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
                shape = RoundedCornerShape(16.dp),
            )
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
        if (state.isLoading && state.items.isEmpty()) {
            item { LoadingState() }
        } else if (visibleItems.isEmpty()) {
            item { EmptyState("没有匹配的条目", "换个关键词或清除分类筛选") }
        } else {
            item {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("精选条目", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text("${visibleItems.size} 篇", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
            items(visibleItems, key = { it.id }) { item ->
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
            FilterChip(selected = selected == category, onClick = { onSelected(category) }, label = { Text(category) })
        }
    }
}

@Composable
private fun LibraryScreen(
    state: ReadingUiState,
    onTabSelected: (LibraryTab) -> Unit,
    onFavoriteToggle: (ReadingItem) -> Unit,
    onOpen: (ReadingItem) -> Unit,
) {
    val items = when (state.libraryTab) {
        LibraryTab.FAVORITES -> state.items.filter { it.isFavorite }
        LibraryTab.HISTORY -> state.items.filter { it.lastOpenedAt != null }.sortedByDescending { it.lastOpenedAt }
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
        }
        if (items.isEmpty()) {
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
private fun DetailScreen(item: ReadingItem, onBack: () -> Unit, onFavoriteToggle: () -> Unit) {
    Scaffold(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        topBar = {
            TopAppBar(
                title = { Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") } },
                actions = {
                    IconButton(onClick = onFavoriteToggle) {
                        Icon(if (item.isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, contentDescription = "收藏")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Text("${item.category}  ·  ${item.dynasty}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                Text(item.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
            item {
                Text("导读", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(item.summary, style = MaterialTheme.typography.bodyLarge, lineHeight = 26.sp)
            }
            item {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), shape = RoundedCornerShape(16.dp)) {
                    Text(item.content, modifier = Modifier.padding(18.dp), style = MaterialTheme.typography.bodyLarge, lineHeight = 30.sp)
                }
            }
            item {
                Text("来源：公开站点内容索引，${item.sourceUrl}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
private fun EmptyState(title: String, subtitle: String) {
    Box(modifier = Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
