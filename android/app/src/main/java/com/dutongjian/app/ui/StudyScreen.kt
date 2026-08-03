package com.dutongjian.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dutongjian.app.domain.model.ReadingItem

private data class StudyTopic(val label: String, val keywords: List<String>)
private data class ChartBar(
    val key: String,
    val label: String,
    val value: Int,
    val characters: Int,
    val items: List<ReadingItem>,
)
private data class PersonStat(val name: String, val count: Int, val items: List<ReadingItem>)
private data class PersonRelation(val left: String, val right: String, val count: Int)
private enum class PeriodScope(val label: String) {
    YEAR("纪年"),
    DYNASTY("纪/朝代"),
}

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
    var selectedPeriodScope by rememberSaveable { mutableStateOf(PeriodScope.YEAR.label) }
    var selectedPeriodKey by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedPerson by rememberSaveable { mutableStateOf<String?>(null) }
    val topic = topics.firstOrNull { it.label == selectedTopic }
    val topicItems = remember(state.items, topic) {
        state.items.filter { item ->
            topic == null || topic.keywords.any { keyword ->
                item.title.contains(keyword) || item.content.contains(keyword) || item.tags.any { it.contains(keyword) }
            }
        }
    }
    val periodScope = PeriodScope.entries.firstOrNull { it.label == selectedPeriodScope } ?: PeriodScope.YEAR
    val yearTitles = remember(state.timelineYears) { state.timelineYears.associate { it.id to it.title } }
    val bars = remember(state.items, state.timelineYears, periodScope) {
        state.items.groupBy { item ->
            if (periodScope == PeriodScope.YEAR) {
                val id = item.yearId?.takeIf(String::isNotBlank) ?: item.dynasty.ifBlank { "unknown" }
                id to (yearTitles[id] ?: item.dynasty.ifBlank { "未分期" })
            } else {
                val label = item.dynasty.ifBlank { "未分纪" }
                "dynasty:$label" to label
            }
        }.map { (keyAndLabel, items) ->
            ChartBar(
                key = keyAndLabel.first,
                label = keyAndLabel.second,
                value = items.size,
                characters = items.sumOf { it.content.length },
                items = items,
            )
        }.sortedByDescending { it.value }
    }
    val maxBar = bars.maxOfOrNull { it.value }?.coerceAtLeast(1) ?: 1
    val selectedPeriod = bars.firstOrNull { it.key == selectedPeriodKey }
    val selectedPeriodItems = selectedPeriod?.items.orEmpty()
    val today = remember { java.time.LocalDate.now() }
    val weekSeconds = remember(state.stats.dailySeconds, today) {
        (0..6).sumOf { offset -> state.stats.dailySeconds[today.minusDays(offset.toLong()).toString()] ?: 0L }
    }
    val monthSeconds = remember(state.stats.dailySeconds, today) {
        (0..29).sumOf { offset -> state.stats.dailySeconds[today.minusDays(offset.toLong()).toString()] ?: 0L }
    }
    val peopleAndRelations = remember(state.items) {
        val peopleItems = linkedMapOf<String, MutableList<ReadingItem>>()
        val pairCounts = linkedMapOf<Pair<String, String>, Int>()
        state.items.forEach { item ->
            val names = parseHistoricalContext(item.notes).people
                .map(String::trim)
                .filter { it.length in 2..12 }
                .distinct()
            names.forEach { name -> peopleItems.getOrPut(name) { mutableListOf() }.add(item) }
            names.take(24).forEachIndexed { index, left ->
                names.drop(index + 1).forEach { right ->
                    val pair = if (left <= right) left to right else right to left
                    pairCounts[pair] = (pairCounts[pair] ?: 0) + 1
                }
            }
        }
        val people = peopleItems.entries
            .sortedWith(compareByDescending<Map.Entry<String, MutableList<ReadingItem>>> { it.value.size }.thenBy { it.key })
            .map { PersonStat(it.key, it.value.size, it.value.distinctBy(ReadingItem::id)) }
        val relations = pairCounts.entries
            .sortedByDescending { it.value }
            .map { PersonRelation(it.key.first, it.key.second, it.value) }
        people to relations
    }
    val people = peopleAndRelations.first
    val relations = peopleAndRelations.second
    val activePerson = selectedPerson?.takeIf { name -> people.any { it.name == name } } ?: people.firstOrNull()?.name
    val activeRelations = relations
        .filter { relation -> relation.left == activePerson || relation.right == activePerson }
        .take(16)

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
            StudyTrendChart(state.stats.dailySeconds, modifier = Modifier.padding(top = 10.dp))
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
            Text("纪年篇目分布", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "覆盖 ${bars.size} 个${periodScope.label}、${state.items.size} 篇；柱高代表篇目数，点选后查看正文。",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                PeriodScope.entries.forEach { scope ->
                    FilterChip(
                        selected = periodScope == scope,
                        onClick = {
                            selectedPeriodScope = scope.label
                            selectedPeriodKey = null
                        },
                        label = { Text(scope.label) },
                    )
                }
            }
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                items(bars, key = { it.key }) { bar ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(76.dp).height(174.dp)) {
                        Text("${bar.value}篇", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Box(modifier = Modifier.height(116.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                            val height = (104f * bar.value / maxBar).coerceAtLeast(12f).dp
                            Surface(
                                modifier = Modifier.width(38.dp).height(height).clickable { selectedPeriodKey = bar.key },
                                color = if (selectedPeriodKey == bar.key) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
                            ) {}
                        }
                        Text(bar.label, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        if (selectedPeriod != null) {
            item {
                Text("${selectedPeriod.label} · ${selectedPeriod.value} 篇", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("共 ${selectedPeriod.characters} 字；按篇幅展示最长的史料切片", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(selectedPeriodItems.sortedByDescending { it.content.length }.take(12), key = { it.id }) { item ->
                ElevatedCard(modifier = Modifier.fillMaxWidth().clickable { onOpen(item) }, shape = RoundedCornerShape(10.dp)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item.title, fontWeight = FontWeight.Bold)
                        Text("${item.content.length} 字 · ${item.dynasty}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(item.summary, maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            Text("人物关联图谱", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("索引覆盖 ${people.size} 位人物；先选择中心人物，再查看与其共同出现的史料。", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (people.isEmpty()) {
                Text("当前离线内容还没有结构化人物关联。", modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(people, key = { it.name }) { person ->
                        FilterChip(
                            selected = activePerson == person.name,
                            onClick = { selectedPerson = person.name },
                            label = { Text("${person.name} ${person.count}") },
                        )
                    }
                }
                RelationshipGraph(
                    people = people,
                    relations = activeRelations,
                    selectedPerson = activePerson,
                    onOpen = onOpen,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
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
private fun StudyTrendChart(dailySeconds: Map<String, Long>, modifier: Modifier = Modifier) {
    val today = remember { java.time.LocalDate.now() }
    val values = remember(dailySeconds, today) {
        (6 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            date to (dailySeconds[date.toString()] ?: 0L)
        }
    }
    val maxValue = values.maxOfOrNull { it.second }?.coerceAtLeast(60L) ?: 60L
    Row(
        modifier = modifier.fillMaxWidth().height(94.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        values.forEach { (date, seconds) ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((54f * seconds / maxValue).coerceAtLeast(if (seconds > 0) 8f else 3f).dp),
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                ) {}
                Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RelationshipGraph(
    people: List<PersonStat>,
    relations: List<PersonRelation>,
    selectedPerson: String?,
    onOpen: (ReadingItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val centerPerson = people.firstOrNull { it.name == selectedPerson }
    val neighborNames = relations
        .flatMap { relation -> listOf(relation.left, relation.right) }
        .filter { it != selectedPerson }
        .distinct()
    val visiblePeople = buildList {
        centerPerson?.let(::add)
        neighborNames.mapNotNull { name -> people.firstOrNull { it.name == name } }
            .take(12)
            .forEach(::add)
    }
    val names = visiblePeople.map(PersonStat::name)
    val relationColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.62f)
    val columns = 3
    val rows = ((visiblePeople.size + columns - 1) / columns).coerceAtLeast(1)
    val graphHeight = (rows * 78 + 18).dp
    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(graphHeight)) {
        val columnWidth = (maxWidth - 32.dp) / columns
        val nodeWidth = (columnWidth - 8.dp).coerceAtLeast(76.dp)
        val nodeHeight = 58.dp
        val positions = visiblePeople.mapIndexed { index, _ ->
            (16.dp + columnWidth * (index % columns)) to (10.dp + 78.dp * (index / columns))
        }
        Canvas(Modifier.fillMaxSize()) {
            relations.forEach { relation ->
                val leftIndex = names.indexOf(relation.left)
                val rightIndex = names.indexOf(relation.right)
                if (leftIndex >= 0 && rightIndex >= 0) {
                    val left = positions[leftIndex]
                    val right = positions[rightIndex]
                    drawLine(
                        color = relationColor,
                        start = Offset(left.first.toPx() + nodeWidth.toPx() / 2f, left.second.toPx() + nodeHeight.toPx() / 2f),
                        end = Offset(right.first.toPx() + nodeWidth.toPx() / 2f, right.second.toPx() + nodeHeight.toPx() / 2f),
                        strokeWidth = (1f + relation.count.coerceAtMost(6) / 2f).dp.toPx(),
                    )
                }
            }
        }
        visiblePeople.forEachIndexed { index, person ->
            val item = person.items.firstOrNull()
            val position = positions[index]
            Surface(
                modifier = Modifier
                    .offset(x = position.first, y = position.second)
                .width(nodeWidth)
                .height(nodeHeight)
                .clickable { item?.let(onOpen) },
                shape = RoundedCornerShape(8.dp),
                color = if (person.name == selectedPerson) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 3.dp,
            ) {
                Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(person.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    Text("${person.count} 篇", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
    centerPerson?.let { person ->
        Text(
            "中心人物：${person.name} · 出现于 ${person.count} 篇；网络显示其最强的 ${relations.size} 条共现关系。",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
    if (relations.isNotEmpty()) {
        Text("高频共现", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            relations.take(10).forEach { relation ->
                val targetName = if (relation.left == selectedPerson) relation.right else relation.left
                val target = people.firstOrNull { it.name == targetName }?.items?.firstOrNull()
                Text(
                    "$targetName · ${relation.count} 篇共现",
                    modifier = Modifier.fillMaxWidth().clickable { target?.let(onOpen) }.padding(vertical = 5.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    } else {
        Text("当前人物还没有可计算的共现关系。", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
    }
}
