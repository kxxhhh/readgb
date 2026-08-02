package com.dutongjian.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaceDao {
    @Query("SELECT * FROM historical_places ORDER BY ancientName ASC")
    fun observeAll(): Flow<List<HistoricalPlaceEntity>>

    @Upsert
    suspend fun upsertAll(places: List<HistoricalPlaceEntity>)
}
