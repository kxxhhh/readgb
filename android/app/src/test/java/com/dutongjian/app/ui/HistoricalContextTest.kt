package com.dutongjian.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoricalContextTest {
    @Test
    fun parsesStructuredPeoplePlacesAndAnnotationsForSandbox() {
        val context = parseHistoricalContext(
            """
            {
              "ExtRef_Children_people": [{"people_name_jianti_auto": "霍光"}],
              "ExtRef_Children_places": [{"place_name_jianti_auto": "五原"}],
              "ExtRef_Children_officials": [{"official_name": "度辽将军"}],
              "ExtRef_Children_hu_notes": [{"note_content_jianti_auto": "五原郡属并州。"}]
            }
            """.trimIndent(),
        )

        assertEquals(listOf("霍光"), context.people)
        assertEquals(listOf("五原"), context.places)
        assertEquals(listOf("度辽将军"), context.officials)
        assertTrue(context.annotations.contains("五原郡属并州。"))
    }

    @Test
    fun formatsNestedHuNotesAsReadableEntries() {
        val notes = formatHistoricalNotes(
            """
            {"ExtRef_Children_hu_notes":[
              {"start_index":4,"note_content_jianti_auto":"复，扶又翻。","ExtRef_Children_people":[]},
              {"start_index":0,"note_content_jianti_auto":"元贵靡，楚主解忧长男也。","ExtRef_Children_people":[{"people_name_jianti_auto":"元贵靡"}]}
            ]}
            """.trimIndent(),
        )

        assertEquals(listOf(0, 4), notes.map { it.position })
        assertEquals("元贵靡，楚主解忧长男也。", notes.first().text)
        assertEquals(listOf("元贵靡"), notes.first().people)
    }
}
