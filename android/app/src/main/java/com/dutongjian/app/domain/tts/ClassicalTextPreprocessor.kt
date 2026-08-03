package com.dutongjian.app.domain.tts

object ClassicalTextPreprocessor {
    private val replacements = linkedMapOf(
        "单于" to "chán yú",
        "长安" to "cháng ān",
        "重耳" to "chóng ěr",
        "乐羊" to "yuè yáng",
        "王莽" to "wáng mǎng",
        "不更" to "bù gēng",
    )

    fun prepare(text: String): String = replacements.entries.fold(text) { value, (source, reading) ->
        value.replace(source, "$source（$reading）")
    }

    fun sentences(text: String): List<String> {
        val normalized = prepare(text).replace(Regex("\\s+"), " ").trim()
        if (normalized.isBlank()) return emptyList()
        return sentenceRanges(normalized).map { range ->
            normalized.substring(range.first, range.last + 1)
        }
    }

    fun sentenceRanges(text: String): List<IntRange> {
        if (text.isBlank()) return emptyList()
        val result = mutableListOf<IntRange>()
        var start = 0
        var index = 0
        while (index < text.length) {
            val character = text[index]
            if (character in SENTENCE_TERMINATORS || character == '…') {
                while (index + 1 < text.length && text[index + 1] in BOUNDARY_SUFFIXES) {
                    index += 1
                }
                addTrimmedRange(text, start, index + 1, result)
                start = index + 1
            }
            index += 1
        }
        addTrimmedRange(text, start, text.length, result)
        return result
    }

    private fun addTrimmedRange(text: String, start: Int, endExclusive: Int, output: MutableList<IntRange>) {
        var first = start
        var last = endExclusive
        while (first < last && text[first].isWhitespace()) first += 1
        while (last > first && text[last - 1].isWhitespace()) last -= 1
        if (first < last) output += first until last
    }

    private val SENTENCE_TERMINATORS = setOf('。', '．', '｡', '；', ';', '？', '?', '！', '!', '.')
    private val BOUNDARY_SUFFIXES = SENTENCE_TERMINATORS + setOf(
        '…', '︙', '⁇', '⁈', '⁉', '”', '’', '＂', '＇', '"', '\'', '」', '』', '》', '】', '）', ')', '］', ']', '}', '〉', '〕', '〗', '〙', '〛', '»',
    )
}
