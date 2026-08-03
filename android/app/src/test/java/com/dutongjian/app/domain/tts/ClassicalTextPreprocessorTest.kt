package com.dutongjian.app.domain.tts

import org.junit.Assert.assertEquals
import org.junit.Test

class ClassicalTextPreprocessorTest {
    @Test
    fun sentencesKeepAllTextAfterTerminalPunctuation() {
        assertEquals(
            listOf("第一句。", "第二句.", "第三句？", "第四句！", "第五句……", "第六句。"),
            ClassicalTextPreprocessor.sentences("第一句。第二句.第三句？第四句！第五句……第六句。"),
        )
    }

    @Test
    fun pronunciationHintsDoNotBreakSentenceQueue() {
        assertEquals(
            listOf("长安（cháng ān）。", "单于（chán yú）出兵。"),
            ClassicalTextPreprocessor.sentences("长安。单于出兵。"),
        )
    }

    @Test
    fun closingQuotesAndBracketsStayWithTheSentence() {
        assertEquals(
            listOf("他说：“可以。”", "随后问：‘为何？’", "最后说……", "完。"),
            ClassicalTextPreprocessor.sentences("他说：“可以。”随后问：‘为何？’最后说……完。"),
        )
    }

    @Test
    fun sentenceRangesUseTheSameBoundariesAsTts() {
        val text = "甲。乙...\n丙！"
        assertEquals(
            listOf("甲。", "乙...", "丙！"),
            ClassicalTextPreprocessor.sentenceRanges(text).map { range ->
                text.substring(range.first, range.last + 1)
            },
        )
    }

    @Test
    fun mixedClosingQuotesStayWithTheSentence() {
        val text = "他说：\"可以。\"她答：‘好！’"
        assertEquals(
            listOf("他说：\"可以。\"", "她答：‘好！’"),
            ClassicalTextPreprocessor.sentences(text),
        )
    }
}
