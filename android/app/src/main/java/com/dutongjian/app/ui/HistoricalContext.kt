package com.dutongjian.app.ui

import com.dutongjian.app.domain.model.ReadingItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

internal data class HistoricalContext(
    val people: List<String> = emptyList(),
    val places: List<String> = emptyList(),
    val officials: List<String> = emptyList(),
    val decisions: List<String> = emptyList(),
    val annotations: List<String> = emptyList(),
)

internal data class DecisionOption(
    val title: String,
    val detail: String,
)

internal data class ReadableHistoricalNote(
    val text: String,
    val position: Int,
    val people: List<String> = emptyList(),
    val places: List<String> = emptyList(),
)

internal fun formatHistoricalNotes(notes: String): List<ReadableHistoricalNote> {
    if (notes.isBlank()) return emptyList()
    val root = runCatching { Json.parseToJsonElement(notes) }.getOrNull() as? JsonObject ?: return emptyList()
    val noteArray = root["ExtRef_Children_hu_notes"] as? JsonArray ?: return emptyList()
    return noteArray.mapNotNull { element ->
        val note = element as? JsonObject ?: return@mapNotNull null
        val text = note["note_content_jianti_auto"]?.jsonPrimitive?.contentOrNull
            ?: note["note_content"]?.jsonPrimitive?.contentOrNull
            ?: ""
        val position = note["start_index"]?.jsonPrimitive?.intOrNull ?: 0
        if (text.isBlank()) return@mapNotNull null
        ReadableHistoricalNote(
            text = text,
            position = position,
            people = childNames(note["ExtRef_Children_people"], "people_name_jianti_auto", "people_name"),
            places = childNames(note["ExtRef_Children_places"], "place_name_jianti_auto", "place_name"),
        )
    }.sortedBy { it.position }
}

private fun childNames(element: JsonElement?, vararg keys: String): List<String> =
    (element as? JsonArray).orEmpty().mapNotNull { child ->
        val objectValue = child as? JsonObject ?: return@mapNotNull null
        keys.firstNotNullOfOrNull { key -> objectValue[key]?.jsonPrimitive?.contentOrNull }
    }.distinct()

internal fun parseHistoricalContext(notes: String): HistoricalContext {
    if (notes.isBlank()) return HistoricalContext()
    val values = linkedMapOf(
        "people" to linkedSetOf<String>(),
        "places" to linkedSetOf<String>(),
        "officials" to linkedSetOf<String>(),
        "decisions" to linkedSetOf<String>(),
        "annotations" to linkedSetOf<String>(),
    )
    val root = runCatching { Json.parseToJsonElement(notes) }.getOrNull() ?: return HistoricalContext()

    fun add(bucket: String, value: String?) {
        val clean = value?.trim().orEmpty()
        if (clean.isNotBlank() && clean.length <= 180) values.getValue(bucket).add(clean)
    }

    fun visit(element: JsonElement) {
        when (element) {
            is JsonObject -> element.forEach { (key, value) ->
                val primitive = (value as? JsonPrimitive)?.contentOrNull
                when {
                    key == "people_name_jianti_auto" || key == "people_name" || key == "people_xingming" -> add("people", primitive)
                    key == "place_name_jianti_auto" || key == "place_name" -> add("places", primitive)
                    key == "official_name" -> add("officials", primitive)
                    key == "decision_name" -> add("decisions", primitive)
                    key == "note_content_jianti_auto" || key == "note_content" -> add("annotations", primitive)
                }
                visit(value)
            }
            is JsonArray -> element.forEach(::visit)
            else -> Unit
        }
    }
    visit(root)
    return HistoricalContext(
        people = values.getValue("people").toList(),
        places = values.getValue("places").toList(),
        officials = values.getValue("officials").toList(),
        decisions = values.getValue("decisions").toList(),
        annotations = values.getValue("annotations").toList().take(4),
    )
}

internal fun localDecisionOptions(item: ReadingItem, context: HistoricalContext): List<DecisionOption> {
    if (context.decisions.isNotEmpty()) {
        return context.decisions.take(3).map { decision ->
            DecisionOption(decision, "围绕“$decision”回看原文、人物立场与结果。")
        }
    }
    val actors = context.people.take(3).joinToString("、").ifBlank { "相关人物" }
    return listOf(
        DecisionOption("先观其变", "结合 ${item.dynasty} 的背景，先利用现有局势，避免在信息不足时扩大冲突。相关人物：$actors。"),
        DecisionOption("主动出击", "从原文中寻找出兵、任用或处置的证据，再评估主动行动的收益与代价。"),
        DecisionOption("稳守边境", "优先保护民生与制度秩序，把“减少损失”作为判断这段事件的另一条路径。"),
    )
}
