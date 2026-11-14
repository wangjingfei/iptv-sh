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
    private val sourceHttpClient: SourceHttpClient,
    private val sourceProbeService: SourceProbeService,
) {
    val channels: Flow<List<Channel>> = dao.observeChannels().map { rows -> rows.map(ChannelEntity::toModel) }
    fun programmes(from: Long, to: Long): Flow<List<Programme>> =
        dao.observeProgrammes(from, to).map { rows -> rows.map(ProgrammeEntity::toModel) }

    suspend fun probe(playlistUrl: String, configuredEpgUrl: String): SourceProbe =
        withContext(Dispatchers.IO) { sourceProbeService.probe(playlistUrl, configuredEpgUrl) }

    suspend fun sync(playlistUrl: String, configuredEpgUrl: String): SyncResult = withContext(Dispatchers.IO) {
        validator.validate(playlistUrl).getOrThrow()
        val previous = dao.getChannels().associateBy { it.id }
        val playlistDownload = sourceHttpClient.download(playlistUrl, settings.playlistMetadata(playlistUrl))
        val playlist = playlistDownload.body?.let { m3uParser.parse(it, playlistUrl) }
        val importWarnings = playlist?.warnings?.toMutableList() ?: mutableListOf()
        val channels = playlist?.let { sourceProbeService.filterChannels(it.channels, importWarnings) }
            ?: previous.values.sortedBy { it.channelNumber }.map(ChannelEntity::toModel)
        require(channels.isNotEmpty()) { "播放列表中没有安全且有效的频道" }
        if (previous.isEmpty()) {
            sourceProbeService.validateSampleHls(channels.first())
        }
        val epgUrl = configuredEpgUrl.ifBlank { playlist?.epgUrl.orEmpty() }
        val now = System.currentTimeMillis()
        if (playlist != null) {
            dao.replaceChannels(channels.map { it.toEntity(previous[it.id]) })
            settings.setPlaylistMetadata(playlistUrl, playlistDownload.metadata)
        }
        val epgResult = if (epgUrl.isNotBlank()) runCatching {
            validator.validate(epgUrl).getOrThrow()
            val epgDownload = sourceHttpClient.download(epgUrl, settings.epgMetadata(epgUrl))
            val programmes = epgDownload.body?.let {
                xmlTvParser.parse(it, now - 6 * 60 * 60_000L, now + 7 * 24 * 60 * 60_000L)
            }
            if (epgDownload.body != null) settings.setEpgMetadata(epgUrl, epgDownload.metadata)
            programmes
        } else Result.success(emptyList())
        val programmes = epgResult.getOrNull()
        if (programmes != null) {
            dao.replaceProgrammes(programmes.map(Programme::toEntity))
        }
        if (epgUrl.isNotBlank() && epgResult.isSuccess) settings.setLastEpgSyncAt(now)
        settings.saveConfig(playlistUrl, epgUrl, complete = true)
        settings.setLastSyncAt(now)
        SyncResult(
            channelCount = channels.size,
            programmeCount = programmes?.size ?: -1,
            completedAt = now,
            playlistUpdated = playlist != null,
            epgUpdated = programmes != null,
            warning = (importWarnings + listOfNotNull(
                epgResult.exceptionOrNull()?.let { "节目单更新失败，已保留原缓存：${it.message}" },
            )).takeIf { it.isNotEmpty() }?.take(3)?.joinToString("；"),
        )
    }

    suspend fun toggleFavorite(channelId: String) = dao.toggleFavorite(channelId)

    suspend fun markWatched(channelId: String) {
        dao.markWatched(channelId, System.currentTimeMillis())
        settings.setLastChannel(channelId)
    }

    suspend fun clearEpg() {
        dao.deleteProgrammes()
        settings.clearEpgMetadata()
        settings.setLastEpgSyncAt(0L)
    }

    suspend fun clearHistory() {
        dao.clearHistory()
        settings.clearLastChannel()
    }

    suspend fun clearFavorites() {
        dao.resetFavorites()
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

}
