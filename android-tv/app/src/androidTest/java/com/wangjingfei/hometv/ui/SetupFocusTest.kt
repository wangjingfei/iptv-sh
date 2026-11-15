@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.wangjingfei.hometv.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.core.app.ApplicationProvider
import com.wangjingfei.hometv.MainActivity
import com.wangjingfei.hometv.data.SettingsRepository
import com.wangjingfei.hometv.data.SourceConfig
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class SetupFocusTest {
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: TestRule = RuleChain
        .outerRule(ResetSetupRule())
        .around(composeRule)

    @Test
    fun setupScreenSupportsDpadFocusPath() {
        composeRule.onNodeWithText("家庭电视").assertIsDisplayed()
        composeRule.onNodeWithText("默认地址已填写，先选择“测试连接”，确认来源后保存并进入直播。").assertIsDisplayed()
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

    @Test
    fun setupScreenCanOpenPhoneQrSession() {
        composeRule.onNodeWithTag("setup-playlist").performKeyInput {
            pressKey(Key.DirectionDown)
        }
        composeRule.onNodeWithTag("setup-epg").performKeyInput {
            pressKey(Key.DirectionDown)
        }
        composeRule.onNodeWithTag("setup-test").performKeyInput {
            pressKey(Key.DirectionDown)
        }
        composeRule.onNodeWithTag("setup-phone").assertIsFocused().performKeyInput {
            pressKey(Key.Enter)
        }
        composeRule.waitUntilAtLeastOneExists(androidx.compose.ui.test.hasTestTag("remote-setup-qr"), 5_000)
        composeRule.onNodeWithTag("remote-setup-qr").assertIsDisplayed()
        composeRule.onNodeWithText("等待手机提交").assertIsDisplayed()
    }

    private class ResetSetupRule : ExternalResource() {
        private val settings = SettingsRepository(ApplicationProvider.getApplicationContext())
        private lateinit var originalConfig: SourceConfig

        override fun before() = runBlocking {
            originalConfig = settings.config.first()
            settings.saveConfig(originalConfig.playlistUrl, originalConfig.epgUrl, complete = false)
        }

        override fun after() = runBlocking {
            settings.saveConfig(originalConfig.playlistUrl, originalConfig.epgUrl, originalConfig.setupComplete)
        }
    }
}
