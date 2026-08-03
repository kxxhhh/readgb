package com.dutongjian.app.domain.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassicalGrammarAnalysisTest {
    @Test
    fun parsesGrammarMarkdownTableAndSkipsHeader() {
        val rows = parseGrammarAnalysisTable(
            """
            | 原句 | 主谓宾或核心结构 | 特殊句式/虚词 | 直译 |
            | --- | --- | --- | --- |
            | 智伯伐赵襄子。 | 智伯（主）伐（谓）赵襄子（宾） | 省略 | 智伯讨伐赵襄子。 |
            """.trimIndent(),
        )

        assertEquals(1, rows.size)
        assertEquals("智伯伐赵襄子。", rows.single().original)
        assertTrue(rows.single().structure.contains("主"))
    }

    @Test
    fun malformedOrPlainTextFallsBackToNoRows() {
        assertTrue(parseGrammarAnalysisTable("AI 无法按表格回答").isEmpty())
    }

    @Test
    fun readsOptionalSourceLocationColumn() {
        val row = parseGrammarAnalysisTable(
            "| 原句 | 结构 | 句式 | 直译 | 原文定位 |\n| --- | --- | --- | --- | --- |\n| 智伯伐赵襄子。 | 主谓宾 | 省略 | 智伯讨伐 | 伐赵襄子 |",
        ).single()

        assertEquals("伐赵襄子", row.sourceText)
    }
}
