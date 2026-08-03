package com.dutongjian.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ItemEntity::class, HistoricalPlaceEntity::class, NoteEntity::class, AiResultEntity::class],
    version = 6,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun placeDao(): PlaceDao
    abstract fun noteDao(): NoteDao
    abstract fun aiResultDao(): AiResultDao
}
