package com.dutongjian.app.ui

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
    val annotations: List<String> = emptyList(),
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
        annotations = values.getValue("annotations").toList().take(4),
    )
}
