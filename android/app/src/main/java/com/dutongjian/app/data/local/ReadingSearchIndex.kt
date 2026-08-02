package com.dutongjian.app.data.local

import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase

internal object ReadingSearchIndex {
    fun ensure(database: SupportSQLiteDatabase) {
        runCatching {
            database.execSQL(
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS reading_items_fts USING fts5(
                    id UNINDEXED, title, summary, content, original, translation, dynasty, tags,
                    tokenize = 'unicode61 remove_diacritics 1'
                )
                """.trimIndent(),
            )
            database.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS reading_items_fts_ai AFTER INSERT ON reading_items BEGIN
                    INSERT INTO reading_items_fts(id, title, summary, content, original, translation, dynasty, tags)
                    VALUES (new.id, new.title, new.summary, new.content, new.original, new.translation, new.dynasty, new.tags);
                END
                """.trimIndent(),
            )
            database.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS reading_items_fts_au AFTER UPDATE ON reading_items BEGIN
                    DELETE FROM reading_items_fts WHERE id = old.id;
                    INSERT INTO reading_items_fts(id, title, summary, content, original, translation, dynasty, tags)
                    VALUES (new.id, new.title, new.summary, new.content, new.original, new.translation, new.dynasty, new.tags);
                END
                """.trimIndent(),
            )
            database.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS reading_items_fts_ad AFTER DELETE ON reading_items BEGIN
                    DELETE FROM reading_items_fts WHERE id = old.id;
                END
                """.trimIndent(),
            )
            database.execSQL(
                """
                INSERT INTO reading_items_fts(id, title, summary, content, original, translation, dynasty, tags)
                SELECT items.id, items.title, items.summary, items.content, items.original, items.translation, items.dynasty, items.tags
                FROM reading_items AS items
                WHERE NOT EXISTS (
                    SELECT 1 FROM reading_items_fts AS indexed WHERE indexed.id = items.id
                )
                """.trimIndent(),
            )
        }.onFailure { error ->
            Log.w("ReadingSearchIndex", "FTS5 unavailable; local search will use the Room fallback", error)
        }
    }
}
