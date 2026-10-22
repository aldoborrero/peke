package dev.aldoborrero.peke.kids

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import dev.aldoborrero.peke.kids.data.KidProfileDao
import dev.aldoborrero.peke.kids.data.KidsDatabase
import dev.aldoborrero.peke.kids.server.KidsServerDao
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [KidsDatabaseModule::class],
)
object TestKidsDatabaseModule {
    @Provides
    @Singleton
    fun kidsDatabase(
        @ApplicationContext context: Context,
    ): KidsDatabase =
        Room
            .inMemoryDatabaseBuilder(context, KidsDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    @Provides
    @Singleton
    fun kidProfileDao(db: KidsDatabase): KidProfileDao = db.kidProfileDao()

    @Provides
    @Singleton
    fun kidsServerDao(db: KidsDatabase): KidsServerDao = db.kidsServerDao()
}
