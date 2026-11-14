package com.wangjingfei.hometv.data

import com.google.common.truth.Truth.assertThat
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Test

class SourceProbeServiceTest {
    @Test
    fun probesPlaylistDiscoveredEpgAndSampleHlsWithoutWritingState() {
        MockWebServer().use { server ->
            server.dispatcher = sourceDispatcher(server)
            val service = service()

            val result = service.probe(server.url("/playlist.m3u").toString(), "")

            assertThat(result.channelCount).isEqualTo(1)
            assertThat(result.programmeCount).isEqualTo(1)
            assertThat(result.resolvedEpgUrl).isEqualTo(server.url("/epg.xml").toString())
            assertThat(result.warning).isNull()
        }
    }

    @Test
    fun allowsLiveTvWhenOptionalEpgFails() {
        MockWebServer().use { server ->
            server.dispatcher = sourceDispatcher(server, epgStatus = 503)

            val result = service().probe(server.url("/playlist.m3u").toString(), "")

            assertThat(result.channelCount).isEqualTo(1)
            assertThat(result.programmeCount).isNull()
            assertThat(result.warning).contains("仍可只使用直播")
        }
    }

    private fun service() = SourceProbeService(
        SourceHttpClient(OkHttpClient()),
        M3uParser(),
        XmlTvParser(),
        SourceValidator(),
    )

    private fun sourceDispatcher(server: MockWebServer, epgStatus: Int = 200) = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
            "/playlist.m3u" -> MockResponse().setBody(
                "#EXTM3U x-tvg-url=\"${server.url("/epg.xml")}\"\n" +
                    "#EXTINF:-1 tvg-id=\"news\" tvg-name=\"新闻\",新闻\n" +
                    "${server.url("/live/index.m3u8")}\n",
            )
            "/live/index.m3u8" -> MockResponse().setBody("#EXTM3U\n#EXT-X-VERSION:3\n")
            "/epg.xml" -> if (epgStatus == 200) MockResponse().setBody(xmlTv()) else MockResponse().setResponseCode(epgStatus)
            else -> MockResponse().setResponseCode(404)
        }
    }

    private fun xmlTv(): String {
        val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z")
        val now = OffsetDateTime.now(ZoneOffset.ofHours(8))
        return """
            <tv>
              <programme channel="news" start="${now.minusMinutes(10).format(formatter)}" stop="${now.plusMinutes(50).format(formatter)}">
                <title>新闻直播</title>
              </programme>
            </tv>
        """.trimIndent()
    }
}
