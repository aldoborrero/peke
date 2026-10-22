package dev.aldoborrero.peke.kids.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dev.aldoborrero.peke.data.Converters
import dev.aldoborrero.peke.kids.server.KidsServer
import dev.aldoborrero.peke.kids.server.KidsServerDao

/**
 * Peke's own data, kept apart from the app database so the two schemas change independently
 */
@Database(
    entities = [
        KidProfile::class,
        KidsServer::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class KidsDatabase : RoomDatabase() {
    abstract fun kidProfileDao(): KidProfileDao

    abstract fun kidsServerDao(): KidsServerDao

    companion object {
        const val NAME = "peke_kids"
    }
}
