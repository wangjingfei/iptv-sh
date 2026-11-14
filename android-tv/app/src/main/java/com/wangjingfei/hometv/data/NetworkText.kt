package com.wangjingfei.hometv.data

import java.net.URI

private val urlPattern = Regex("https?://[^\\s，；。）（]+", RegexOption.IGNORE_CASE)

fun networkOrigin(url: String): String? = runCatching {
    val uri = URI(url)
    require((uri.scheme == "http" || uri.scheme == "https") && !uri.host.isNullOrBlank())
    val defaultPort = (uri.scheme == "http" && uri.port == 80) || (uri.scheme == "https" && uri.port == 443)
    URI(uri.scheme, null, uri.host, if (defaultPort) -1 else uri.port, null, null, null).toString()
}.getOrNull()

fun redactNetworkText(text: String?): String? = text?.let { value ->
    urlPattern.replace(value) { match ->
        val raw = match.value
        val punctuation = raw.takeLastWhile { it in ".,:;!?" }
        val url = raw.dropLast(punctuation.length)
        val redacted = runCatching {
            val uri = URI(url)
            URI(uri.scheme, uri.userInfo?.let { "***" }, uri.host, uri.port, uri.path, null, null).toString()
        }.getOrDefault(url.substringBefore('?').substringBefore('#'))
        redacted + punctuation
    }
}
