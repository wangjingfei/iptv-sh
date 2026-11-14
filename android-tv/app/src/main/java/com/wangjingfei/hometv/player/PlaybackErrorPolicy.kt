package com.wangjingfei.hometv.player

import com.wangjingfei.hometv.data.redactNetworkText

data class PlaybackFailure(
    val conclusion: String,
    val detail: String,
    val retryDelaysMillis: List<Long>,
)

object PlaybackErrorPolicy {
    private val defaultRetryDelays = listOf(1_000L, 2_000L, 4_000L)

    fun classify(errorCodeName: String, httpStatus: Int?, message: String?): PlaybackFailure {
        val decoderFailure = errorCodeName.contains("DECOD", ignoreCase = true)
        val conclusion = when {
            httpStatus == 404 -> "频道配置已失效"
            httpStatus == 502 || httpStatus == 503 -> "NAS 正在启动频道或媒体服务异常"
            decoderFailure -> "电视无法解码该频道，兼容解码器回退也已失败"
            errorCodeName.contains("NETWORK", ignoreCase = true) ||
                errorCodeName.contains("TIMEOUT", ignoreCase = true) -> "电视无法连接直播服务"
            else -> "播放失败"
        }
        val retryDelays = when {
            httpStatus == 404 || decoderFailure -> emptyList()
            else -> defaultRetryDelays
        }
        return PlaybackFailure(
            conclusion = conclusion,
            detail = "$conclusion（$errorCodeName）：${redactNetworkText(message) ?: "无更多信息"}",
            retryDelaysMillis = retryDelays,
        )
    }
}
