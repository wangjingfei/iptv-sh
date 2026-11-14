package com.wangjingfei.hometv.data

import java.net.URI
import javax.inject.Inject

class SourceValidator @Inject constructor() {
    fun validate(url: String): Result<URI> = runCatching {
        val uri = URI(url.trim())
        require(uri.scheme == "https" || uri.scheme == "http") { "仅支持 HTTP 或 HTTPS 地址" }
        require(!uri.host.isNullOrBlank()) { "地址缺少主机名" }
        if (uri.scheme == "http") {
            require(isPrivateHost(uri.host)) { "明文 HTTP 仅允许局域网地址" }
        }
        uri
    }

    private fun isPrivateHost(host: String): Boolean {
        if (host.endsWith(".home.arpa") || host.endsWith(".local") || host == "localhost") return true
        val parts = host.split('.').mapNotNull(String::toIntOrNull)
        if (parts.size != 4 || parts.any { it !in 0..255 }) return false
        return parts[0] == 10 || parts[0] == 127 ||
            (parts[0] == 192 && parts[1] == 168) ||
            (parts[0] == 172 && parts[1] in 16..31)
    }
}
