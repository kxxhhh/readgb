package com.dutongjian.app.ui

import com.dutongjian.app.domain.model.ReadingItem

internal fun orderedReadingItemsForYear(items: List<ReadingItem>, current: ReadingItem): List<ReadingItem> {
    val yearId = current.yearId ?: return listOf(current)
    return (items.filter { it.section == current.section && it.yearId == yearId } + current)
        .distinctBy(ReadingItem::id)
        .sortedWith(
            compareBy<ReadingItem> { item -> if (item.sortOrder > 0) item.sortOrder else Int.MAX_VALUE }
                .thenBy(ReadingItem::id),
        )
}
