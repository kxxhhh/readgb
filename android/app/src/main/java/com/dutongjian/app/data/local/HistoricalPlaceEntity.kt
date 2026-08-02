package com.dutongjian.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.dutongjian.app.domain.model.HistoricalPlace

@Entity(tableName = "historical_places")
data class HistoricalPlaceEntity(
    @PrimaryKey val ancientName: String,
    val modernName: String,
    val latitude: Double,
    val longitude: Double,
    val description: String,
)

fun HistoricalPlaceEntity.toDomain() = HistoricalPlace(ancientName, modernName, latitude, longitude, description)

fun HistoricalPlace.toEntity() = HistoricalPlaceEntity(ancientName, modernName, latitude, longitude, description)
