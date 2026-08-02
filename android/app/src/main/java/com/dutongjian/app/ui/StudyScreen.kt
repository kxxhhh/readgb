package com.dutongjian.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dutongjian.app.domain.model.ReadingItem

private data class StudyTopic(val label: String, val keywords: List<String>)
private data class ChartBar(val label: String, val value: Int, val item: ReadingItem?)
private data class GenealogyNode(val label: String, val query: String, val x: Int, val y: Int)

@Composable
internal fun StudyScreen(state: ReadingUiState, onOpen: (ReadingItem) -> Unit) {
    val topics = remember {
        listOf(
            StudyTopic("赋税", listOf("赋税", "租庸调", "两税法", "均田")),
            StudyTopic("兵制", listOf("府兵", "募兵", "军", "将")),
            StudyTopic("科举", listOf("科举", "进士", "明经", "贡举")),
            StudyTopic("货币", listOf("货币", "开元通宝", "钱", "盐铁")),
        )
    }
    var selectedTopic by rememberSaveable { mutableStateOf<String?>(null) }
    val topic = topics.firstOrNull { it.label == selectedTopic }
    val topicItems = remember(state.items, topic) {
        state.items.filter { item ->
            topic == null || topic.keywords.any { keyword ->
                item.title.contains(keyword) || item.content.contains(keyword) || item.tags.any { it.contains(keyword) }
            }
        }
    }
    val bars = remember(state.items) {
        state.items.groupBy { it.dynasty.ifBlank { "未分纪" } }
            .map { (label, items) -> ChartBar(label.take(6), items.sumOf { it.content.length }, items.firstOrNull()) }
            .sortedByDescending { it.value }
            .take(10)
    }
    val maxBar = bars.maxOfOrNull { it.value }?.coerceAtLeast(1) ?: 1
    val today = remember { java.time.LocalDate.now() }
    val weekSeconds = remember(state.stats.dailySeconds, today) {
        (0..6).sumOf { offset -> state.stats.dailySeconds[today.minusDays(offset.toLong()).toString()] ?: 0L }
    }
    val monthSeconds = remember(state.stats.dailySeconds, today) {
        (0..29).sumOf { offset -> state.stats.dailySeconds[today.minusDays(offset.toLong()).toString()] ?: 0L }
    }
    val genealogy = remember(state.items) {
        listOf(
            GenealogyNode("琅琊王氏", "王", 16, 18),
            GenealogyNode("王导", "王导", 132, 86),
            GenealogyNode("王羲之", "王羲之", 246, 18),
            GenealogyNode("弘农杨氏", "杨", 16, 154),
            GenealogyNode("杨坚", "杨坚", 132, 222),
            GenealogyNode("杨广", "杨广", 246, 154),
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("研读工作台", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("从正文、目录和本地阅读记录组织专题线索。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                StudyMetric("阅读条目", state.stats.openedItems.toString(), Modifier.weight(1f))
                StudyMetric("阅读字数", state.stats.readCharacters.coerceAtLeast(0L).toString(), Modifier.weight(1f))
                StudyMetric("覆盖卷数", "${state.stats.coveredVolumes}/${state.stats.totalVolumes}", Modifier.weight(1f))
            }
            Text("累计 ${state.stats.totalSeconds / 60} 分钟", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
        }
        item {
            Text("阅读趋势", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                StudyMetric("近 7 日", "${weekSeconds / 60} 分钟", Modifier.weight(1f))
                StudyMetric("近 30 日", "${monthSeconds / 60} 分钟", Modifier.weight(1f))
            }
            Text("统计保存在本机，不上传阅读行为。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
        item {
            Text("制度与经济史专题", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp)) {
                FilterChip(selected = selectedTopic == null, onClick = { selectedTopic = null }, label = { Text("全部") })
                topics.forEach { candidate ->
                    FilterChip(selected = selectedTopic == candidate.label, onClick = { selectedTopic = candidate.label }, label = { Text(candidate.label) })
                }
            }
        }
        items(topicItems.take(8), key = { it.id }) { item ->
            ElevatedCard(modifier = Modifier.fillMaxWidth().clickable { onOpen(item) }, shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.title, fontWeight = FontWeight.Bold)
                    Text("${item.dynasty} · ${item.content.length} 字", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(item.summary, maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Text("篇幅与事件密度", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("点击柱形进入对应时期的首条史料。", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                bars.forEach { bar ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(58.dp)) {
                        val height = (112f * bar.value / maxBar).coerceAtLeast(12f).dp
                        Surface(
                            modifier = Modifier.width(34.dp).height(height).clickable { bar.item?.let(onOpen) },
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
                        ) {}
                        Text(bar.label, maxLines = 1, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        item {
            Text("门阀与世族谱系", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("节点以当前离线史料中的人物关键词联动。", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            GenealogyGraph(nodes = genealogy, items = state.items, onOpen = onOpen, modifier = Modifier.padding(top = 8.dp))
        }
        item {
            val quote = state.items.getOrNull((java.time.LocalDate.now().dayOfYear - 1).mod(state.items.size.coerceAtLeast(1)))
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("今日金句", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(quote?.content?.take(120).orEmpty().ifBlank { "暂无离线正文" }, fontSize = 17.sp, lineHeight = 26.sp)
                    Text(quote?.title.orEmpty(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun StudyMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp), tonalElevation = 2.dp) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GenealogyGraph(
    nodes: List<GenealogyNode>,
    items: List<ReadingItem>,
    onOpen: (ReadingItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val outlineColor = MaterialTheme.colorScheme.outline
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier = modifier.fillMaxWidth().height(280.dp)) {
        Canvas(Modifier.fillMaxWidth().height(280.dp)) {
            val lines = listOf(
                Offset(62.dp.toPx(), 47.dp.toPx()) to Offset(178.dp.toPx(), 113.dp.toPx()),
                Offset(294.dp.toPx(), 47.dp.toPx()) to Offset(178.dp.toPx(), 113.dp.toPx()),
                Offset(62.dp.toPx(), 183.dp.toPx()) to Offset(178.dp.toPx(), 249.dp.toPx()),
                Offset(294.dp.toPx(), 183.dp.toPx()) to Offset(178.dp.toPx(), 249.dp.toPx()),
            )
            lines.forEach { (start, end) -> drawLine(outlineColor, start, end, strokeWidth = 2.dp.toPx()) }
            drawRoundRect(surfaceVariant, style = Stroke(width = 1.dp.toPx()))
        }
        nodes.forEach { node ->
            val item = items.firstOrNull { source ->
                source.title.contains(node.query) || source.content.contains(node.query) || source.tags.any { it.contains(node.query) }
            }
            Surface(
                modifier = Modifier.offset(node.x.dp, node.y.dp).size(width = 104.dp, height = 58.dp).clickable { item?.let(onOpen) },
                shape = RoundedCornerShape(10.dp),
                color = if (item == null) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 3.dp,
            ) {
                Box(contentAlignment = Alignment.Center) { Text(node.label, maxLines = 2, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
