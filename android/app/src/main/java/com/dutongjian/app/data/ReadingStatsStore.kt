package com.dutongjian.app.data

import android.content.Context
import com.dutongjian.app.domain.model.ReadingItem
import com.dutongjian.app.domain.model.ReadingStats
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

interface ReadingStatsRecorder {
    fun snapshot(): ReadingStats
    fun open(item: ReadingItem): ReadingStats
    fun close(): ReadingStats
}

@Singleton
class ReadingStatsStore @Inject constructor(@ApplicationContext context: Context) : ReadingStatsRecorder {
    private val preferences = context.getSharedPreferences("reading_stats", Context.MODE_PRIVATE)

    override
    @Synchronized
    fun snapshot(): ReadingStats = ReadingStats(
        totalSeconds = preferences.getLong(KEY_SECONDS, 0L),
        readCharacters = preferences.getLong(KEY_CHARACTERS, 0L),
        openedItems = preferences.getInt(KEY_ITEMS, 0),
        coveredVolumes = preferences.getStringSet(KEY_VOLUMES, emptySet()).orEmpty().size,
        dailySeconds = readDailySeconds(),
    )

    override
    @Synchronized
    fun open(item: ReadingItem): ReadingStats {
        closeSession()
        val volumes = preferences.getStringSet(KEY_VOLUMES, emptySet()).orEmpty().toMutableSet()
        item.volumeId?.let(volumes::add)
        preferences.edit()
            .putLong(KEY_SESSION_STARTED, System.currentTimeMillis())
            .putLong(KEY_CHARACTERS, preferences.getLong(KEY_CHARACTERS, 0L) + item.content.length)
            .putInt(KEY_ITEMS, preferences.getInt(KEY_ITEMS, 0) + 1)
            .putStringSet(KEY_VOLUMES, volumes)
            .apply()
        return snapshot()
    }

    override
    @Synchronized
    fun close(): ReadingStats {
        closeSession()
        return snapshot()
    }

    private fun closeSession() {
        val started = preferences.getLong(KEY_SESSION_STARTED, 0L)
        if (started <= 0L) return
        val elapsed = ((System.currentTimeMillis() - started) / 1000L).coerceAtLeast(0L)
        preferences.edit()
            .putLong(KEY_SECONDS, preferences.getLong(KEY_SECONDS, 0L) + elapsed)
            .putString(KEY_DAILY, encodeDailySeconds(readDailySeconds(), sessionDay(started), elapsed))
            .remove(KEY_SESSION_STARTED)
            .apply()
    }

    private fun readDailySeconds(): Map<String, Long> = preferences.getString(KEY_DAILY, null)
        .orEmpty()
        .split('|')
        .mapNotNull { entry ->
            val parts = entry.split('=', limit = 2)
            if (parts.size != 2) return@mapNotNull null
            val day = parts[0].takeIf(String::isNotBlank) ?: return@mapNotNull null
            day to (parts[1].toLongOrNull() ?: return@mapNotNull null)
        }
        .toMap()

    private fun encodeDailySeconds(values: Map<String, Long>, day: String, elapsed: Long): String =
        (values + (day to ((values[day] ?: 0L) + elapsed)))
            .filterValues { it > 0L }
            .toSortedMap()
            .entries
            .toList()
            .takeLast(35)
            .joinToString("|") { (key, seconds) -> "$key=$seconds" }

    private fun sessionDay(started: Long): String = Instant.ofEpochMilli(started)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .toString()

    private companion object {
        const val KEY_SECONDS = "total_seconds"
        const val KEY_CHARACTERS = "read_characters"
        const val KEY_ITEMS = "opened_items"
        const val KEY_VOLUMES = "covered_volumes"
        const val KEY_SESSION_STARTED = "session_started"
        const val KEY_DAILY = "daily_seconds"
    }
}
