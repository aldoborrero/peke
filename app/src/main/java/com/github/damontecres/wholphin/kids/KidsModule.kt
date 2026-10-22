package com.github.damontecres.wholphin.kids

import com.github.damontecres.wholphin.data.AppDatabase
import com.github.damontecres.wholphin.kids.data.KidProfileDao
import com.github.damontecres.wholphin.kids.server.KidsServerDao
import com.github.damontecres.wholphin.kids.timer.MonotonicClock
import com.github.damontecres.wholphin.kids.timer.SystemMonotonicClock
import com.github.damontecres.wholphin.services.ProfilePolicy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
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
