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
        composeRule.onNodeWithTag("home-current-channel").assertIsFocused()

        press("home-current-channel", Key.Enter)
        composeRule.waitUntilAtLeastOneExists(hasTestTag("player-overlay"), 5_000)
        composeRule.onNodeWithTag("player-current-channel").assertIsFocused()

        pressBack()
        composeRule.waitUntilDoesNotExist(hasTestTag("player-overlay"), 5_000)
        composeRule.onNodeWithTag("player-root").assertIsFocused()

        pressBack()
        composeRule.waitUntilAtLeastOneExists(hasTestTag("home-current-channel"), 5_000)
        composeRule.onNodeWithTag("home-current-channel").assertIsFocused()

        press("home-current-channel", Key.DirectionUp)
        composeRule.onNodeWithTag("home-selected-group").assertIsFocused()
        press("home-selected-group", Key.DirectionUp)
        composeRule.onNodeWithTag("home-settings").assertIsFocused()
        press("home-settings", Key.Enter)

        composeRule.onNodeWithTag("settings-root").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-back").assertIsFocused()
        press("settings-back", Key.DirectionDown)
        composeRule.onNodeWithTag("settings-playlist").assertIsFocused()
        press("settings-playlist", Key.DirectionDown)
        composeRule.onNodeWithTag("settings-epg").assertIsFocused()
        press("settings-epg", Key.DirectionUp)
        composeRule.onNodeWithTag("settings-playlist").assertIsFocused()
        press("settings-playlist", Key.DirectionUp)
        composeRule.onNodeWithTag("settings-back").assertIsFocused()
        press("settings-back", Key.Enter)

        composeRule.waitUntilAtLeastOneExists(hasTestTag("home-current-channel"), 5_000)
        composeRule.onNodeWithTag("home-current-channel").assertIsFocused()
    }

    private fun press(tag: String, key: Key) {
        composeRule.onNodeWithTag(tag).performKeyInput {
            keyDown(key)
            keyUp(key)
        }
        composeRule.waitForIdle()
    }

    private fun pressBack() {
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        composeRule.waitForIdle()
    }

    private class SeedConfiguredHomeRule : ExternalResource() {
        private val context = ApplicationProvider.getApplicationContext<Context>()
        private val settings = SettingsRepository(context)
        private lateinit var originalConfig: SourceConfig
        private var originalLastChannelId: String? = null
        private var originalLastSyncAt: Long = 0L

        override fun before() = runBlocking {
            originalConfig = settings.config.first()
            originalLastChannelId = settings.lastChannelId.first()
            originalLastSyncAt = settings.lastSyncAt.first()
            withDatabase { database ->
                database.iptvDao().upsertChannels(
                    listOf(
                        ChannelEntity(
                            id = TEST_CHANNEL_ID,
                            name = "遥控器测试频道",
                            streamUrl = "http://127.0.0.1:65534/unavailable/index.m3u8",
                            groupName = "测试",
                            channelNumber = 900,
                        ),
                    ),
                )
            }
            settings.saveConfig(originalConfig.playlistUrl, originalConfig.epgUrl, complete = true)
            settings.setLastChannel(TEST_CHANNEL_ID)
            settings.setLastSyncAt(System.currentTimeMillis())
        }

        override fun after() = runBlocking {
            withDatabase { database ->
                database.openHelper.writableDatabase.execSQL(
                    "DELETE FROM channels WHERE id = ?",
                    arrayOf(TEST_CHANNEL_ID),
                )
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
        const val TEST_CHANNEL_ID = "codex-remote-navigation-test"
    }
}
