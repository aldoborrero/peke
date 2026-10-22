package dev.aldoborrero.peke.kids

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.aldoborrero.peke.data.AppDatabase
import dev.aldoborrero.peke.kids.data.KidProfileDao
import dev.aldoborrero.peke.kids.server.KidsServerDao
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
    fun kidProfileDao(db: AppDatabase): KidProfileDao = db.kidProfileDao()

    @Provides
    @Singleton
    fun kidsServerDao(db: AppDatabase): KidsServerDao = db.kidsServerDao()

    @Provides
    @Singleton
    fun monotonicClock(): MonotonicClock = SystemMonotonicClock

    @Provides
    @Singleton
    fun profilePolicy(policy: KidsProfilePolicy): ProfilePolicy = policy
}
