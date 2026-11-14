package com.wangjingfei.hometv.data

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class M3uParserTest {
    private val parser = M3uParser()

    @Test
    fun parsesCurrentNasPlaylistFormat() {
        val result = parser.parse(
            """
            #EXTM3U x-tvg-url="http://192.168.31.200:8091/epg.xml"
            #EXTINF:-1 tvg-id="shanghai-news" tvg-name="上海新闻综合",上海新闻综合
            http://192.168.31.200:8090/hls/shanghai-news/index.m3u8
            #EXTINF:-1 tvg-id="cctv5" tvg-name="CCTV5",CCTV5
            http://192.168.31.200:8090/hls/cctv5/index.m3u8
            """.trimIndent(),
        )

        assertThat(result.epgUrl).isEqualTo("http://192.168.31.200:8091/epg.xml")
        assertThat(result.channels).hasSize(2)
        assertThat(result.channels[0].group).isEqualTo("上海")
        assertThat(result.channels[1].group).isEqualTo("央视")
        assertThat(result.channels[1].number).isEqualTo(2)
    }

    @Test
    fun rejectsDuplicateChannelIds() {
        val content = """
            #EXTM3U
            #EXTINF:-1 tvg-id="same",频道一
            http://192.168.31.200/1.m3u8
            #EXTINF:-1 tvg-id="same",频道二
            http://192.168.31.200/2.m3u8
        """.trimIndent()
        assertThrows(IllegalArgumentException::class.java) { parser.parse(content) }
    }

    @Test
    fun resolvesRelativeUrlAndCreatesStableId() {
        val content = """
            #EXTM3U
            #EXTINF:-1 tvg-name="测试频道",备用名称
            ../hls/test/index.m3u8
        """.trimIndent()
        val first = parser.parse(content, "http://192.168.31.200:8091/lists/playlist.m3u").channels.single()
        val second = parser.parse(content, "http://192.168.31.200:8091/lists/playlist.m3u").channels.single()

        assertThat(first.name).isEqualTo("测试频道")
        assertThat(first.streamUrl).isEqualTo("http://192.168.31.200:8091/hls/test/index.m3u8")
        assertThat(first.id).isEqualTo(second.id)
        assertThat(first.id).startsWith("url-")
    }
}
