package com.wangjingfei.hometv.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate1To2PreservesProgrammesAndAllowsNullStop() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL("INSERT INTO programmes (channelId, startAt, stopAt, title, description) VALUES ('c', 1, 2, '节目', '')")
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, AppDatabase.MIGRATION_1_2)
        db.query("SELECT channelId, stopAt FROM programmes").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals("c", cursor.getString(0))
            assertFalse(cursor.isNull(1))
        }
        db.close()
    }

    @Test
    fun migrate2To3AddsFavoriteArchive() {
        helper.createDatabase(TEST_DB_V2, 2).close()
        val db = helper.runMigrationsAndValidate(TEST_DB_V2, 3, true, AppDatabase.MIGRATION_2_3)
        db.execSQL("INSERT INTO favorite_archive (channelId, removedAt) VALUES ('c', 1)")
        db.query("SELECT COUNT(*) FROM favorite_archive").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }
        db.close()
    }

    private companion object {
        const val TEST_DB = "migration-test"
        const val TEST_DB_V2 = "migration-test-v2"
    }
}
