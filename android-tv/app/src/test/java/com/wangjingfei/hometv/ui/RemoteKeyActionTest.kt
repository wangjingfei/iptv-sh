package com.wangjingfei.hometv.ui

import android.view.KeyEvent
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RemoteKeyActionTest {
    @Test
    fun resolvesTopRowAndNumpadDigits() {
        assertThat(digitFromKeyCode(KeyEvent.KEYCODE_0)).isEqualTo(0)
        assertThat(digitFromKeyCode(KeyEvent.KEYCODE_9)).isEqualTo(9)
        assertThat(digitFromKeyCode(KeyEvent.KEYCODE_NUMPAD_0)).isEqualTo(0)
        assertThat(digitFromKeyCode(KeyEvent.KEYCODE_NUMPAD_9)).isEqualTo(9)
        assertThat(digitFromKeyCode(KeyEvent.KEYCODE_DPAD_CENTER)).isNull()
    }

    @Test
    fun mediaKeysWorkWithOrWithoutPlayerControls() {
        listOf(false, true).forEach { controlsVisible ->
            assertThat(playerRemoteAction(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, controlsVisible))
                .isEqualTo(PlayerRemoteAction.TOGGLE_PLAYBACK)
            assertThat(playerRemoteAction(KeyEvent.KEYCODE_MEDIA_PLAY, controlsVisible))
                .isEqualTo(PlayerRemoteAction.PLAY)
            assertThat(playerRemoteAction(KeyEvent.KEYCODE_MEDIA_PAUSE, controlsVisible))
                .isEqualTo(PlayerRemoteAction.PAUSE)
        }
    }

    @Test
    fun dpadChangesChannelsOnlyWhenControlsAreHidden() {
        assertThat(playerRemoteAction(KeyEvent.KEYCODE_DPAD_UP, controlsVisible = false))
            .isEqualTo(PlayerRemoteAction.PREVIOUS_CHANNEL)
        assertThat(playerRemoteAction(KeyEvent.KEYCODE_DPAD_DOWN, controlsVisible = false))
            .isEqualTo(PlayerRemoteAction.NEXT_CHANNEL)
        assertThat(playerRemoteAction(KeyEvent.KEYCODE_DPAD_LEFT, controlsVisible = false))
            .isEqualTo(PlayerRemoteAction.SHOW_CONTROLS)
        assertThat(playerRemoteAction(KeyEvent.KEYCODE_DPAD_UP, controlsVisible = true)).isNull()
        assertThat(playerRemoteAction(KeyEvent.KEYCODE_MENU, controlsVisible = true))
            .isEqualTo(PlayerRemoteAction.HIDE_CONTROLS)
    }
}
