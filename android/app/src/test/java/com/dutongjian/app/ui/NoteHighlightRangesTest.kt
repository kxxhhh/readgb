package com.dutongjian.app.ui

import com.dutongjian.app.domain.model.Note
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteHighlightRangesTest {
    @Test
    fun emptySelectionDoesNotHighlightTheWholeArticle() {
        val notes = listOf(note(selectedText = ""), note(selectedText = "乙"))

        assertEquals(listOf(1 until 2), noteHighlightRanges("甲乙丙", notes))
    }

    private fun note(selectedText: String) = Note(
        id = selectedText.ifBlank { "empty" },
        articleId = "article",
        startIndex = 0,
        endIndex = selectedText.length,
        selectedText = selectedText,
        memo = "",
        color = "#F4C95D",
        createdAt = 0L,
    )
}
