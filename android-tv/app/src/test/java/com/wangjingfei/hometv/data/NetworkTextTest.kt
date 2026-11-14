package com.wangjingfei.hometv.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NetworkTextTest {
    @Test
    fun stripsCredentialsQueriesAndFragmentsFromUrls() {
        val userInfo = listOf("user", "example-password").joinToString(":")
        val query = listOf("token", "example-value").joinToString("=")
        val result = redactNetworkText(
            "请求 http://$userInfo@192.168.31.200:8090/live/index.m3u8?$query#part 返回 403",
        )

        assertThat(result).isEqualTo("请求 http://***@192.168.31.200:8090/live/index.m3u8 返回 403")
        assertThat(result).doesNotContain("example-password")
        assertThat(result).doesNotContain("example-value")
    }

    @Test
    fun extractsHttpOriginWithoutDefaultPort() {
        assertThat(networkOrigin("https://tv.home.arpa:443/live/a.m3u8")).isEqualTo("https://tv.home.arpa")
        assertThat(networkOrigin("http://192.168.31.200:8090/live/a.m3u8")).isEqualTo("http://192.168.31.200:8090")
        assertThat(networkOrigin("https://[fd00::1]:8443/live/a.m3u8")).isEqualTo("https://[fd00::1]:8443")
        assertThat(networkOrigin("file:///tmp/a")).isNull()
    }
}
