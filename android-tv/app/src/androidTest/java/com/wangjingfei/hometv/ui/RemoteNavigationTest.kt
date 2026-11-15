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
        waitUntilFocused("settings-back")
        press("settings-back", Key.DirectionDown)
        waitUntilFocused("settings-playlist")
        press("settings-playlist", Key.DirectionDown)
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
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
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
        private var originalTestChannel: ChannelEntity? = null

        override fun before() = runBlocking {
            originalConfig = settings.config.first()
            originalLastChannelId = settings.lastChannelId.first()
            originalLastSyncAt = settings.lastSyncAt.first()
            withDatabase { database ->
                originalTestChannel = database.iptvDao().getChannels().firstOrNull { it.id == TEST_CHANNEL_ID }
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
                val original = originalTestChannel
                if (original == null) {
                    database.openHelper.writableDatabase.execSQL(
                        "DELETE FROM channels WHERE id = ?",
                        arrayOf(TEST_CHANNEL_ID),
                    )
                } else {
                    database.iptvDao().upsertChannels(listOf(original))
                }
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
