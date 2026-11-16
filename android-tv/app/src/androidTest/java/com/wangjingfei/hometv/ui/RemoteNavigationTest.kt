@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.wangjingfei.hometv.ui

import android.content.Context
import android.view.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.wangjingfei.hometv.MainActivity
import com.wangjingfei.hometv.data.AppDatabase
import com.wangjingfei.hometv.data.ChannelEntity
import com.wangjingfei.hometv.data.SettingsRepository
import com.wangjingfei.hometv.data.SourceConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule

class RemoteNavigationTest {
    private val composeRule = createAndroidComposeRule<MainActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val rules: TestRule = RuleChain
        .outerRule(SeedConfiguredHomeRule())
        .around(composeRule)

    @Test
    fun configuredHomeSupportsPlayerAndSettingsRemotePaths() {
        composeRule.waitUntilAtLeastOneExists(hasTestTag("home-current-channel"), 5_000)
        waitUntilFocused("home-current-channel")

        pressNative(KeyEvent.KEYCODE_9)
        composeRule.waitUntilAtLeastOneExists(hasTestTag("channel-number-overlay"), 3_000)
        pressNative(KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_1)
        composeRule.waitUntilDoesNotExist(hasTestTag("channel-number-overlay"), 3_000)
        waitUntilFocused("home-current-channel")

        press("home-current-channel", Key.Enter)
        composeRule.waitUntilAtLeastOneExists(hasTestTag("player-overlay"), 5_000)
        waitUntilFocused("player-current-channel")

        pressBack()
        composeRule.waitUntilDoesNotExist(hasTestTag("player-overlay"), 5_000)
        waitUntilFocused("player-root")

        pressBack()
        composeRule.waitUntilAtLeastOneExists(hasTestTag("home-current-channel"), 5_000)
        waitUntilFocused("home-current-channel")

        press("home-current-channel", Key.DirectionUp)
        waitUntilFocused("home-selected-group")
        press("home-selected-group", Key.DirectionUp)
        waitUntilFocused("home-settings")
        press("home-settings", Key.Enter)

        composeRule.onNodeWithTag("settings-root").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-update").assertIsDisplayed()
        waitUntilFocused("settings-back")
        press("settings-back", Key.DirectionDown)
        waitUntilFocused("settings-playlist")
        press("settings-playlist", Key.DirectionDown)
        waitUntilFocused("settings-epg")
        press("settings-epg", Key.DirectionDown)
        waitUntilFocused("settings-test")
        press("settings-test", Key.DirectionRight)
        waitUntilFocused("settings-diagnostics")
        press("settings-diagnostics", Key.DirectionRight)
        waitUntilFocused("settings-phone")
        press("settings-phone", Key.DirectionRight)
        waitUntilFocused("settings-update")
        press("settings-update", Key.DirectionLeft)
        waitUntilFocused("settings-phone")
        press("settings-phone", Key.DirectionLeft)
        waitUntilFocused("settings-diagnostics")
        press("settings-diagnostics", Key.DirectionLeft)
        waitUntilFocused("settings-test")
        press("settings-test", Key.DirectionUp)
        waitUntilFocused("settings-epg")
        press("settings-epg", Key.DirectionUp)
        waitUntilFocused("settings-playlist")
        press("settings-playlist", Key.DirectionUp)
        waitUntilFocused("settings-back")
        press("settings-back", Key.Enter)

        composeRule.waitUntilAtLeastOneExists(hasTestTag("home-current-channel"), 5_000)
        waitUntilFocused("home-current-channel")
    }

    private fun press(tag: String, key: Key) {
        composeRule.onNodeWithTag(tag).performKeyInput {
            keyDown(key)
            keyUp(key)
        }
        composeRule.waitForIdle()
    }

    private fun pressBack() {
        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
    }

    private fun pressNative(vararg keyCodes: Int) {
        keyCodes.forEach(instrumentation::sendKeyDownUpSync)
        composeRule.waitForIdle()
    }

    private fun waitUntilFocused(tag: String) {
        composeRule.waitUntil(5_000) {
            runCatching { composeRule.onNodeWithTag(tag).assertIsFocused() }.isSuccess
        }
    }

    private class SeedConfiguredHomeRule : ExternalResource() {
        private val context = ApplicationProvider.getApplicationContext<Context>()
        private val settings = SettingsRepository(context)
        private lateinit var originalConfig: SourceConfig
        private var originalLastChannelId: String? = null
        private var originalLastSyncAt: Long = 0L
        private var originalTestChannels: List<ChannelEntity> = emptyList()

        override fun before() = runBlocking {
            originalConfig = settings.config.first()
            originalLastChannelId = settings.lastChannelId.first()
            originalLastSyncAt = settings.lastSyncAt.first()
            withDatabase { database ->
                originalTestChannels = database.iptvDao().getChannels().filter { it.id in TEST_CHANNEL_IDS }
                database.iptvDao().upsertChannels(
                    listOf(
                        ChannelEntity(
                            id = TEST_CHANNEL_900_ID,
                            name = "遥控器测试频道 900",
                            streamUrl = "http://127.0.0.1:65534/unavailable/index.m3u8",
                            groupName = "测试",
                            channelNumber = 900,
                        ),
                        ChannelEntity(
                            id = TEST_CHANNEL_901_ID,
                            name = "遥控器测试频道 901",
                            streamUrl = "http://127.0.0.1:65534/unavailable-901/index.m3u8",
                            groupName = "测试",
                            channelNumber = 901,
                        ),
                    ),
                )
            }
            settings.saveConfig(originalConfig.playlistUrl, originalConfig.epgUrl, complete = true)
            settings.setLastChannel(TEST_CHANNEL_900_ID)
            settings.setLastSyncAt(System.currentTimeMillis())
        }

        override fun after() = runBlocking {
            withDatabase { database ->
                database.openHelper.writableDatabase.execSQL(
                    "DELETE FROM channels WHERE id IN (?, ?)",
                    arrayOf(TEST_CHANNEL_900_ID, TEST_CHANNEL_901_ID),
                )
                if (originalTestChannels.isNotEmpty()) database.iptvDao().upsertChannels(originalTestChannels)
            }
            settings.saveConfig(originalConfig.playlistUrl, originalConfig.epgUrl, originalConfig.setupComplete)
            originalLastChannelId?.let { settings.setLastChannel(it) } ?: settings.clearLastChannel()
            settings.setLastSyncAt(originalLastSyncAt)
        }

        private suspend fun withDatabase(block: suspend (AppDatabase) -> Unit) {
            val database = Room.databaseBuilder(context, AppDatabase::class.java, "home-tv.db")
                .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
                .build()
            try {
                block(database)
            } finally {
                database.close()
            }
        }
    }

    private companion object {
        const val TEST_CHANNEL_900_ID = "codex-remote-navigation-test-900"
        const val TEST_CHANNEL_901_ID = "codex-remote-navigation-test-901"
        val TEST_CHANNEL_IDS = setOf(TEST_CHANNEL_900_ID, TEST_CHANNEL_901_ID)
    }
}
