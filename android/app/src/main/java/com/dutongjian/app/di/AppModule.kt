package com.dutongjian.app.di

import android.content.Context
import androidx.room.Room
import com.dutongjian.app.BuildConfig
import com.dutongjian.app.data.ReadingRepositoryImpl
import com.dutongjian.app.data.AiRepositoryImpl
import com.dutongjian.app.data.ReadingStatsRecorder
import com.dutongjian.app.data.ReadingStatsStore
import com.dutongjian.app.data.local.AppDatabase
import com.dutongjian.app.data.local.ItemDao
import com.dutongjian.app.data.local.ReadingSearchIndex
import com.dutongjian.app.data.network.DutongjianApi
import com.dutongjian.app.domain.repository.AiRepository
import com.dutongjian.app.domain.repository.ReadingRepository
import com.dutongjian.app.domain.tts.TtsPlayer
import com.dutongjian.app.data.tts.TtsController
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE reading_items ADD COLUMN section TEXT NOT NULL DEFAULT '资治通鉴'")
        db.execSQL("ALTER TABLE reading_items ADD COLUMN volumeId TEXT")
        db.execSQL("ALTER TABLE reading_items ADD COLUMN yearId TEXT")
        db.execSQL("ALTER TABLE reading_items ADD COLUMN original TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE reading_items ADD COLUMN translation TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE reading_items ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE reading_items ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
    }
}

private val MIGRATION_2_4 = object : androidx.room.migration.Migration(2, 4) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS historical_places (
                ancientName TEXT NOT NULL PRIMARY KEY,
                modernName TEXT NOT NULL,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                description TEXT NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS reading_notes (
                id TEXT NOT NULL PRIMARY KEY,
                articleId TEXT NOT NULL,
                startIndex INTEGER NOT NULL,
                endIndex INTEGER NOT NULL,
                selectedText TEXT NOT NULL,
                memo TEXT NOT NULL,
                color TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_reading_notes_articleId ON reading_notes(articleId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_reading_notes_createdAt ON reading_notes(createdAt)")
    }
}

private val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS ai_results (
                id TEXT NOT NULL PRIMARY KEY,
                itemId TEXT NOT NULL,
                task TEXT NOT NULL,
                result TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_ai_results_itemId ON ai_results(itemId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_ai_results_createdAt ON ai_results(createdAt)")
    }
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideJson() = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()

    @Provides
    @Singleton
    @Named("ai")
    fun provideAiOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(150, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideApi(json: Json, client: OkHttpClient): DutongjianApi = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(DutongjianApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "dutongjian.db",
    ).addMigrations(MIGRATION_1_2, MIGRATION_2_4, MIGRATION_4_5)
        .addCallback(object : RoomDatabase.Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                ReadingSearchIndex.ensure(db)
            }
        })
        .build()

    @Provides
    fun provideItemDao(database: AppDatabase): ItemDao = database.itemDao()

    @Provides
    fun providePlaceDao(database: AppDatabase): com.dutongjian.app.data.local.PlaceDao = database.placeDao()

    @Provides
    fun provideNoteDao(database: AppDatabase): com.dutongjian.app.data.local.NoteDao = database.noteDao()

    @Provides
    fun provideAiResultDao(database: AppDatabase): com.dutongjian.app.data.local.AiResultDao = database.aiResultDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindReadingRepository(impl: ReadingRepositoryImpl): ReadingRepository

    @Binds
    @Singleton
    abstract fun bindAiRepository(impl: AiRepositoryImpl): AiRepository

    @Binds
    @Singleton
    abstract fun bindTtsPlayer(impl: TtsController): TtsPlayer

    @Binds
    @Singleton
    abstract fun bindReadingStatsRecorder(impl: ReadingStatsStore): ReadingStatsRecorder
}
