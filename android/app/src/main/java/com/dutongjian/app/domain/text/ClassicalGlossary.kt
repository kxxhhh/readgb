package com.dutongjian.app.domain.text

import java.io.InputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class GlossaryEntry(
    val term: String,
    val explanation: String,
    val category: String,
)

object ClassicalGlossary {
    private var entries: List<GlossaryEntry> = emptyList()

    fun load(input: InputStream) {
        runCatching {
            entries = Json { ignoreUnknownKeys = true }
                .decodeFromString<List<GlossaryAssetEntry>>(input.bufferedReader().use { it.readText() })
                .map { GlossaryEntry(it.term, it.explanation, it.category) }
        }
    }

    fun find(text: String): List<GlossaryEntry> = entries
        .filter { it.term.length > 1 && text.contains(it.term) }
        .distinctBy { it.term }

    @Serializable
    private data class GlossaryAssetEntry(
        val term: String,
        val explanation: String,
        val category: String = "字词",
    )
}
