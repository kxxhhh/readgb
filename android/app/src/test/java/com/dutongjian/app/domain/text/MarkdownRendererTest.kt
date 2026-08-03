package com.dutongjian.app.domain.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownRendererTest {
    @Test
    fun parsesCommonMarkdownBlocks() {
        val blocks = parseMarkdown(
            """
            # 史论

            **核心**判断。

            - 第一条
            - 第二条

            > 原文引句

            ```text
            code()
            ```

            | 项 | 说明 |
            | --- | --- |
            | 甲 | 乙 |
            """.trimIndent(),
        )

        assertTrue(blocks[0] is MarkdownBlock.Heading)
        assertTrue(blocks.any { it is MarkdownBlock.UnorderedList })
        assertTrue(blocks.any { it is MarkdownBlock.Quote })
        assertTrue(blocks.any { it is MarkdownBlock.Code })
        val table = blocks.filterIsInstance<MarkdownBlock.Table>().single()
        assertEquals(listOf("项", "说明"), table.headers)
        assertEquals(listOf("甲", "乙"), table.rows.single())
    }

    @Test
    fun parsesInlineStylesWithoutMarkdownMarkers() {
        val tokens = parseInlineMarkdown("**重** *斜* `码` [链](https://example.com) ~~删~~")

        assertEquals("重 斜 码 链 删", tokens.joinToString(separator = "") { it.text })
        assertTrue(tokens.any { it.style.bold && it.text == "重" })
        assertTrue(tokens.any { it.style.italic && it.text == "斜" })
        assertTrue(tokens.any { it.style.code && it.text == "码" })
        assertTrue(tokens.any { it.style.link && it.text == "链" })
        assertTrue(tokens.any { it.style.strikeThrough && it.text == "删" })
    }

    @Test
    fun markdownFenceCanContainRenderableMarkdown() {
        val blocks = parseMarkdown("```markdown\n## 可渲染\n\n正文\n```")

        assertEquals(
            listOf(MarkdownBlock.Heading(2, "可渲染"), MarkdownBlock.Paragraph("正文")),
            blocks,
        )
    }
}
