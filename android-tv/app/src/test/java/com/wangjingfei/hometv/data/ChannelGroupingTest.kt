package com.wangjingfei.hometv.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChannelGroupingTest {
    @Test
    fun assignsDeterministicGroups() {
        assertThat(inferChannelGroup("东方卫视")).isEqualTo("上海")
        assertThat(inferChannelGroup("湖南卫视")).isEqualTo("卫视")
        assertThat(inferChannelGroup("CCTV5+体育赛事")).isEqualTo("央视")
        assertThat(inferChannelGroup("爱上4K")).isEqualTo("4K")
        assertThat(inferChannelGroup("CCTV4K超高清")).isEqualTo("4K")
        assertThat(inferChannelGroup("CETV1")).isEqualTo("央视")
    }
}
