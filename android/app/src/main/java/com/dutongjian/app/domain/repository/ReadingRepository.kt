package com.dutongjian.app.domain.repository

import com.dutongjian.app.domain.model.HomeFeed
import com.dutongjian.app.domain.model.HistoricalPlace
import com.dutongjian.app.domain.model.KnowledgeEntry
import com.dutongjian.app.domain.model.LibrarySection
import com.dutongjian.app.domain.model.Note
import com.dutongjian.app.domain.model.ReadingItem
import com.dutongjian.app.domain.model.ReadingYear
import com.dutongjian.app.domain.model.Volume
import kotlinx.coroutines.flow.Flow

interface ReadingRepository {
    fun observeItems(): Flow<List<ReadingItem>>
    suspend fun refreshHome(): Result<HomeFeed>
    suspend fun search(query: String): Result<List<ReadingItem>>
    suspend fun setFavorite(itemId: String, favorite: Boolean)
    suspend fun recordOpened(itemId: String)
    suspend fun loadSections(): Result<List<LibrarySection>>
    suspend fun loadVolumes(sectionId: String): Result<List<Volume>>
    suspend fun loadYears(volumeId: String): Result<List<ReadingYear>>
    suspend fun loadAllYears(): Result<List<ReadingYear>>
    suspend fun loadYearItems(yearId: String): Result<List<ReadingItem>>
    suspend fun loadKnowledge(query: String? = null, category: String? = null): Result<List<KnowledgeEntry>>
    fun observeNotes(): Flow<List<Note>>
    suspend fun saveNote(note: Note)
    suspend fun deleteNote(note: Note)
    fun observePlaces(): Flow<List<HistoricalPlace>>
}
