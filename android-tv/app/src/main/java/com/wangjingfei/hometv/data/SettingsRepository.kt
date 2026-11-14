package com.wangjingfei.hometv.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wangjingfei.hometv.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(@param:ApplicationContext private val context: Context) {
    private object Keys {
        val playlistUrl = stringPreferencesKey("playlist_url")
        val epgUrl = stringPreferencesKey("epg_url")
        val setupComplete = booleanPreferencesKey("setup_complete")
        val lastChannelId = stringPreferencesKey("last_channel_id")
        val lastSyncAt = longPreferencesKey("last_sync_at")
    }

    val config: Flow<SourceConfig> = context.settingsDataStore.data.map { prefs ->
        SourceConfig(
            playlistUrl = prefs[Keys.playlistUrl] ?: BuildConfig.DEFAULT_PLAYLIST_URL,
            epgUrl = prefs[Keys.epgUrl] ?: BuildConfig.DEFAULT_PLAYLIST_URL.substringBeforeLast('/') + "/epg.xml",
            setupComplete = prefs[Keys.setupComplete] ?: false,
        )
    }
    val lastChannelId = context.settingsDataStore.data.map { it[Keys.lastChannelId] }
    val lastSyncAt = context.settingsDataStore.data.map { it[Keys.lastSyncAt] ?: 0L }

    suspend fun saveConfig(playlistUrl: String, epgUrl: String, complete: Boolean = true) {
        context.settingsDataStore.edit {
            it[Keys.playlistUrl] = playlistUrl.trim()
            it[Keys.epgUrl] = epgUrl.trim()
            it[Keys.setupComplete] = complete
        }
    }

    suspend fun setLastChannel(channelId: String) {
        context.settingsDataStore.edit { it[Keys.lastChannelId] = channelId }
    }

    suspend fun setLastSyncAt(value: Long) {
        context.settingsDataStore.edit { it[Keys.lastSyncAt] = value }
    }
}
