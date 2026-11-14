package com.wangjingfei.hometv.data

import java.net.URI
import java.security.MessageDigest
import javax.inject.Inject

class M3uParser @Inject constructor() {
    private val attribute = Regex("([\\w-]+)=\"([^\"]*)\"")

    fun parse(content: String, baseUrl: String? = null): Playlist {
        val lines = content.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        require(lines.firstOrNull()?.startsWith("#EXTM3U") == true) { "不是有效的 M3U 播放列表" }

        val epgUrl = attribute.findAll(lines.first()).associate { it.groupValues[1] to it.groupValues[2] }["x-tvg-url"]
        val channels = mutableListOf<Channel>()
        var pending: String? = null
        lines.drop(1).forEach { line ->
            when {
                line.startsWith("#EXTINF:") -> pending = line
                !line.startsWith("#") && pending != null -> {
                    val info = pending!!
                    val attrs = attribute.findAll(info).associate { it.groupValues[1] to it.groupValues[2] }
                    val name = attrs["tvg-name"].orEmpty().ifBlank { info.substringAfterLast(',').trim() }
                    val streamUrl = runCatching {
                        if (baseUrl.isNullOrBlank()) URI(line).toString() else URI(baseUrl).resolve(line).toString()
                    }.getOrNull()
                    if (name.isNotBlank() && !streamUrl.isNullOrBlank()) {
                        channels += Channel(
                            id = attrs["tvg-id"].orEmpty().ifBlank { stableId(streamUrl) },
                            name = name,
                            streamUrl = streamUrl,
                            group = attrs["group-title"].orEmpty().ifBlank { inferChannelGroup(name) },
                            number = channels.size + 1,
                        )
                    }
                    pending = null
                }
            }
        }
        require(channels.isNotEmpty()) { "播放列表中没有频道" }
        require(channels.map { it.id }.distinct().size == channels.size) { "播放列表包含重复频道 ID" }
        return Playlist(channels, epgUrl)
    }

    private fun stableId(url: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
        return "url-" + digest.take(8).joinToString("") { "%02x".format(it) }
    }
}
