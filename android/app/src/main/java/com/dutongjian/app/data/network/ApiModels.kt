package com.dutongjian.app.data.network

import kotlinx.serialization.Serializable

@Serializable
data class ApiEnvelope<T>(
    val code: Int,
    val message: String,
    val data: T? = null,
)

@Serializable
data class ItemDto(
    val id: String,
    val title: String,
    val category: String,
    val dynasty: String,
    val summary: String,
    val content: String,
    val source_url: String,
    val updated_at: String,
    val section: String = "资治通鉴",
    val volume_id: String? = null,
    val year_id: String? = null,
    val original: String = "",
    val translation: String = "",
    val notes: String = "",
    val tags: List<String> = emptyList(),
)

@Serializable
data class HomeData(
    val items: List<ItemDto> = emptyList(),
    val categories: List<String> = emptyList(),
)

@Serializable
data class ItemListData(
    val items: List<ItemDto> = emptyList(),
    val category: String? = null,
    val query: String? = null,
)

@Serializable
data class SectionDto(
    val id: String,
    val title: String,
    val description: String,
    val source_url: String,
    val sort_order: Int,
)

@Serializable
data class VolumeDto(
    val id: String,
    val section_id: String,
    val title: String,
    val dynasty: String,
    val sort_order: Int,
)

@Serializable
data class YearDto(
    val id: String,
    val volume_id: String,
    val title: String,
    val era: String,
    val sort_order: Int,
    val year_int: Int? = null,
)

@Serializable
data class SectionListData(val sections: List<SectionDto> = emptyList())

@Serializable
data class VolumeListData(
    val section_id: String? = null,
    val volumes: List<VolumeDto> = emptyList(),
)

@Serializable
data class YearListData(
    val volume_id: String? = null,
    val years: List<YearDto> = emptyList(),
)

@Serializable
data class KnowledgeDto(
    val id: String,
    val title: String,
    val category: String,
    val summary: String,
    val content: String,
    val source_url: String,
    val updated_at: String,
)

@Serializable
data class KnowledgeListData(
    val category: String? = null,
    val query: String? = null,
    val items: List<KnowledgeDto> = emptyList(),
    val categories: List<String> = emptyList(),
)
