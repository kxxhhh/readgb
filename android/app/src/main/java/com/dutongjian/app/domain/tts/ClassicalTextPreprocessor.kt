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

    fun sentences(text: String): List<String> = prepare(text)
        .replace(Regex("\\s+"), " ")
        .split(Regex("(?<=[。；？！])"))
        .map(String::trim)
        .filter(String::isNotBlank)
}
