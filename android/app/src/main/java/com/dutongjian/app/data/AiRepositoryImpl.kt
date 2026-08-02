package com.dutongjian.app.data

import android.net.Uri
import com.dutongjian.app.domain.model.AiSettings
import com.dutongjian.app.domain.model.AiTask
import com.dutongjian.app.domain.model.ReadingItem
import com.dutongjian.app.domain.repository.AiRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class AiRepositoryImpl @Inject constructor(
    private val settingsStore: AiSettingsStore,
    private val json: kotlinx.serialization.json.Json,
    @Named("ai") private val client: OkHttpClient,
) : AiRepository {
    override suspend fun loadSettings(): Result<AiSettings> = runCatching {
        settingsStore.load().toDomain()
    }

    override suspend fun saveSettings(baseUrl: String, model: String, apiKey: String?): Result<AiSettings> = runCatching {
        val normalizedUrl = validateBaseUrl(baseUrl)
        val normalizedModel = model.trim().also { require(it.isNotBlank()) { "模型不能为空" } }
        settingsStore.save(normalizedUrl, normalizedModel, apiKey?.trim())
        settingsStore.load().toDomain()
    }

    override suspend fun clearApiKey(): Result<AiSettings> = runCatching {
        settingsStore.clearApiKey()
        settingsStore.load().toDomain()
    }

    override suspend fun generate(item: ReadingItem, task: AiTask): Result<String> = withContext(Dispatchers.IO) {
        try {
            val settings = settingsStore.load()
            val url = chatCompletionsUrl(validateBaseUrl(settings.baseUrl))
            require(settings.model.isNotBlank()) { "请先配置模型" }
            require(settings.apiKey.isNotBlank() || isLocalEndpoint(url)) { "请先在 AI 设置中填写 API Key" }

            val body = buildJsonObject {
                put("model", settings.model)
                put("temperature", 0.2)
                put("max_tokens", 1800)
                putJsonArray("messages") {
                    add(buildJsonObject {
                        put("role", "system")
                        put("content", systemPrompt(task))
                    })
                    add(buildJsonObject {
                        put("role", "user")
                        put("content", aiPrompt(item, task))
                    })
                }
            }
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .apply {
                    if (settings.apiKey.isNotBlank()) header("Authorization", "Bearer ${settings.apiKey}")
                }
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body.string()
                if (!response.isSuccessful) {
                    val message = runCatching {
                        json.parseToJsonElement(responseBody).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                    }.getOrNull()
                    error("AI 服务返回 HTTP ${response.code}${message?.let { ": $it" }.orEmpty()}")
                }
                val content = json.parseToJsonElement(responseBody)
                    .jsonObject["choices"]?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content
                require(!content.isNullOrBlank()) { "AI 返回内容为空" }
                Result.success(content)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: IOException) {
            Result.failure(IllegalStateException("AI 网络请求失败，请检查 URL、网络和服务状态", error))
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private fun systemPrompt(task: AiTask): String = when (task) {
        AiTask.SUMMARY -> "你是中国古代史研究助手。只依据用户提供的史料，不补造史实。用简体中文输出：事件摘要、时间与政权、关键人物及立场、因果链、值得核对的原文证据。"
        AiTask.CLASSICAL_TRANSLATION -> "你是古汉语翻译助手。只依据用户提供的繁体原文，按语义分句输出 Markdown 表格：古文原句 | 白话翻译 | 关键词/语法。不要漏译，不要添加史料外的事实。"
        AiTask.WORD_GLOSSARY -> "你是古汉语训诂助手。只依据用户提供的繁体原文和白话，挑出真正影响理解的古文词语，输出 Markdown 表格：古文词语 | 白话对应 | 本句语境说明。不要把每个虚词机械罗列。"
    }

    private fun validateBaseUrl(raw: String): String {
        val normalized = raw.trim().removeSuffix("/")
        val uri = Uri.parse(normalized)
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()
        require(!normalized.contains(' ')) { "API URL 不能包含空格" }
        require(scheme == "https" || (scheme == "http" && host in LOCAL_HOSTS)) {
            "API URL 必须使用 HTTPS；本机调试可使用 localhost、127.0.0.1 或 10.0.2.2"
        }
        require(!host.isNullOrBlank()) { "API URL 缺少主机名" }
        return normalized
    }

    private fun chatCompletionsUrl(baseUrl: String): String = if (baseUrl.endsWith("/chat/completions")) {
        baseUrl
    } else {
        "$baseUrl/chat/completions"
    }

    private fun isLocalEndpoint(url: String): Boolean = Uri.parse(url).host?.lowercase() in LOCAL_HOSTS

    private fun StoredAiSettings.toDomain() = AiSettings(baseUrl = baseUrl, model = model, hasApiKey = apiKey.isNotBlank())
}

internal fun aiPrompt(item: ReadingItem, task: AiTask): String {
    val original = item.original.ifBlank { item.content }.take(MAX_AI_TEXT_LENGTH)
    val translation = item.translation.ifBlank { item.content }.take(MAX_AI_TEXT_LENGTH)
    val notes = item.notes.take(MAX_AI_NOTES_LENGTH)
    return when (task) {
        AiTask.SUMMARY -> """
            条目：${item.title}
            朝代/分类：${item.dynasty} / ${item.category}
            导读：${item.summary}
            繁体原文：$original
            已有白话：$translation
            结构化注释：$notes
        """.trimIndent()
        AiTask.CLASSICAL_TRANSLATION -> """
            条目：${item.title}
            繁体原文：$original
            现有白话（只用于校对，不要盲从）：$translation
        """.trimIndent()
        AiTask.WORD_GLOSSARY -> """
            条目：${item.title}
            繁体原文：$original
            现有白话：$translation
        """.trimIndent()
    }
}

private val LOCAL_HOSTS = setOf("localhost", "127.0.0.1", "10.0.2.2")
private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
private const val MAX_AI_TEXT_LENGTH = 12_000
private const val MAX_AI_NOTES_LENGTH = 4_000
