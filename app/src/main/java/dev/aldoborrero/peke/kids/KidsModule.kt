package dev.aldoborrero.peke.kids

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.aldoborrero.peke.kids.data.KidProfileDao
import dev.aldoborrero.peke.kids.data.KidsDatabase
import dev.aldoborrero.peke.kids.server.KidsServerDao
import dev.aldoborrero.peke.kids.setup.TvSetupDao
import dev.aldoborrero.peke.kids.timer.MonotonicClock
import dev.aldoborrero.peke.kids.timer.SystemMonotonicClock
import dev.aldoborrero.peke.services.ProfilePolicy
import javax.inject.Singleton

/**
 * Kids mode bindings, kept separate so test modules replacing the app modules don't need to repeat them
 */
@Module
@InstallIn(SingletonComponent::class)
object KidsModule {
    @Provides
    @Singleton
    fun monotonicClock(): MonotonicClock = SystemMonotonicClock

    @Provides
    @Singleton
    fun profilePolicy(policy: KidsProfilePolicy): ProfilePolicy = policy
}

/**
 * The kids database and its DAOs, replaced by an in-memory database in tests
 */
@Module
@InstallIn(SingletonComponent::class)
object KidsDatabaseModule {
    @Provides
    @Singleton
    fun kidsDatabase(
        @ApplicationContext context: Context,
    ): KidsDatabase =
        Room
            .databaseBuilder(context, KidsDatabase::class.java, KidsDatabase.NAME)
            .addMigrations(KidsDatabase.MIGRATION_1_2)
            .build()

    @Provides
    @Singleton
    fun kidProfileDao(db: KidsDatabase): KidProfileDao = db.kidProfileDao()

    @Provides
    @Singleton
    fun kidsServerDao(db: KidsDatabase): KidsServerDao = db.kidsServerDao()

    @Provides
    @Singleton
    fun tvSetupDao(db: KidsDatabase): TvSetupDao = db.tvSetupDao()
}
