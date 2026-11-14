package com.wangjingfei.hometv.data

import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.Request

data class DownloadedText(
    val body: String?,
    val metadata: HttpMetadata,
)

@Singleton
class SourceHttpClient @Inject constructor(private val client: OkHttpClient) {
    fun download(url: String, metadata: HttpMetadata = HttpMetadata()): DownloadedText {
        val request = Request.Builder().url(url).get().apply {
            metadata.etag?.let { header("If-None-Match", it) }
            metadata.lastModified?.let { header("If-Modified-Since", it) }
        }.build()
        return client.newCall(request).execute().use { response ->
            if (response.code == 304) return@use DownloadedText(null, metadata)
            val origin = networkOrigin(url) ?: "来源服务"
            if (!response.isSuccessful) throw IOException("$origin 返回 HTTP ${response.code}")
            val body = response.body?.string()?.ifBlank { throw IOException("$origin 返回空内容") }
                ?: throw IOException("$origin 没有响应体")
            DownloadedText(
                body,
                HttpMetadata(
                    etag = response.header("ETag") ?: metadata.etag,
                    lastModified = response.header("Last-Modified") ?: metadata.lastModified,
                ),
            )
        }
    }
}
