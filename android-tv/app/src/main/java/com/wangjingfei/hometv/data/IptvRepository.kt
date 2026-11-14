package com.wangjingfei.hometv.data

import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

@Singleton
class IptvRepository @Inject constructor(
    private val client: OkHttpClient,
    private val dao: IptvDao,
    private val m3uParser: M3uParser,
    private val xmlTvParser: XmlTvParser,
    private val validator: SourceValidator,
    private val settings: SettingsRepository,
) {
    val channels: Flow<List<Channel>> = dao.observeChannels().map { rows -> rows.map(ChannelEntity::toModel) }
    fun programmes(from: Long, to: Long): Flow<List<Programme>> =
        dao.observeProgrammes(from, to).map { rows -> rows.map(ProgrammeEntity::toModel) }

    suspend fun sync(playlistUrl: String, configuredEpgUrl: String): SyncResult = withContext(Dispatchers.IO) {
        validator.validate(playlistUrl).getOrThrow()
        val playlist = m3uParser.parse(download(playlistUrl), playlistUrl)
        playlist.channels.forEach { validator.validate(it.streamUrl).getOrThrow() }
        val previous = dao.getChannels().associateBy { it.id }
        if (previous.isEmpty()) {
            val sampleHls = download(playlist.channels.first().streamUrl)
            require(sampleHls.lineSequence().firstOrNull()?.trim() == "#EXTM3U") {
                "样例频道没有返回有效的 HLS 播放列表"
            }
        }
        val epgUrl = configuredEpgUrl.ifBlank { playlist.epgUrl.orEmpty() }
        val now = System.currentTimeMillis()
        dao.replaceChannels(playlist.channels.map { it.toEntity(previous[it.id]) })
        val epgResult = if (epgUrl.isNotBlank()) runCatching {
            validator.validate(epgUrl).getOrThrow()
            xmlTvParser.parse(download(epgUrl), now - 6 * 60 * 60_000L, now + 7 * 24 * 60 * 60_000L)
        } else Result.success(emptyList())
        val programmes = epgResult.getOrNull()
        if (programmes != null) dao.replaceProgrammes(programmes.map(Programme::toEntity))
        settings.saveConfig(playlistUrl, epgUrl, complete = true)
        settings.setLastSyncAt(now)
        SyncResult(
            channelCount = playlist.channels.size,
            programmeCount = programmes?.size ?: 0,
            completedAt = now,
            warning = epgResult.exceptionOrNull()?.let { "节目单更新失败，已保留原缓存：${it.message}" },
        )
    }

    suspend fun toggleFavorite(channelId: String) = dao.toggleFavorite(channelId)

    suspend fun markWatched(channelId: String) {
        dao.markWatched(channelId, System.currentTimeMillis())
        settings.setLastChannel(channelId)
    }

    suspend fun checkUrl(name: String, url: String): DiagnosticItem = withContext(Dispatchers.IO) {
        runCatching {
            validator.validate(url).getOrThrow()
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val size = response.body?.contentLength() ?: -1L
                DiagnosticItem(name, DiagnosticStatus.OK, "HTTP ${response.code}${if (size >= 0) "，$size 字节" else ""}")
            }
        }.getOrElse { DiagnosticItem(name, DiagnosticStatus.ERROR, it.message ?: "连接失败") }
    }

    private fun download(url: String): String {
        val request = Request.Builder().url(url).get().build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("$url 返回 HTTP ${response.code}")
            response.body?.string()?.ifBlank { throw IOException("$url 返回空内容") }
                ?: throw IOException("$url 没有响应体")
        }
    }
}
