package com.wangjingfei.hometv.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlaybackErrorPolicyTest {
    @Test
    fun retriesTransientNetworkAndServerFailures() {
        val network = PlaybackErrorPolicy.classify("ERROR_CODE_IO_NETWORK_CONNECTION_FAILED", null, "断开")
        val server = PlaybackErrorPolicy.classify("ERROR_CODE_IO_BAD_HTTP_STATUS", 503, null)

        assertThat(network.retryDelaysMillis).containsExactly(1_000L, 2_000L, 4_000L).inOrder()
        assertThat(network.conclusion).isEqualTo("电视无法连接直播服务")
        assertThat(server.retryDelaysMillis).hasSize(3)
        assertThat(server.conclusion).contains("NAS")
    }

    @Test
    fun doesNotLoopForMissingChannelOrExhaustedDecoderFallback() {
        val missing = PlaybackErrorPolicy.classify("ERROR_CODE_IO_BAD_HTTP_STATUS", 404, null)
        val decoder = PlaybackErrorPolicy.classify("ERROR_CODE_DECODING_FAILED", null, "unsupported")

        assertThat(missing.retryDelaysMillis).isEmpty()
        assertThat(missing.conclusion).isEqualTo("频道配置已失效")
        assertThat(decoder.retryDelaysMillis).isEmpty()
        assertThat(decoder.conclusion).contains("回退")
    }
}
