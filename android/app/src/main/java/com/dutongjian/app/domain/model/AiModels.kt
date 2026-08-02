package com.dutongjian.app.domain.model

enum class AiTask(val label: String) {
    SUMMARY("AI总结"),
    CLASSICAL_TRANSLATION("AI逐句对照"),
    WORD_GLOSSARY("AI词语对照"),
}

data class AiSettings(
    val baseUrl: String = "https://api.openai.com/v1",
    val model: String = "gpt-4o-mini",
    val hasApiKey: Boolean = false,
)
