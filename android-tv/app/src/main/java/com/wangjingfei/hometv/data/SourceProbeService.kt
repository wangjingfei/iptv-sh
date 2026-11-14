package com.wangjingfei.hometv.data

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SourceProbeService @Inject constructor(
    private val sourceHttpClient: SourceHttpClient,
    private val m3uParser: M3uParser,
    private val xmlTvParser: XmlTvParser,
    private val validator: SourceValidator,
) {
    fun probe(playlistUrl: String, configuredEpgUrl: String): SourceProbe {
        val normalizedPlaylistUrl = playlistUrl.trim()
        val requestedEpgUrl = configuredEpgUrl.trim()
        validator.validate(normalizedPlaylistUrl).getOrThrow()
        val playlist = m3uParser.parse(sourceHttpClient.download(normalizedPlaylistUrl).body.orEmpty(), normalizedPlaylistUrl)
        val warnings = playlist.warnings.toMutableList()
        val channels = filterChannels(playlist.channels, warnings)
        require(channels.isNotEmpty()) { "播放列表中没有安全且有效的频道" }
        validateSampleHls(channels.first())

        val resolvedEpgUrl = requestedEpgUrl.ifBlank { playlist.epgUrl.orEmpty() }
        val programmeResult = if (resolvedEpgUrl.isBlank()) {
            Result.success(null)
        } else runCatching {
            validator.validate(resolvedEpgUrl).getOrThrow()
            val now = System.currentTimeMillis()
            xmlTvParser.parse(
                sourceHttpClient.download(resolvedEpgUrl).body.orEmpty(),
                now - 6 * 60 * 60_000L,
                now + 7 * 24 * 60 * 60_000L,
            ).size
        }
        programmeResult.exceptionOrNull()?.let { warnings += "节目单测试失败，仍可只使用直播：${it.message}" }
        return SourceProbe(
            playlistUrl = normalizedPlaylistUrl,
            requestedEpgUrl = requestedEpgUrl,
            resolvedEpgUrl = resolvedEpgUrl,
            channelCount = channels.size,
            programmeCount = programmeResult.getOrNull(),
            warning = warnings.takeIf { it.isNotEmpty() }?.take(3)?.joinToString("；"),
        )
    }

    internal fun filterChannels(channels: List<Channel>, warnings: MutableList<String>): List<Channel> =
        channels.filter { channel ->
            validator.validate(channel.streamUrl).fold(
                onSuccess = { true },
                onFailure = {
                    warnings += "频道 ${channel.name} 的流地址不安全或无效，已跳过：${it.message}"
                    false
                },
            )
        }

    internal fun validateSampleHls(channel: Channel) {
        val sampleHls = sourceHttpClient.download(channel.streamUrl).body.orEmpty()
        require(sampleHls.lineSequence().firstOrNull()?.trim() == "#EXTM3U") {
            "样例频道没有返回有效的 HLS 播放列表"
        }
    }
}
