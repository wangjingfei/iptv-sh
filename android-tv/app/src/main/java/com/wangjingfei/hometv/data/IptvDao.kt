package com.wangjingfei.hometv.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface IptvDao {
    @Query("SELECT * FROM channels ORDER BY channelNumber")
    fun observeChannels(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM programmes WHERE stopAt >= :from AND startAt <= :to ORDER BY startAt")
    fun observeProgrammes(from: Long, to: Long): Flow<List<ProgrammeEntity>>

    @Query("SELECT * FROM channels")
    suspend fun getChannels(): List<ChannelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChannels(channels: List<ChannelEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgrammes(programmes: List<ProgrammeEntity>)

    @Query("DELETE FROM channels WHERE id NOT IN (:ids)")
    suspend fun deleteMissingChannels(ids: List<String>)

    @Query("DELETE FROM programmes")
    suspend fun deleteProgrammes()

    @Query("UPDATE channels SET favorite = NOT favorite WHERE id = :channelId")
    suspend fun toggleFavorite(channelId: String)

    @Query("UPDATE channels SET lastWatchedAt = :watchedAt WHERE id = :channelId")
    suspend fun markWatched(channelId: String, watchedAt: Long)

    @Transaction
    suspend fun replaceGuide(channels: List<ChannelEntity>, programmes: List<ProgrammeEntity>) {
        replaceChannels(channels)
        replaceProgrammes(programmes)
    }

    @Transaction
    suspend fun replaceChannels(channels: List<ChannelEntity>) {
        upsertChannels(channels)
        if (channels.isNotEmpty()) deleteMissingChannels(channels.map { it.id })
    }

    @Transaction
    suspend fun replaceProgrammes(programmes: List<ProgrammeEntity>) {
        deleteProgrammes()
        if (programmes.isNotEmpty()) upsertProgrammes(programmes)
    }
}
