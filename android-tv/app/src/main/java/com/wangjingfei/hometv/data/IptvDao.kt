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

    @Query("SELECT * FROM programmes WHERE (stopAt IS NULL OR stopAt >= :from) AND startAt <= :to ORDER BY startAt")
    fun observeProgrammes(from: Long, to: Long): Flow<List<ProgrammeEntity>>

    @Query("SELECT * FROM channels")
    suspend fun getChannels(): List<ChannelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChannels(channels: List<ChannelEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgrammes(programmes: List<ProgrammeEntity>)

    @Query("DELETE FROM channels WHERE id NOT IN (:ids)")
    suspend fun deleteMissingChannels(ids: List<String>)

    @Query("INSERT OR REPLACE INTO favorite_archive (channelId, removedAt) SELECT id, :removedAt FROM channels WHERE favorite = 1 AND id NOT IN (:ids)")
    suspend fun archiveMissingFavorites(ids: List<String>, removedAt: Long)

    @Query("UPDATE channels SET favorite = 1 WHERE id IN (SELECT channelId FROM favorite_archive WHERE removedAt >= :cutoff)")
    suspend fun restoreArchivedFavorites(cutoff: Long)

    @Query("DELETE FROM favorite_archive WHERE removedAt < :cutoff")
    suspend fun purgeArchivedFavorites(cutoff: Long)

    @Query("DELETE FROM favorite_archive")
    suspend fun clearFavoriteArchive()

    @Query("DELETE FROM programmes")
    suspend fun deleteProgrammes()

    @Query("UPDATE channels SET favorite = NOT favorite WHERE id = :channelId")
    suspend fun toggleFavorite(channelId: String)

    @Query("UPDATE channels SET lastWatchedAt = :watchedAt WHERE id = :channelId")
    suspend fun markWatched(channelId: String, watchedAt: Long)

    @Query("UPDATE channels SET favorite = 0")
    suspend fun clearFavorites()

    @Transaction
    suspend fun resetFavorites() {
        clearFavorites()
        clearFavoriteArchive()
    }

    @Query("UPDATE channels SET lastWatchedAt = NULL")
    suspend fun clearHistory()

    @Transaction
    suspend fun replaceGuide(channels: List<ChannelEntity>, programmes: List<ProgrammeEntity>) {
        replaceChannels(channels)
        replaceProgrammes(programmes)
    }

    @Transaction
    suspend fun replaceChannels(channels: List<ChannelEntity>) {
        val now = System.currentTimeMillis()
        val cutoff = now - 30L * 24 * 60 * 60_000
        if (channels.isNotEmpty()) archiveMissingFavorites(channels.map { it.id }, now)
        upsertChannels(channels)
        if (channels.isNotEmpty()) {
            restoreArchivedFavorites(cutoff)
            deleteMissingChannels(channels.map { it.id })
        }
        purgeArchivedFavorites(cutoff)
    }

    @Transaction
    suspend fun replaceProgrammes(programmes: List<ProgrammeEntity>) {
        deleteProgrammes()
        if (programmes.isNotEmpty()) upsertProgrammes(programmes)
    }
}
