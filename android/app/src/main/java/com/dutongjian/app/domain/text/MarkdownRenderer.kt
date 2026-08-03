package com.dutongjian.app.domain.text

sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class UnorderedList(val items: List<String>) : MarkdownBlock
    data class OrderedList(val items: List<String>) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
    data class Code(val language: String, val text: String) : MarkdownBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MarkdownBlock
    data object Divider : MarkdownBlock
}

data class MarkdownInlineStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val strikeThrough: Boolean = false,
    val link: Boolean = false,
)

data class MarkdownInlineToken(
    val text: String,
    val style: MarkdownInlineStyle = MarkdownInlineStyle(),
)

fun parseMarkdown(markdown: String): List<MarkdownBlock> {
    val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split('\n')
    val blocks = mutableListOf<MarkdownBlock>()
    var index = 0

    while (index < lines.size) {
        val line = lines[index]
        if (line.isBlank()) {
            index += 1
            continue
        }

        val fence = FENCE_PATTERN.matchEntire(line)
        if (fence != null) {
            val language = fence.groupValues[1].lowercase()
            index += 1
            val codeLines = mutableListOf<String>()
            while (index < lines.size && !FENCE_PATTERN.matches(lines[index])) {
                codeLines += lines[index]
                index += 1
            }
            if (index < lines.size) index += 1
            val code = codeLines.joinToString("\n").trimEnd()
            if (language in MARKDOWN_LANGUAGES) {
                blocks += parseMarkdown(code)
            } else {
                blocks += MarkdownBlock.Code(language, code)
            }
            continue
        }

        HEADING_PATTERN.matchEntire(line)?.let { match ->
            blocks += MarkdownBlock.Heading(
                level = match.groupValues[1].length,
                text = match.groupValues[2].trim().trimEnd('#').trim(),
            )
            index += 1
            continue
        }

        if (isDivider(line)) {
            blocks += MarkdownBlock.Divider
            index += 1
            continue
        }

        if (isTableRow(line) && index + 1 < lines.size && isTableSeparator(lines[index + 1])) {
            val headers = splitTableCells(line)
            index += 2
            val rows = mutableListOf<List<String>>()
            while (index < lines.size && lines[index].isNotBlank() && isTableRow(lines[index])) {
                rows += splitTableCells(lines[index])
                index += 1
            }
            blocks += MarkdownBlock.Table(headers, rows)
            continue
        }

        if (isQuote(line)) {
            val quoteLines = mutableListOf<String>()
            while (index < lines.size && isQuote(lines[index])) {
                quoteLines += lines[index].trimStart().removePrefix("> ").removePrefix(">").trimEnd()
                index += 1
            }
            blocks += MarkdownBlock.Quote(quoteLines.joinToString("\n"))
            continue
        }

        UNORDERED_LIST_PATTERN.matchEntire(line)?.let {
            val items = mutableListOf<String>()
            while (index < lines.size) {
                val match = UNORDERED_LIST_PATTERN.matchEntire(lines[index]) ?: break
                items += match.groupValues[1].trim()
                index += 1
            }
            blocks += MarkdownBlock.UnorderedList(items)
            continue
        }

        ORDERED_LIST_PATTERN.matchEntire(line)?.let {
            val items = mutableListOf<String>()
            while (index < lines.size) {
                val match = ORDERED_LIST_PATTERN.matchEntire(lines[index]) ?: break
                items += match.groupValues[1].trim()
                index += 1
            }
            blocks += MarkdownBlock.OrderedList(items)
            continue
        }

        val paragraphLines = mutableListOf(line)
        index += 1
        while (index < lines.size && lines[index].isNotBlank() && !startsBlock(lines, index)) {
            paragraphLines += lines[index]
            index += 1
        }
        blocks += MarkdownBlock.Paragraph(paragraphLines.joinToString("\n"))
    }
    return blocks
}

