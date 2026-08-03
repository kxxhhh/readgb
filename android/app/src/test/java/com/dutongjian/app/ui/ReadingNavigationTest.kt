package com.dutongjian.app.ui

import com.dutongjian.app.domain.model.ReadingItem
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingNavigationTest {
    @Test
    fun ordersEntriesBySourceParagraphAndKeepsCurrentEntry() {
        val second = item("second", 2)
        val first = item("first", 1)
        val third = item("third", 3)

        assertEquals(
            listOf("first", "second", "third"),
            orderedReadingItemsForYear(listOf(third, first), second).map { it.id },
        )
    }

    @Test
    fun doesNotMixEntriesFromAnotherYear() {
        val current = item("current", 1, "year-a")
        val other = item("other", 2, "year-b")

        assertEquals(listOf("current"), orderedReadingItemsForYear(listOf(other), current).map { it.id })
    }

    private fun item(id: String, order: Int, year: String = "year-a") = ReadingItem(
        id = id,
        title = id,
        category = "资治通鉴",
        dynasty = "周纪",
        summary = id,
        content = id,
        sourceUrl = "https://example.com/$id",
        updatedAt = "2026-08-04",
        yearId = year,
        sortOrder = order,
    )
}
