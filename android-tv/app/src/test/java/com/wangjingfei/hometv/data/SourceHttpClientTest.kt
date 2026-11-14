package com.wangjingfei.hometv.data

import com.google.common.truth.Truth.assertThat
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test

class SourceHttpClientTest {
    @Test
    fun savesResponseMetadataAndSendsConditionalHeaders() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("ETag", "\"v1\"")
                    .setHeader("Last-Modified", "Sat, 15 Nov 2026 00:00:00 GMT")
                    .setBody("#EXTM3U\n"),
            )
            server.enqueue(MockResponse().setResponseCode(304))
            val client = SourceHttpClient(OkHttpClient())

            val first = client.download(server.url("/playlist.m3u").toString())
            val second = client.download(server.url("/playlist.m3u").toString(), first.metadata)
            val initialRequest = server.takeRequest()
            val conditionalRequest = server.takeRequest()

            assertThat(first.body).isEqualTo("#EXTM3U\n")
            assertThat(first.metadata.etag).isEqualTo("\"v1\"")
            assertThat(second.body).isNull()
            assertThat(initialRequest.getHeader("If-None-Match")).isNull()
            assertThat(conditionalRequest.getHeader("If-None-Match")).isEqualTo("\"v1\"")
            assertThat(conditionalRequest.getHeader("If-Modified-Since")).isEqualTo("Sat, 15 Nov 2026 00:00:00 GMT")
        }
    }
}
