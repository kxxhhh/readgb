@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.dutongjian.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import android.content.ClipDescription
import android.content.ClipboardManager
import com.dutongjian.app.domain.model.HistoricalPlace
import com.dutongjian.app.domain.model.Note
import com.dutongjian.app.domain.model.ReadingYear
import com.dutongjian.app.domain.model.ReadingItem
import com.dutongjian.app.domain.model.TimelineEvent
import com.dutongjian.app.domain.tts.TtsPlaybackState
import androidx.compose.ui.text.TextStyle
import java.util.UUID

@Composable
internal fun TimelineScreen(items: List<ReadingItem>, catalogYears: List<ReadingYear>, onOpen: (ReadingItem) -> Unit) {
    var yearFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var eraFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val yearById = remember(catalogYears) { catalogYears.associateBy { it.id } }
    val events = remember(items, yearById) {
        items.map { item ->
            val year = item.yearId?.let(yearById::get)
            TimelineEvent(
                item = item,
                yearLabel = year?.title ?: item.yearId ?: "未标年",
                era = year?.era ?: item.dynasty.ifBlank { "未分纪" },
                sortKey = "${item.updatedAt}-${item.id}",
                yearInt = year?.yearInt,
            )
        }.sortedWith(compareBy<TimelineEvent> { it.yearInt ?: Int.MAX_VALUE }.thenBy(TimelineEvent::sortKey))
    }
    val yearOptions = remember(events) { events.map(TimelineEvent::yearLabel).distinct() }
    val eras = remember(events) { events.map(TimelineEvent::era).distinct() }
    val visible = events.filter { (yearFilter == null || it.yearLabel == yearFilter) && (eraFilter == null || it.era == eraFilter) }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text("历史年表", modifier = Modifier.padding(top = 16.dp), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("按本地已导入条目的纪、年和年号整理", color = MaterialTheme.colorScheme.onSurfaceVariant)
        FilterRow("年份", yearOptions, yearFilter) { yearFilter = it }
        FilterRow("纪年", eras, eraFilter) { eraFilter = it }
        Text("${visible.size} 个事件", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 8.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(visible, key = { it.item.id }) { event ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(end = 12.dp)) {
                        Box(Modifier.size(12.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)))
                        Box(Modifier.width(2.dp).height(74.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { onOpen(event.item) },
                        shape = RoundedCornerShape(12.dp),
                        tonalElevation = 2.dp,
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(event.item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("${event.era} · ${formatPublicYear(event.yearInt, event.yearLabel)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text(event.item.summary, maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("打开正文并定位事件", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }
        }
    }
}

internal fun formatPublicYear(yearInt: Int?, fallback: String): String = when {
    yearInt == null -> fallback
    yearInt < 0 -> "公元前${-yearInt}年"
    else -> "公元${yearInt}年"
}

@Composable
private fun FilterRow(label: String, values: List<String>, selected: String?, onSelected: (String?) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(end = 8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item { FilterChip(selected = selected == null, onClick = { onSelected(null) }, label = { Text("全部") }) }
            items(values, key = { it }) { value ->
                FilterChip(selected = selected == value, onClick = { onSelected(if (selected == value) null else value) }, label = { Text(value) })
            }
        }
    }
}

@Composable
internal fun PlaceText(
    text: String,
    places: List<HistoricalPlace>,
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    onPlaceClick: (HistoricalPlace) -> Unit,
) {
    val annotated = remember(text, places) {
        buildPlaceAnnotatedString(text, places)
    }
    ClickableText(
        text = annotated,
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = fontSize, lineHeight = lineHeight, color = MaterialTheme.colorScheme.onSurface),
        onClick = { offset ->
            annotated.getStringAnnotations("place", offset, offset).firstOrNull()?.let { annotation ->
                places.firstOrNull { it.ancientName == annotation.item }?.let(onPlaceClick)
            }
        },
    )
}

private fun buildPlaceAnnotatedString(text: String, places: List<HistoricalPlace>): AnnotatedString {
    val matches = places.flatMap { place ->
        Regex(Regex.escape(place.ancientName)).findAll(text).map { match -> Triple(match.range.first, match.range.last + 1, place) }.toList()
    }.sortedBy { it.first }
    return AnnotatedString.Builder().apply {
        var cursor = 0
        matches.forEach { (start, end, place) ->
            if (start < cursor) return@forEach
            append(text.substring(cursor, start))
            pushStringAnnotation("place", place.ancientName)
            pushStyle(SpanStyle(color = Color(0xFF2E6F63), textDecoration = TextDecoration.Underline))
            append(text.substring(start, end))
            pop()
            pop()
            cursor = end
        }
        append(text.substring(cursor))
    }.toAnnotatedString()
}

@Composable
internal fun PlaceBottomSheet(place: HistoricalPlace, onDismiss: () -> Unit, onShowMap: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(place.ancientName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("今名：${place.modernName}", color = MaterialTheme.colorScheme.primary)
            Text(place.description)
            Text("坐标：${"%.4f".format(place.latitude)}, ${"%.4f".format(place.longitude)}", style = MaterialTheme.typography.labelMedium)
            Button(onClick = onShowMap, modifier = Modifier.fillMaxWidth()) { Text("在地图中查看") }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
internal fun MapSheet(places: List<HistoricalPlace>, selected: HistoricalPlace?, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("历史地图", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("${selected?.ancientName ?: "已标记地点"} · ${selected?.modernName.orEmpty()}", color = MaterialTheme.colorScheme.primary)
            HistoricalMap(places, selected)
            Text("已加载 ${places.size} 个古地名 Marker；红色标记为当前选中地点。", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun HistoricalMap(places: List<HistoricalPlace>, selected: HistoricalPlace?) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Canvas(Modifier.fillMaxWidth().height(260.dp).padding(14.dp)) {
            val minLat = 20.0
            val maxLat = 48.0
            val minLon = 90.0
            val maxLon = 125.0
            fun point(place: HistoricalPlace): Offset = Offset(
                ((place.longitude - minLon) / (maxLon - minLon) * size.width).toFloat(),
                ((maxLat - place.latitude) / (maxLat - minLat) * size.height).toFloat(),
            )
            for (step in 1..5) {
                drawLine(Color.Gray.copy(alpha = .28f), Offset(size.width * step / 6f, 0f), Offset(size.width * step / 6f, size.height))
                drawLine(Color.Gray.copy(alpha = .28f), Offset(0f, size.height * step / 6f), Offset(size.width, size.height * step / 6f))
            }
            places.forEach { place ->
                val p = point(place)
                drawCircle(if (place == selected) Color(0xFFB34B32) else Color(0xFF35645B), radius = if (place == selected) 10f else 6f, center = p)
            }
        }
    }
}

@Composable
internal fun NoteEditorDialog(
    articleId: String,
    articleText: String,
    selectedText: String,
    startIndex: Int,
    initial: Note? = null,
    onDismiss: () -> Unit,
    onSave: (Note) -> Unit,
) {
    var quote by rememberSaveable(initial?.id) { mutableStateOf(initial?.selectedText ?: selectedText) }
    var memo by rememberSaveable(initial?.id) { mutableStateOf(initial?.memo.orEmpty()) }
    var color by rememberSaveable(initial?.id) { mutableStateOf(initial?.color ?: "#F4C95D") }
    val context = LocalContext.current
    val quoteStart = quote.trim().let { value -> articleText.indexOf(value).takeIf { value.isNotBlank() && it >= 0 } }
    val normalizedQuote = quote.trim()
    val saveEnabled = normalizedQuote.isNotBlank() && (quoteStart != null || initial != null)
    val colors = listOf("#F4C95D", "#A8DADC", "#F4A6A6", "#CDB4DB")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "记一条笔记" else "编辑笔记") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = quote,
                    onValueChange = { quote = it },
                    label = { Text("划线原文") },
                    placeholder = { Text("先在正文选择并复制，再粘贴到这里") },
                    minLines = 2,
                )
                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        if (clipboard?.hasPrimaryClip() == true && clipboard.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true) {
                            quote = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                        }
                    },
                ) { Text("粘贴剪贴板原文") }
                quote.takeIf { it.isNotBlank() }?.let { Text("“${it.take(120)}”", color = MaterialTheme.colorScheme.primary) }
                OutlinedTextField(value = memo, onValueChange = { memo = it }, label = { Text("笔记心得") }, minLines = 3)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(colors) { candidate ->
                        FilterChip(selected = color == candidate, onClick = { color = candidate }, label = { Text("颜色") })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val resolvedStart = quoteStart ?: initial?.startIndex ?: startIndex
                onSave(Note(initial?.id ?: UUID.randomUUID().toString(), articleId, resolvedStart, resolvedStart + normalizedQuote.length, normalizedQuote, memo.trim(), color, initial?.createdAt ?: System.currentTimeMillis()))
                onDismiss()
            }, enabled = saveEnabled) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
internal fun NotesLibrary(
    notes: List<Note>,
    items: List<ReadingItem>,
    onOpen: (ReadingItem, Note) -> Unit,
    onDelete: (Note) -> Unit,
) {
    if (notes.isEmpty()) {
        EmptyState("还没有笔记", "在正文中选择划线或记笔记")
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            items(notes, key = { it.id }) { note ->
                val item = items.firstOrNull { it.id == note.articleId }
                Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 2.dp) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Column(
                            modifier = Modifier.fillMaxWidth().clickable { item?.let { onOpen(it, note) } },
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(note.selectedText, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            if (note.memo.isNotBlank()) Text(note.memo)
                            Text(item?.title ?: "已删除条目", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { onDelete(note) }) { Text("删除") }
                    }
                }
            }
        }
    }
}

