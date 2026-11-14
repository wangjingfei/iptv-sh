package com.wangjingfei.hometv.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SourceValidatorTest {
    private val validator = SourceValidator()

    @Test
    fun acceptsLanHttpAndAnyHttps() {
        assertThat(validator.validate("http://192.168.31.200:8091/playlist.m3u").isSuccess).isTrue()
        assertThat(validator.validate("http://iptv.home.arpa/playlist.m3u").isSuccess).isTrue()
        assertThat(validator.validate("https://example.com/playlist.m3u").isSuccess).isTrue()
    }

    @Test
    fun rejectsPublicCleartextAndUnknownSchemes() {
        assertThat(validator.validate("http://example.com/playlist.m3u").isFailure).isTrue()
        assertThat(validator.validate("file:///tmp/playlist.m3u").isFailure).isTrue()
    }

    @Test
    fun rejectsPublicCleartextStreamFromPlaylist() {
        val playlist = M3uParser().parse(
            """
            #EXTM3U
            #EXTINF:-1 tvg-id="unsafe",不安全频道
            http://example.com/live/index.m3u8
            """.trimIndent(),
        )
        assertThat(validator.validate(playlist.channels.single().streamUrl).isFailure).isTrue()
    }
}
