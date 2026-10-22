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
 * The kids database's schema history starts at version 1; later versions add their migration tests here
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
}
