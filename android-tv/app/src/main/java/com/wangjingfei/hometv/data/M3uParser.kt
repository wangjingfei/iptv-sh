package com.wangjingfei.hometv.data

import java.net.URI
import java.security.MessageDigest
import javax.inject.Inject

class M3uParser @Inject constructor() {
    private val attribute = Regex("([\\w-]+)=\"([^\"]*)\"")

    fun parse(content: String, baseUrl: String? = null): Playlist {
        val lines = content.lineSequence()
            .mapIndexed { index, line -> index + 1 to line.trim() }
            .filter { it.second.isNotEmpty() }
            .toList()
        require(lines.firstOrNull()?.second?.startsWith("#EXTM3U") == true) { "不是有效的 M3U 播放列表" }

        val epgUrl = attribute.findAll(lines.first().second).associate { it.groupValues[1] to it.groupValues[2] }["x-tvg-url"]
        val channels = mutableListOf<Channel>()
        val warnings = mutableListOf<String>()
        val seenIds = mutableSetOf<String>()
        var pending: Pair<Int, String>? = null
        lines.drop(1).forEach { (lineNumber, line) ->
            when {
                line.startsWith("#EXTINF:") -> {
                    pending?.let { warnings += "第 ${it.first} 行频道缺少流地址" }
                    pending = lineNumber to line
                }
                !line.startsWith("#") && pending != null -> {
                    val (infoLine, info) = pending!!
                    val attrs = attribute.findAll(info).associate { it.groupValues[1] to it.groupValues[2] }
                    val name = attrs["tvg-name"].orEmpty().ifBlank { info.substringAfterLast(',').trim() }
                    val streamUrl = runCatching {
                        if (baseUrl.isNullOrBlank()) URI(line).toString() else URI(baseUrl).resolve(line).toString()
                    }.getOrNull()
                    if (name.isBlank()) {
                        warnings += "第 $infoLine 行频道名称为空"
                    } else if (streamUrl.isNullOrBlank()) {
                        warnings += "第 $lineNumber 行流地址无效"
                    } else {
                        val declaredId = attrs["tvg-id"].orEmpty()
                        val id = declaredId.ifBlank { stableId(streamUrl) }
                        if (declaredId.isBlank()) warnings += "第 $infoLine 行缺少 tvg-id，已生成临时 ID $id"
                        if (!seenIds.add(id)) {
                            warnings += "第 $infoLine 行频道 ID $id 重复，已跳过"
                            pending = null
                            return@forEach
                        }
                        channels += Channel(
                            id = id,
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
        pending?.let { warnings += "第 ${it.first} 行频道缺少流地址" }
        require(channels.isNotEmpty()) { "播放列表中没有频道" }
        return Playlist(channels, epgUrl, warnings)
    }

    private fun stableId(url: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
        return "url-" + digest.take(8).joinToString("") { "%02x".format(it) }
    }
}
