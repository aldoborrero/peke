package dev.aldoborrero.peke.test

import androidx.room.testing.MigrationTestHelper
import androidx.room.util.useCursor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.aldoborrero.peke.kids.data.KidsDatabase
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * The kids database's schema history starts at version 1; each later version adds its migration test here
 */
@RunWith(AndroidJUnit4::class)
class TestKidsDbMigrations {
    private val testDbName = "kids-migration-test"

    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            KidsDatabase::class.java,
        )

    @Test
    @Throws(IOException::class)
    fun createVersion1() {
        val db = helper.createDatabase(testDbName, 1)
        db.query("SELECT userId, serverId, revision, sessionLimitMinutes, serverJson FROM kid_profiles").useCursor { c ->
            Assert.assertFalse(c.moveToFirst())
        }
        db.query("SELECT serverId, lockChangedAt, offlineFailures, offlineBlockedAt FROM kids_servers").useCursor { c ->
            Assert.assertFalse(c.moveToFirst())
        }
        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate1To2WithAStoredServerAndKids() {
        helper.createDatabase(testDbName, 1).apply {
            execSQL("INSERT INTO kids_servers (serverId, features, offlineFailures) VALUES ('$SERVER', '', 0)")
            execSQL("INSERT INTO kids_servers (serverId, features, offlineFailures) VALUES ('$OTHER_SERVER', '', 0)")
            insertKid(userId = 1, serverId = SERVER)
            insertKid(userId = 2, serverId = SERVER)
            insertKid(userId = 3, serverId = OTHER_SERVER)
            close()
        }

        val db = helper.runMigrationsAndValidate(testDbName, 2, true, KidsDatabase.MIGRATION_1_2)
        db.query("SELECT id, serverId, serverUrl, step, lastKidRowId, completedAt FROM tv_setup").useCursor { c ->
            Assert.assertTrue(c.moveToFirst())
            Assert.assertEquals(1, c.getInt(0))
            // The server with the most kids
            Assert.assertEquals(SERVER, c.getString(1))
            // The URL is in the app database, which a migration of this one can't read
            Assert.assertEquals("", c.getString(2))
            Assert.assertEquals("DONE", c.getString(3))
            Assert.assertTrue(c.isNull(4))
            Assert.assertFalse(c.isNull(5))
            Assert.assertFalse(c.moveToNext())
        }
        // Existing data is kept
        db.query("SELECT COUNT(*) FROM kid_profiles").useCursor { c ->
            c.moveToFirst()
            Assert.assertEquals(3, c.getInt(0))
        }
        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate1To2WithoutAStoredServer() {
        helper.createDatabase(testDbName, 1).close()

        val db = helper.runMigrationsAndValidate(testDbName, 2, true, KidsDatabase.MIGRATION_1_2)
        db.query("SELECT id FROM tv_setup").useCursor { c ->
            Assert.assertFalse(c.moveToFirst())
        }
        db.close()
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.insertKid(
        userId: Int,
        serverId: String,
    ) {
        execSQL(
            "INSERT INTO kid_profiles (userId, serverId, searchAllowed, autoplayEnabled, autoplayCap, pickedItemIds, sections, " +
                "revision) VALUES ($userId, '$serverId', 0, 0, 3, '[]', '[]', 1)",
        )
    }

    companion object {
        // UUIDs as the app's converters store them, without dashes
        private const val SERVER = "eca8209203cc45ffa09cc0e132f3cf16"
        private const val OTHER_SERVER = "0123456789abcdef0123456789abcdef"
    }
}
