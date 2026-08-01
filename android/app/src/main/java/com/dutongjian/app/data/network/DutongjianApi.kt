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

    @GET("api/sections")
    suspend fun sections(): ApiEnvelope<SectionListData>

    @GET("api/sections/{id}/volumes")
    suspend fun volumes(@retrofit2.http.Path("id") sectionId: String): ApiEnvelope<VolumeListData>

    @GET("api/volumes/{id}/years")
    suspend fun years(@retrofit2.http.Path("id") volumeId: String): ApiEnvelope<YearListData>

    @GET("api/years/{id}/items")
    suspend fun yearItems(@retrofit2.http.Path("id") yearId: String): ApiEnvelope<ItemListData>

    @GET("api/knowledge")
    suspend fun knowledge(
        @Query("q") query: String? = null,
        @Query("category") category: String? = null,
    ): ApiEnvelope<KnowledgeListData>

    @GET("api/knowledge/{id}")
    suspend fun knowledgeDetail(@retrofit2.http.Path("id") id: String): ApiEnvelope<KnowledgeDto>
}
