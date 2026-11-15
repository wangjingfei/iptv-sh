package com.wangjingfei.hometv.ui

import androidx.media3.common.Player
import com.google.common.truth.Truth.assertThat
import com.wangjingfei.hometv.data.Channel
import com.wangjingfei.hometv.data.Programme
import com.wangjingfei.hometv.data.SourceProbe
import org.junit.Test

class MainUiStateTest {
    private val channels = listOf(
        Channel("a", "上海新闻", "https://example.test/a.m3u8", "上海", 1, favorite = true),
        Channel("b", "CCTV5", "https://example.test/b.m3u8", "央视", 2, lastWatchedAt = 20),
        Channel("c", "湖南卫视", "https://example.test/c.m3u8", "卫视", 3, lastWatchedAt = 10),
    )

    @Test
    fun filtersFavoritesRecentGroupsAndSearch() {
        assertThat(MainUiState(channels = channels, selectedGroup = "收藏").visibleChannels.map { it.id }).containsExactly("a")
        assertThat(MainUiState(channels = channels, selectedGroup = "最近").visibleChannels.map { it.id }).containsExactly("b", "c").inOrder()
        assertThat(MainUiState(channels = channels, selectedGroup = "央视").visibleChannels.map { it.id }).containsExactly("b")
        assertThat(MainUiState(channels = channels, searchQuery = "湖南").visibleChannels.map { it.id }).containsExactly("c")
        assertThat(MainUiState(channels = channels, searchQuery = "2").visibleChannels.map { it.id }).containsExactly("b")
    }

    @Test
    fun findsCurrentAndNextProgrammes() {
        val state = MainUiState(
            programmes = listOf(
                Programme("a", 100, 200, "当前"),
                Programme("a", 200, 300, "下一档"),
                Programme("b", 100, null, "未知结束时间"),
            ),
        )
        assertThat(state.currentProgramme("a", 150)?.title).isEqualTo("当前")
        assertThat(state.nextProgramme("a", 150)?.title).isEqualTo("下一档")
        assertThat(state.currentProgramme("b", 150)?.title).isEqualTo("未知结束时间")
    }

    @Test
    fun testedSourceOnlyMatchesUnchangedInput() {
        val probe = SourceProbe("http://192.168.1.2/list.m3u", "", "http://192.168.1.2/epg.xml", 10, 20)

        assertThat(probe.matches(" http://192.168.1.2/list.m3u ", "")).isTrue()
        assertThat(probe.matches("http://192.168.1.3/list.m3u", "")).isFalse()
        assertThat(probe.matches("http://192.168.1.2/list.m3u", "http://192.168.1.2/epg.xml")).isFalse()
    }

    @Test
    fun keepsPlaybackFailureControlsVisible() {
        assertThat(MainUiState(playbackError = null).shouldAutoHideControls).isTrue()
        assertThat(MainUiState(playbackError = "解码失败").shouldAutoHideControls).isFalse()
    }

    @Test
    fun keepsScreenOnOnlyWhilePlayerIsActuallyPlaying() {
        assertThat(MainUiState(isPlaying = true).shouldKeepScreenOn).isTrue()
        assertThat(MainUiState(playbackState = Player.STATE_READY, isPlaying = false).shouldKeepScreenOn).isFalse()
    }

    @Test
    fun quickChannelsStayUniqueForSmallChannelLists() {
        val channels = (1..3).map { Channel("id-$it", "频道 $it", "http://192.168.1.2/$it.m3u8", "测试", it) }

        assertThat(MainUiState(channels = channels).quickChannels("id-2").map { it.id })
            .containsExactly("id-3", "id-1", "id-2")
            .inOrder()
        assertThat(MainUiState(channels = channels.take(1)).quickChannels("id-1")).containsExactly(channels.first())
        assertThat(MainUiState().quickChannels("missing")).isEmpty()
    }
}
