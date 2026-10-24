package dev.aldoborrero.peke.kids.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.aldoborrero.peke.data.Converters
import dev.aldoborrero.peke.kids.server.KidsServer
import dev.aldoborrero.peke.kids.server.KidsServerDao
import dev.aldoborrero.peke.kids.setup.TvSetupDao
import dev.aldoborrero.peke.kids.setup.TvSetupEntity
import dev.aldoborrero.peke.kids.setup.TvSetupStep

/**
 * Peke's own data, kept apart from the app database so the two schemas change independently
 */
@Database(
    entities = [
        KidProfile::class,
        KidsServer::class,
        TvSetupEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class KidsDatabase : RoomDatabase() {
    abstract fun kidProfileDao(): KidProfileDao

    abstract fun kidsServerDao(): KidsServerDao

    abstract fun tvSetupDao(): TvSetupDao

    companion object {
        const val NAME = "peke_kids"

        /**
         * Adds `tv_setup`. A TV that already has kids (test TVs only, Peke is unreleased) gets a finished setup for
         * the server with the most kids; its URL is in the app database, out of reach here, so it is left empty and
         * [dev.aldoborrero.peke.kids.setup.TvSetupRepository] takes it from the server row.
         */
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `tv_setup` (`id` INTEGER NOT NULL, `serverId` TEXT NOT NULL, " +
                            "`serverUrl` TEXT NOT NULL, `step` TEXT NOT NULL, `lastKidRowId` INTEGER, `completedAt` INTEGER, " +
                            "PRIMARY KEY(`id`))",
                    )
                    db.execSQL(
                        "INSERT INTO `tv_setup` (`id`, `serverId`, `serverUrl`, `step`, `lastKidRowId`, `completedAt`) " +
                            "SELECT ${TvSetupEntity.ID}, `serverId`, '', '${TvSetupStep.DONE.name}', NULL, ? FROM `kid_profiles` " +
                            "WHERE `serverId` IS NOT NULL GROUP BY `serverId` ORDER BY COUNT(*) DESC LIMIT 1",
                        arrayOf(System.currentTimeMillis()),
                    )
                }
            }
    }
}
