package com.wangjingfei.hometv.data

import androidx.room.Entity
import androidx.room.Index

@Entity(tableName = "channels", primaryKeys = ["id"])
data class ChannelEntity(
    val id: String,
    val name: String,
    val streamUrl: String,
    val groupName: String,
    val channelNumber: Int,
    val favorite: Boolean = false,
    val lastWatchedAt: Long? = null,
)

@Entity(
    tableName = "programmes",
    primaryKeys = ["channelId", "startAt"],
    indices = [Index(value = ["channelId", "stopAt"])],
)
data class ProgrammeEntity(
    val channelId: String,
    val startAt: Long,
    val stopAt: Long,
    val title: String,
    val description: String,
)

fun ChannelEntity.toModel() = Channel(id, name, streamUrl, groupName, channelNumber, favorite, lastWatchedAt)
fun Channel.toEntity(previous: ChannelEntity? = null) = ChannelEntity(
    id = id,
    name = name,
    streamUrl = streamUrl,
    groupName = group,
    channelNumber = number,
    favorite = previous?.favorite ?: favorite,
    lastWatchedAt = previous?.lastWatchedAt ?: lastWatchedAt,
)
fun ProgrammeEntity.toModel() = Programme(channelId, startAt, stopAt, title, description)
fun Programme.toEntity() = ProgrammeEntity(channelId, startAt, stopAt, title, description)