@Composable
internal fun HistoricalNotesList(notes: List<ReadableHistoricalNote>, onClick: (ReadableHistoricalNote) -> Unit) {
    if (notes.isEmpty()) {
        Text("当前条目暂无可读注释。", color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            notes.forEach { note ->
                Surface(modifier = Modifier.fillMaxWidth().clickable { onClick(note) }, shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.background) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("原文位置 ${note.position}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(note.text, fontSize = 16.sp, lineHeight = 25.sp)
                        val links = (note.people + note.places).distinct()
                        if (links.isNotEmpty()) Text("关联：${links.joinToString("、")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
internal fun HistoricalNoteText(
    text: String,
    notes: List<ReadableHistoricalNote>,
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    selectedPosition: Int?,
    onNoteClick: (ReadableHistoricalNote) -> Unit,
) {
    val annotated = remember(text, notes, selectedPosition) {
        buildHistoricalNoteAnnotatedString(text, notes, selectedPosition)
    }
    ClickableText(
        text = annotated,
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = fontSize, lineHeight = lineHeight, color = MaterialTheme.colorScheme.onSurface),
        onClick = { offset ->
            annotated.getStringAnnotations("historical-note", offset, offset).firstOrNull()?.let { annotation ->
                notes.firstOrNull { it.position.toString() == annotation.item }?.let(onNoteClick)
            }
        },
    )
}

@Composable
internal fun ReadingAnnotatedText(
    text: String,
    places: List<HistoricalPlace>,
    historicalNotes: List<ReadableHistoricalNote>,
    savedNotes: List<Note>,
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    highlightedSavedNoteId: String?,
    highlightedNotePosition: Int? = null,
    modifier: Modifier = Modifier,
    onPlaceClick: (HistoricalPlace) -> Unit,
    onHistoricalNoteClick: (ReadableHistoricalNote) -> Unit = {},
    onSavedNoteClick: (Note) -> Unit = {},
) {
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val annotated = remember(text, places, historicalNotes, savedNotes, highlightedSavedNoteId, highlightedNotePosition) {
        buildReadingAnnotatedString(text, places, historicalNotes, savedNotes, highlightedSavedNoteId, highlightedNotePosition)
    }
    val noteRanges = remember(text, historicalNotes) {
        historicalNotes.sortedBy { it.position }.mapNotNull { note ->
            if (note.position !in text.indices) return@mapNotNull null
            val end = (note.position + 2).coerceAtMost(text.length)
            note.position to end
        }.filter { (start, end) -> start < end }
    }
    ClickableText(
        text = annotated,
        modifier = modifier
            .drawBehind {
                val layout = layoutResult ?: return@drawBehind
                noteRanges.forEach { (start, end) ->
                    val firstLine = layout.getLineForOffset(start)
                    val lastLine = layout.getLineForOffset((end - 1).coerceAtLeast(start))
                    for (line in firstLine..lastLine) {
                        val left = if (line == firstLine) layout.getHorizontalPosition(start, true) else layout.getLineLeft(line)
                        val right = if (line == lastLine) layout.getHorizontalPosition(end, false) else layout.getLineRight(line)
                        val y = layout.getLineBottom(line) + 2.dp.toPx()
                        drawLine(
                            color = Color(0xFFB34B32),
                            start = Offset(left, y),
                            end = Offset(right, y),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                        )
                    }
                }
            },
        style = TextStyle(fontSize = fontSize, lineHeight = lineHeight, color = MaterialTheme.colorScheme.onSurface),
        onTextLayout = { layoutResult = it },
        onClick = { offset ->
            annotated.getStringAnnotations("place", offset, offset).firstOrNull()?.let { annotation ->
                places.firstOrNull { it.ancientName == annotation.item }?.let(onPlaceClick)
                return@ClickableText
            }
            annotated.getStringAnnotations("historical-note", offset, offset).firstOrNull()?.let { annotation ->
                historicalNotes.firstOrNull { it.position.toString() == annotation.item }?.let(onHistoricalNoteClick)
                return@ClickableText
            }
            annotated.getStringAnnotations("saved-note", offset, offset).firstOrNull()?.let { annotation ->
                savedNotes.firstOrNull { it.id == annotation.item }?.let(onSavedNoteClick)
            }
        },
    )
}

internal fun noteHighlightRanges(text: String, savedNotes: List<Note>): List<IntRange> = savedNotes
    .filter { it.selectedText.isNotBlank() }
    .flatMap { note ->
        buildList {
            var cursor = note.startIndex.coerceIn(0, text.length)
            while (cursor < text.length) {
                val found = text.indexOf(note.selectedText, cursor)
                if (found < 0) break
                add(found until (found + note.selectedText.length))
                cursor = found + note.selectedText.length
            }
        }
    }

private data class ReadingMark(val start: Int, val end: Int, val type: String, val id: String, val style: SpanStyle)

private fun buildReadingAnnotatedString(
    text: String,
    places: List<HistoricalPlace>,
    historicalNotes: List<ReadableHistoricalNote>,
    savedNotes: List<Note>,
    highlightedSavedNoteId: String?,
    highlightedNotePosition: Int? = null,
): AnnotatedString {
    val marks = mutableListOf<ReadingMark>()
    places.forEach { place ->
        Regex(Regex.escape(place.ancientName)).findAll(text).forEach { match ->
            marks += ReadingMark(match.range.first, match.range.last + 1, "place", place.ancientName, SpanStyle(color = Color(0xFF2E6F63), textDecoration = TextDecoration.Underline))
        }
    }
    historicalNotes.sortedBy { it.position }.forEachIndexed { index, note ->
        if (note.position in text.indices) {
            val end = (note.position + 2).coerceAtMost(text.length)
            marks += ReadingMark(note.position, end, "historical-note", note.position.toString(), SpanStyle(background = if (note.position == highlightedNotePosition) Color(0xFFF4C95D) else Color(0x33B34B32)))
        }
    }
    savedNotes.forEach { note ->
        if (note.selectedText.isBlank()) return@forEach
        var cursor = note.startIndex.coerceIn(0, text.length)
        while (cursor < text.length) {
            val found = text.indexOf(note.selectedText, cursor)
            if (found < 0) break
            marks += ReadingMark(found, found + note.selectedText.length, "saved-note", note.id, SpanStyle(background = if (note.id == highlightedSavedNoteId) Color(0xFFF4C95D) else Color(0x55F4C95D)))
            cursor = found + note.selectedText.length.coerceAtLeast(1)
        }
    }
    val nonOverlapping = marks.sortedWith(compareBy<ReadingMark> { it.start }.thenByDescending { it.end - it.start }).fold(mutableListOf<ReadingMark>()) { result, mark ->
        if (result.none { mark.start < it.end && it.start < mark.end }) result += mark
        result
    }
    return AnnotatedString.Builder().apply {
        var cursor = 0
        nonOverlapping.forEach { mark ->
            if (mark.start > cursor) append(text.substring(cursor, mark.start))
            pushStringAnnotation(mark.type, mark.id)
            pushStyle(mark.style)
            append(text.substring(mark.start, mark.end))
            pop()
            pop()
            cursor = mark.end
        }
        if (cursor < text.length) append(text.substring(cursor))
    }.toAnnotatedString()
}

private fun buildHistoricalNoteAnnotatedString(text: String, notes: List<ReadableHistoricalNote>, selectedPosition: Int?): AnnotatedString {
    val valid = notes.filter { it.position in text.indices }.sortedBy { it.position }
    return AnnotatedString.Builder().apply {
        var cursor = 0
        valid.forEachIndexed { index, note ->
            val start = note.position.coerceAtLeast(cursor)
            val end = (start + 2).coerceAtMost(text.length)
            if (start > cursor) append(text.substring(cursor, start))
            if (end > start) {
                pushStringAnnotation("historical-note", note.position.toString())
                pushStyle(SpanStyle(background = if (selectedPosition == note.position) Color(0xFFF4C95D) else Color(0x33B34B32)))
                append(text.substring(start, end))
                pop()
                pop()
                cursor = end
            }
        }
        if (cursor < text.length) append(text.substring(cursor))
    }.toAnnotatedString()
}

@Composable
internal fun TtsControlRow(
    isPlaying: Boolean,
    isPaused: Boolean,
    onSpeak: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = onSpeak) { Text(if (isPlaying) "重新朗读" else "朗读") }
        if (isPlaying && !isPaused) TextButton(onClick = onPause) { Text("暂停") }
        if (isPaused) TextButton(onClick = onResume) { Text("继续") }
        if (isPlaying) TextButton(onClick = onStop) { Text("停止") }
    }
}

@Composable
internal fun TtsSleepTimerRow(
    state: TtsPlaybackState,
    onStartSleepTimer: (Int) -> Unit,
    onStopAfterCurrentItem: () -> Unit,
    onCancel: () -> Unit,
) {
    val hasTimer = state.sleepRemainingSeconds > 0L || state.stopAfterCurrentItem
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("定时停止", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.sleepRemainingSeconds > 0L) {
                val minutes = state.sleepRemainingSeconds / 60
                val seconds = state.sleepRemainingSeconds % 60
                Text("${minutes}:${seconds.toString().padStart(2, '0')}", color = MaterialTheme.colorScheme.primary)
            } else if (state.stopAfterCurrentItem) {
                Text("本篇结束", color = MaterialTheme.colorScheme.primary)
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(selected = state.sleepRemainingSeconds == 15 * 60L, onClick = { onStartSleepTimer(15) }, label = { Text("15 分钟") }) }
            item { FilterChip(selected = state.sleepRemainingSeconds == 30 * 60L, onClick = { onStartSleepTimer(30) }, label = { Text("30 分钟") }) }
            item { FilterChip(selected = state.stopAfterCurrentItem, onClick = onStopAfterCurrentItem, label = { Text("本篇结束") }) }
            if (hasTimer) item { FilterChip(selected = false, onClick = onCancel, label = { Text("取消") }) }
        }
    }
}

@Composable
internal fun FloatingTtsBall(
    title: String,
    isPaused: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val ballScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (expanded) 0.94f else 1f,
        animationSpec = androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
        label = "tts-ball-scale",
    )
    Box(modifier = modifier) {
        Column(horizontalAlignment = Alignment.End) {
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + scaleIn(initialScale = 0.92f) + slideInVertically { it / 4 },
                exit = fadeOut() + scaleOut(targetScale = 0.92f) + slideOutVertically { it / 4 },
            ) {
                Surface(
                    modifier = Modifier.width(220.dp).padding(bottom = 8.dp),
                    shape = RoundedCornerShape(14.dp),
                    tonalElevation = 5.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(start = 12.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(title, modifier = Modifier.weight(1f), maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        IconButton(onClick = { if (isPaused) onResume() else onPause() }) {
                            Icon(if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, contentDescription = if (isPaused) "继续朗读" else "暂停朗读")
                        }
                        IconButton(onClick = onStop) { Icon(Icons.Default.Stop, contentDescription = "停止朗读") }
                    }
                }
            }
            Surface(
                modifier = Modifier
                    .size(58.dp)
                    .graphicsLayer {
                        scaleX = ballScale
                        scaleY = ballScale
                    }
                    .clickable { expanded = !expanded },
                shape = androidx.compose.foundation.shape.CircleShape,
                tonalElevation = 8.dp,
                color = MaterialTheme.colorScheme.primary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = if (isPaused) "展开并继续朗读" else "展开并暂停朗读",
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}
