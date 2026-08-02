package com.dutongjian.app.data

import com.dutongjian.app.domain.model.AiTask
import com.dutongjian.app.domain.model.ReadingItem
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPromptTest {
    private val item = ReadingItem(
        id = "item-1",
        title = "三家分晋",
        category = "资治通鉴",
        dynasty = "周纪",
        summary = "晋国权力分裂。",
        content = "正文",
        sourceUrl = "https://example.com/item-1",
        updatedAt = "2026-08-03",
        original = "智伯伐赵襄子。",
        translation = "智伯讨伐赵襄子。",
        notes = "王导",
        tags = listOf("智伯", "赵襄子"),
    )

    @Test
    fun advancedTasksProduceTaskSpecificPrompts() {
        assertTrue(aiPrompt(item, AiTask.ROLE_DIALOGUE).contains("主要人物"))
        assertTrue(aiPrompt(item, AiTask.COUNTERFACTUAL).contains("反事实"))
        assertTrue(aiPrompt(item, AiTask.GRAMMAR_ANALYSIS).contains("主谓宾"))
    }
}
