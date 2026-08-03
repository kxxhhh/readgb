package com.dutongjian.app.domain.model

enum class AiTask(val label: String) {
    SUMMARY("AI总结"),
    CLASSICAL_TRANSLATION("AI逐句对照"),
    WORD_GLOSSARY("AI词语对照"),
    ROLE_DIALOGUE("历史角色对话"),
    COUNTERFACTUAL("反事实推演"),
    GRAMMAR_ANALYSIS("古文语法拆解"),
}

data class AiSettings(
    val baseUrl: String = "https://api.openai.com/v1",
    val model: String = "gpt-4o-mini",
    val hasApiKey: Boolean = false,
)

data class AiResult(
    val id: String,
    val itemId: String,
    val task: AiTask,
    val result: String,
    val createdAt: Long,
)
