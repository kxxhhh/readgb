package com.dutongjian.app.di

import android.content.Context
import androidx.room.Room
import com.dutongjian.app.BuildConfig
import com.dutongjian.app.data.ReadingRepositoryImpl
import com.dutongjian.app.data.local.AppDatabase
import com.dutongjian.app.data.local.ItemDao
import com.dutongjian.app.data.network.DutongjianApi
import com.dutongjian.app.domain.repository.ReadingRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
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

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideJson() = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
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
    ).addMigrations(MIGRATION_1_2).build()

    @Provides
    fun provideItemDao(database: AppDatabase): ItemDao = database.itemDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindReadingRepository(impl: ReadingRepositoryImpl): ReadingRepository
}
