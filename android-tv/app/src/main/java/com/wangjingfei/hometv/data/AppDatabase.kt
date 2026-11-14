package com.wangjingfei.hometv.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ChannelEntity::class, ProgrammeEntity::class, FavoriteArchiveEntity::class], version = 3, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun iptvDao(): IptvDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE programmes_new (channelId TEXT NOT NULL, startAt INTEGER NOT NULL, stopAt INTEGER, title TEXT NOT NULL, description TEXT NOT NULL, PRIMARY KEY(channelId, startAt))")
                db.execSQL("INSERT INTO programmes_new (channelId, startAt, stopAt, title, description) SELECT channelId, startAt, stopAt, title, description FROM programmes")
                db.execSQL("DROP TABLE programmes")
                db.execSQL("ALTER TABLE programmes_new RENAME TO programmes")
                db.execSQL("CREATE INDEX index_programmes_channelId_stopAt ON programmes (channelId, stopAt)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS favorite_archive (channelId TEXT NOT NULL, removedAt INTEGER NOT NULL, PRIMARY KEY(channelId))")
            }
        }
    }
}
