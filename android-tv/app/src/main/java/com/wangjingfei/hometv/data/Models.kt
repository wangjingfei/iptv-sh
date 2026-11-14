package com.wangjingfei.hometv.data

data class Channel(
    val id: String,
    val name: String,
    val streamUrl: String,
    val group: String,
    val number: Int,
    val favorite: Boolean = false,
    val lastWatchedAt: Long? = null,
)

data class Programme(
    val channelId: String,
    val startAt: Long,
    val stopAt: Long?,
    val title: String,
    val description: String = "",
)

data class Playlist(
    val channels: List<Channel>,
    val epgUrl: String?,
    val warnings: List<String> = emptyList(),
)

data class SourceConfig(
    val playlistUrl: String,
    val epgUrl: String,
    val setupComplete: Boolean,
)

data class SourceProbe(
    val playlistUrl: String,
    val requestedEpgUrl: String,
    val resolvedEpgUrl: String,
    val channelCount: Int,
    val programmeCount: Int?,
    val warning: String? = null,
) {
    fun matches(playlistUrl: String, epgUrl: String): Boolean =
        this.playlistUrl == playlistUrl.trim() && requestedEpgUrl == epgUrl.trim()
}

enum class DiagnosticStatus { CHECKING, OK, WARNING, ERROR }

data class DiagnosticItem(
    val name: String,
    val status: DiagnosticStatus,
    val detail: String,
)

data class SyncResult(
    val channelCount: Int,
    val programmeCount: Int,
    val completedAt: Long,
    val playlistUpdated: Boolean,
    val epgUpdated: Boolean,
    val warning: String? = null,
)

data class HttpMetadata(
    val etag: String? = null,
    val lastModified: String? = null,
)

fun inferChannelGroup(name: String): String = when {
    name.contains("4K", ignoreCase = true) || name.contains("超高清") -> "4K"
    name.contains("CCTV", ignoreCase = true) || name.contains("CETV", ignoreCase = true) || name.contains("央视") -> "央视"
    name.contains("上海") || name.contains("东方") || name.contains("五星") || name.contains("生活时尚") || name.contains("都市剧场") -> "上海"
    name.contains("卫视") -> "卫视"
    name.contains("少儿") || name.contains("卡通") || name.contains("动漫") -> "少儿"
    name.contains("体育") || name.contains("足球") || name.contains("篮球") -> "体育"
    name.contains("电影") || name.contains("影视") || name.contains("剧场") -> "影视"
    else -> "其他"
}
