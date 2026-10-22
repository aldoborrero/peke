package dev.aldoborrero.peke.services.hilt

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.aldoborrero.peke.data.AppDatabase
import dev.aldoborrero.peke.data.ItemPlaybackDao
import dev.aldoborrero.peke.data.JellyfinServerDao
import dev.aldoborrero.peke.data.LibraryDisplayInfoDao
import dev.aldoborrero.peke.data.PlaybackEffectDao
import dev.aldoborrero.peke.data.RememberedTabDao
import dev.aldoborrero.peke.data.SeriesTrackChoiceDao
import dev.aldoborrero.peke.preferences.AppPreferences
import dev.aldoborrero.peke.preferences.AppPreferencesSerializer
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(
        @ApplicationContext context: Context,
    ): AppDatabase =
        Room
            .databaseBuilder(
                context,
                AppDatabase::class.java,
                "peke",
            )
//            .setQueryCallback({ sqlQuery, args ->
//                Timber.v("sqlQuery=$sqlQuery, args=$args")
//            }, PekeDispatchers.IO.asExecutor())
            .build()

    @Provides
    @Singleton
    fun serverDao(db: AppDatabase): JellyfinServerDao = db.serverDao()

    @Provides
    @Singleton
    fun itemPlaybackDao(db: AppDatabase): ItemPlaybackDao = db.itemPlaybackDao()

    @Provides
    @Singleton
    fun libraryDisplayInfoDao(db: AppDatabase): LibraryDisplayInfoDao = db.libraryDisplayInfoDao()

    @Provides
    @Singleton
    fun playbackEffectDao(db: AppDatabase): PlaybackEffectDao = db.playbackEffectDao()

    @Provides
    @Singleton
    fun rememberedTabDao(db: AppDatabase): RememberedTabDao = db.rememberedTabDao()

    @Provides
    @Singleton
    fun seriesTrackChoiceTabDao(db: AppDatabase): SeriesTrackChoiceDao = db.seriesTrackChoiceDao()

    @Provides
    @Singleton
    fun userPreferencesDataStore(
        @ApplicationContext context: Context,
        userPreferencesSerializer: AppPreferencesSerializer,
    ): DataStore<AppPreferences> =
        DataStoreFactory.create(
            serializer = userPreferencesSerializer,
            produceFile = { context.dataStoreFile("app_preferences.pb") },
            corruptionHandler =
                ReplaceFileCorruptionHandler(
                    produceNewData = { AppPreferences.getDefaultInstance() },
                ),
        )

    @Provides
    @Singleton
    fun keyValueDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("key_value") },
        )
}
