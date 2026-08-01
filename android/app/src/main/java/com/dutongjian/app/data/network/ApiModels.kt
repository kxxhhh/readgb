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
