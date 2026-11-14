@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.wangjingfei.hometv.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import com.wangjingfei.hometv.MainActivity
import org.junit.Rule
import org.junit.Test

class SetupFocusTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun setupScreenSupportsDpadFocusPath() {
        composeRule.onNodeWithText("家庭电视").assertIsDisplayed()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("setup-playlist").assertIsFocused()

        composeRule.onNodeWithTag("setup-playlist").performKeyInput {
            keyDown(Key.DirectionDown)
            keyUp(Key.DirectionDown)
        }
        composeRule.onNodeWithTag("setup-epg").assertIsFocused()

        composeRule.onNodeWithTag("setup-epg").performKeyInput {
            keyDown(Key.DirectionDown)
            keyUp(Key.DirectionDown)
        }
        composeRule.onNodeWithTag("setup-test").assertIsFocused()
    }
}
