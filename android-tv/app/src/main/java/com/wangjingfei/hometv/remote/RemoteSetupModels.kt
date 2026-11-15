package com.wangjingfei.hometv.remote

import java.net.Inet4Address

enum class RemoteSetupPhase {
    WAITING,
    TESTING,
    SUCCESS,
    ERROR,
}

data class RemoteSetupSession(
    val id: String,
    val url: String,
    val expiresAt: Long,
    val phase: RemoteSetupPhase = RemoteSetupPhase.WAITING,
    val message: String = "等待手机提交",
    val playlistUrl: String,
    val epgUrl: String,
    val submissionVersion: Long = 0L,
)

data class RemoteSourceSubmission(
    val sessionId: String,
    val playlistUrl: String,
    val epgUrl: String,
)

data class LanEndpoint(
    val address: Inet4Address,
    val prefixLength: Int,
)

internal fun isSameSubnet(local: Inet4Address, remote: Inet4Address, prefixLength: Int): Boolean {
    require(prefixLength in 0..32)
    if (prefixLength == 0) return true
    val mask = -1 shl (32 - prefixLength)
    return local.toInt() and mask == remote.toInt() and mask
}

private fun Inet4Address.toInt(): Int = address.fold(0) { value, byte -> (value shl 8) or (byte.toInt() and 0xff) }
