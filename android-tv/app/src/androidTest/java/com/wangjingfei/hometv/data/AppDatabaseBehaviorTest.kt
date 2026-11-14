package com.wangjingfei.hometv.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseBehaviorTest {
    @Test
    fun removedFavoriteIsArchivedAndRestoredWhenChannelReturns() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build()
        try {
            val dao = db.iptvDao()
            val retained = Channel("kept", "保留", "http://192.168.31.200/kept.m3u8", "其他", 1, favorite = true).toEntity()
            val removed = Channel("removed", "暂时消失", "http://192.168.31.200/removed.m3u8", "其他", 2, favorite = true).toEntity()
            dao.upsertChannels(listOf(retained, removed))

            dao.replaceChannels(listOf(retained.copy(channelNumber = 1)))

            assertEquals(listOf("kept"), dao.getChannels().map { it.id })
            db.query(SimpleSQLiteQuery("SELECT channelId FROM favorite_archive")).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("removed", cursor.getString(0))
            }

            dao.replaceChannels(listOf(retained, removed.copy(channelNumber = 2)))

            assertEquals(true, dao.getChannels().first { it.id == "removed" }.favorite)
            db.query(SimpleSQLiteQuery("SELECT COUNT(*) FROM favorite_archive")).use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun clearingFavoritesAlsoClearsArchivedFavorites() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build()
        try {
            val dao = db.iptvDao()
            val channel = Channel("removed", "收藏频道", "http://192.168.31.200/removed.m3u8", "其他", 1, favorite = true).toEntity()
            dao.upsertChannels(listOf(channel))
            val replacement = Channel("replacement", "替代频道", "http://192.168.31.200/replacement.m3u8", "其他", 1).toEntity()
            dao.replaceChannels(listOf(replacement))

            dao.resetFavorites()

            assertFalse(dao.getChannels().any { it.favorite })
            db.query(SimpleSQLiteQuery("SELECT COUNT(*) FROM favorite_archive")).use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
        } finally {
            db.close()
        }
    }
}
