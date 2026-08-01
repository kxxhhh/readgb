package com.dutongjian.app.data.network

import retrofit2.http.GET
import retrofit2.http.Query

interface DutongjianApi {
    @GET("api/home")
    suspend fun home(): ApiEnvelope<HomeData>

    @GET("api/search")
    suspend fun search(@Query("q") query: String): ApiEnvelope<ItemListData>

    @GET("api/items")
    suspend fun items(@Query("category") category: String? = null): ApiEnvelope<ItemListData>

    @GET("api/detail/{id}")
    suspend fun detail(@retrofit2.http.Path("id") id: String): ApiEnvelope<ItemDto>
}
