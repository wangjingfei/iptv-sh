package com.wangjingfei.hometv.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ChannelEntity::class, ProgrammeEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun iptvDao(): IptvDao
}