fun parseInlineMarkdown(markdown: String): List<MarkdownInlineToken> {
    val tokens = mutableListOf<MarkdownInlineToken>()

    fun append(text: String, style: MarkdownInlineStyle = MarkdownInlineStyle()) {
        if (text.isEmpty()) return
        val previous = tokens.lastOrNull()
        if (previous != null && previous.style == style) {
            tokens[tokens.lastIndex] = previous.copy(text = previous.text + text)
        } else {
            tokens += MarkdownInlineToken(text, style)
        }
    }

    var index = 0
    while (index < markdown.length) {
        if (markdown[index] == '\\' && index + 1 < markdown.length) {
            append(markdown[index + 1].toString())
            index += 2
            continue
        }

        if (markdown[index] == '`') {
            val end = markdown.indexOf('`', index + 1)
            if (end > index + 1) {
                append(markdown.substring(index + 1, end), MarkdownInlineStyle(code = true))
                index = end + 1
                continue
            }
        }

        if (markdown.startsWith("[", index)) {
            val labelEnd = markdown.indexOf("](", index + 1)
            val urlEnd = if (labelEnd >= 0) markdown.indexOf(')', labelEnd + 2) else -1
            if (labelEnd > index + 1 && urlEnd > labelEnd + 2) {
                val label = markdown.substring(index + 1, labelEnd)
                append(
                    parseInlineMarkdown(label).joinToString(separator = "") { it.text },
                    MarkdownInlineStyle(link = true),
                )
                index = urlEnd + 1
                continue
            }
        }

        val marker = when {
            markdown.startsWith("**", index) -> "**"
            markdown.startsWith("__", index) -> "__"
            markdown.startsWith("~~", index) -> "~~"
            markdown[index] == '*' || markdown[index] == '_' -> markdown[index].toString()
            else -> null
        }
        if (marker != null) {
            val end = markdown.indexOf(marker, index + marker.length)
            if (end > index + marker.length && !markdown.substring(index + marker.length, end).first().isWhitespace()) {
                val style = when (marker) {
                    "~~" -> MarkdownInlineStyle(strikeThrough = true)
                    "**", "__" -> MarkdownInlineStyle(bold = true)
                    else -> MarkdownInlineStyle(italic = true)
                }
                append(markdown.substring(index + marker.length, end), style)
                index = end + marker.length
                continue
            }
        }

        append(markdown[index].toString())
        index += 1
    }
    return tokens
}

fun markdownPlainText(markdown: String): String = parseMarkdown(markdown).joinToString("\n") { block ->
    when (block) {
        is MarkdownBlock.Heading -> block.text
        is MarkdownBlock.Paragraph -> block.text
        is MarkdownBlock.UnorderedList -> block.items.joinToString("\n") { "• $it" }
        is MarkdownBlock.OrderedList -> block.items.mapIndexed { index, item -> "${index + 1}. $item" }.joinToString("\n")
        is MarkdownBlock.Quote -> block.text
        is MarkdownBlock.Code -> block.text
        is MarkdownBlock.Table -> (listOf(block.headers) + block.rows).joinToString("\n") { it.joinToString(" | ") }
        MarkdownBlock.Divider -> ""
    }
}

private fun startsBlock(lines: List<String>, index: Int): Boolean {
    val line = lines[index]
    return FENCE_PATTERN.matches(line) ||
        HEADING_PATTERN.matches(line) ||
        isDivider(line) ||
        isQuote(line) ||
        UNORDERED_LIST_PATTERN.matches(line) ||
        ORDERED_LIST_PATTERN.matches(line) ||
        (isTableRow(line) && index + 1 < lines.size && isTableSeparator(lines[index + 1]))
}

private fun isQuote(line: String): Boolean = line.trimStart().startsWith(">")

private fun isDivider(line: String): Boolean {
    val value = line.trim().filterNot(Char::isWhitespace)
    return value.length >= 3 && value.all { it == '-' || it == '*' || it == '_' }
}

private fun isTableRow(line: String): Boolean = line.count { it == '|' } >= 1

private fun isTableSeparator(line: String): Boolean = splitTableCells(line).size >= 2 &&
    splitTableCells(line).all { it.trim().matches(Regex(":?-{3,}:?")) }

private fun splitTableCells(line: String): List<String> {
    val value = line.trim().removePrefix("|").removeSuffix("|")
    val cells = mutableListOf<String>()
    val current = StringBuilder()
    var escaped = false
    value.forEach { character ->
        when {
            character == '|' && !escaped -> {
                cells += current.toString().trim()
                current.clear()
            }
            character == '\\' && !escaped -> escaped = true
            else -> {
                current.append(character)
                escaped = false
            }
        }
    }
    cells += current.toString().trim()
    return cells
}

private val FENCE_PATTERN = Regex("^\\s{0,3}```\\s*([^\\s`]*)\\s*$")
private val HEADING_PATTERN = Regex("^\\s{0,3}(#{1,6})\\s+(.+?)\\s*$")
private val UNORDERED_LIST_PATTERN = Regex("^\\s{0,3}[-+*]\\s+(.+)$")
private val ORDERED_LIST_PATTERN = Regex("^\\s{0,3}\\d+[.)]\\s+(.+)$")
private val MARKDOWN_LANGUAGES = setOf("md", "markdown", "mdown", "mkdown")
