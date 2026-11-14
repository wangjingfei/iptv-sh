package com.wangjingfei.hometv.data

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Test

class XmlTvParserTest {
    @Test
    fun parsesChineseProgrammeAndTimezone() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <channel id="cctv1"><display-name>CCTV1</display-name></channel>
              <programme start="20261115120000 +0800" stop="20261115123000 +0800" channel="cctv1">
                <title lang="zh">新闻30分</title><desc lang="zh">午间新闻</desc>
              </programme>
            </tv>
        """.trimIndent()
        val from = Instant.parse("2026-11-15T03:00:00Z").toEpochMilli()
        val to = Instant.parse("2026-11-15T05:00:00Z").toEpochMilli()
        val programmes = XmlTvParser().parse(xml, from, to)

        assertThat(programmes).hasSize(1)
        assertThat(programmes.single().title).isEqualTo("新闻30分")
        assertThat(programmes.single().startAt).isEqualTo(Instant.parse("2026-11-15T04:00:00Z").toEpochMilli())
    }

    @Test
    fun excludesProgrammesOutsideWindow() {
        val xml = """<tv><programme start="20261115120000 +0800" stop="20261115123000 +0800" channel="c"><title>节目</title></programme></tv>"""
        val after = Instant.parse("2026-11-16T00:00:00Z").toEpochMilli()
        assertThat(XmlTvParser().parse(xml, after, after + 60_000)).isEmpty()
    }

    @Test
    fun fillsMissingStopFromNextProgrammeAndKeepsUnknownLastStop() {
        val xml = """
            <tv>
              <programme start="20261115120000 +0800" channel="c"><title>节目一</title></programme>
              <programme start="20261115123000 +0800" channel="c"><title>节目二</title></programme>
            </tv>
        """.trimIndent()
        val from = Instant.parse("2026-11-15T03:00:00Z").toEpochMilli()
        val programmes = XmlTvParser().parse(xml, from, from + 2 * 3_600_000)

        assertThat(programmes).hasSize(2)
        assertThat(programmes[0].stopAt).isEqualTo(programmes[1].startAt)
        assertThat(programmes[1].stopAt).isNull()
    }
}
