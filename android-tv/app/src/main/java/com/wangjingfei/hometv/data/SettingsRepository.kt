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
import kotlinx.coroutines.flow.first

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(@param:ApplicationContext private val context: Context) {
    private object Keys {
        val playlistUrl = stringPreferencesKey("playlist_url")
        val epgUrl = stringPreferencesKey("epg_url")
        val setupComplete = booleanPreferencesKey("setup_complete")
        val lastChannelId = stringPreferencesKey("last_channel_id")
        val lastSyncAt = longPreferencesKey("last_sync_at")
        val lastEpgSyncAt = longPreferencesKey("last_epg_sync_at")
        val subtitlesEnabled = booleanPreferencesKey("subtitles_enabled")
        val audioLanguage = stringPreferencesKey("audio_language")
        val playlistEtag = stringPreferencesKey("playlist_etag")
        val playlistModified = stringPreferencesKey("playlist_modified")
        val epgEtag = stringPreferencesKey("epg_etag")
        val epgModified = stringPreferencesKey("epg_modified")
        val playlistMetadataUrl = stringPreferencesKey("playlist_metadata_url")
        val epgMetadataUrl = stringPreferencesKey("epg_metadata_url")
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
    val lastEpgSyncAt = context.settingsDataStore.data.map { it[Keys.lastEpgSyncAt] ?: 0L }
    val subtitlesEnabled = context.settingsDataStore.data.map { it[Keys.subtitlesEnabled] ?: true }
    val audioLanguage = context.settingsDataStore.data.map { it[Keys.audioLanguage] }

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

    suspend fun setLastEpgSyncAt(value: Long) {
        context.settingsDataStore.edit { it[Keys.lastEpgSyncAt] = value }
    }

    suspend fun setSubtitlesEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.subtitlesEnabled] = enabled }
    }

    suspend fun setAudioLanguage(language: String?) {
        context.settingsDataStore.edit {
            if (language == null) it.remove(Keys.audioLanguage) else it[Keys.audioLanguage] = language
        }
    }

    suspend fun clearLastChannel() {
        context.settingsDataStore.edit { it.remove(Keys.lastChannelId) }
    }

    suspend fun playlistMetadata(url: String): HttpMetadata = context.settingsDataStore.data.first().let {
        if (it[Keys.playlistMetadataUrl] == url) HttpMetadata(it[Keys.playlistEtag], it[Keys.playlistModified]) else HttpMetadata()
    }

    suspend fun epgMetadata(url: String): HttpMetadata = context.settingsDataStore.data.first().let {
        if (it[Keys.epgMetadataUrl] == url) HttpMetadata(it[Keys.epgEtag], it[Keys.epgModified]) else HttpMetadata()
    }

    suspend fun setPlaylistMetadata(url: String, metadata: HttpMetadata) = setMetadata(
        url,
        metadata,
        Keys.playlistMetadataUrl,
        Keys.playlistEtag,
        Keys.playlistModified,
    )

    suspend fun setEpgMetadata(url: String, metadata: HttpMetadata) = setMetadata(
        url,
        metadata,
        Keys.epgMetadataUrl,
        Keys.epgEtag,
        Keys.epgModified,
    )

    private suspend fun setMetadata(
        url: String,
        metadata: HttpMetadata,
        urlKey: androidx.datastore.preferences.core.Preferences.Key<String>,
        etagKey: androidx.datastore.preferences.core.Preferences.Key<String>,
        modifiedKey: androidx.datastore.preferences.core.Preferences.Key<String>,
    ) {
        context.settingsDataStore.edit {
            it[urlKey] = url
            if (metadata.etag == null) it.remove(etagKey) else it[etagKey] = metadata.etag
            if (metadata.lastModified == null) it.remove(modifiedKey) else it[modifiedKey] = metadata.lastModified
        }
    }
}
