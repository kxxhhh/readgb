package com.dutongjian.app.domain.repository

import com.dutongjian.app.domain.model.AiSettings
import com.dutongjian.app.domain.model.AiConversationTurn
import com.dutongjian.app.domain.model.AiTask
import com.dutongjian.app.domain.model.ReadingItem

interface AiRepository {
    suspend fun loadSettings(): Result<AiSettings>
    suspend fun saveSettings(baseUrl: String, model: String, apiKey: String?): Result<AiSettings>
    suspend fun clearApiKey(): Result<AiSettings>
    suspend fun generate(
        item: ReadingItem,
        task: AiTask,
        conversation: List<AiConversationTurn> = emptyList(),
        followUp: String? = null,
    ): Result<String>
}
