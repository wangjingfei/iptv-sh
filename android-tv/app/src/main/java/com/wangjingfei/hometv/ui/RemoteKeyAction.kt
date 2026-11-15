package com.wangjingfei.hometv.ui

import android.view.KeyEvent

internal enum class PlayerRemoteAction {
    PREVIOUS_CHANNEL,
    NEXT_CHANNEL,
    SHOW_CONTROLS,
    HIDE_CONTROLS,
    TOGGLE_PLAYBACK,
    PLAY,
    PAUSE,
}

internal fun digitFromKeyCode(keyCode: Int): Int? = when (keyCode) {
    in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> keyCode - KeyEvent.KEYCODE_0
    in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> keyCode - KeyEvent.KEYCODE_NUMPAD_0
    else -> null
}

internal fun playerRemoteAction(keyCode: Int, controlsVisible: Boolean): PlayerRemoteAction? {
    when (keyCode) {
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> return PlayerRemoteAction.TOGGLE_PLAYBACK
        KeyEvent.KEYCODE_MEDIA_PLAY -> return PlayerRemoteAction.PLAY
        KeyEvent.KEYCODE_MEDIA_PAUSE -> return PlayerRemoteAction.PAUSE
        KeyEvent.KEYCODE_CHANNEL_UP -> return PlayerRemoteAction.PREVIOUS_CHANNEL
        KeyEvent.KEYCODE_CHANNEL_DOWN -> return PlayerRemoteAction.NEXT_CHANNEL
    }
    if (controlsVisible) {
        return if (keyCode == KeyEvent.KEYCODE_MENU) PlayerRemoteAction.HIDE_CONTROLS else null
    }
    return when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> PlayerRemoteAction.PREVIOUS_CHANNEL
        KeyEvent.KEYCODE_DPAD_DOWN -> PlayerRemoteAction.NEXT_CHANNEL
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_MENU,
        -> PlayerRemoteAction.SHOW_CONTROLS
        else -> null
    }
}
