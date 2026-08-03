package com.dutongjian.app.domain.text

data class GrammarAnalysisRow(
    val original: String,
    val structure: String,
    val grammar: String,
    val translation: String,
)

fun parseGrammarAnalysisTable(markdown: String): List<GrammarAnalysisRow> = markdown
    .lineSequence()
    .filter { it.trim().startsWith("|") }
    .mapNotNull { line ->
        val cells = line.trim().trim('|').split('|').map(String::trim)
        if (cells.size < 4 || cells.take(4).all(::isMarkdownSeparator)) return@mapNotNull null
        if (cells[0] in setOf("原句", "原文")) return@mapNotNull null
        cells.take(4).takeIf { it.all(String::isNotBlank) }?.let {
            GrammarAnalysisRow(it[0], it[1], it[2], it[3])
        }
    }
    .toList()

private fun isMarkdownSeparator(value: String): Boolean =
    value.isNotEmpty() && value.all { it == '-' || it == ':' || it.isWhitespace() }
