package com.wangjingfei.hometv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.HttpDataSource
import com.wangjingfei.hometv.BuildConfig
import com.wangjingfei.hometv.data.Channel
import com.wangjingfei.hometv.data.DiagnosticItem
import com.wangjingfei.hometv.data.DiagnosticStatus
import com.wangjingfei.hometv.data.IptvRepository
import com.wangjingfei.hometv.data.NetworkMonitor
import com.wangjingfei.hometv.data.Programme
import com.wangjingfei.hometv.data.SettingsRepository
import com.wangjingfei.hometv.data.SourceConfig
import com.wangjingfei.hometv.player.PlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.net.URI
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class AppScreen { SETUP, HOME, PLAYER, SETTINGS }

data class MainUiState(
    val screen: AppScreen = AppScreen.SETUP,
    val channels: List<Channel> = emptyList(),
    val programmes: List<Programme> = emptyList(),
    val config: SourceConfig = SourceConfig(BuildConfig.DEFAULT_PLAYLIST_URL, BuildConfig.DEFAULT_PLAYLIST_URL.substringBeforeLast('/') + "/epg.xml", false),
    val selectedGroup: String = "全部",
    val searchQuery: String = "",
    val isSyncing: Boolean = false,
    val message: String? = null,
    val selectedChannel: Channel? = null,
    val controlsVisible: Boolean = true,
    val numericBuffer: String = "",
    val playbackState: Int = Player.STATE_IDLE,
    val playbackError: String? = null,
    val diagnostics: List<DiagnosticItem> = emptyList(),
    val audioLabel: String? = null,
    val subtitlesEnabled: Boolean = true,
    val lastChannelId: String? = null,
    val lastEpgSyncAt: Long = 0L,
    val showBuffering: Boolean = false,
) {
    val groups: List<String> get() = listOf("全部", "收藏", "最近") + channels.map { it.group }.distinct()
    val visibleChannels: List<Channel> get() {
        val grouped = when (selectedGroup) {
            "收藏" -> channels.filter { it.favorite }
            "最近" -> channels.filter { it.lastWatchedAt != null }.sortedByDescending { it.lastWatchedAt }
            "全部" -> channels
            else -> channels.filter { it.group == selectedGroup }
        }
        return if (searchQuery.isBlank()) grouped else grouped.filter { it.name.contains(searchQuery, ignoreCase = true) || it.number.toString() == searchQuery }
    }

    fun currentProgramme(channelId: String, now: Long = System.currentTimeMillis()): Programme? =
        programmes.firstOrNull { it.channelId == channelId && it.startAt <= now && (it.stopAt == null || it.stopAt > now) }

    fun nextProgramme(channelId: String, now: Long = System.currentTimeMillis()): Programme? =
        programmes.firstOrNull { it.channelId == channelId && it.startAt > now }
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: IptvRepository,
    private val settings: SettingsRepository,
    val playerManager: PlayerManager,
    private val networkMonitor: NetworkMonitor,
) : ViewModel(), Player.Listener {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    private var controlsJob: Job? = null
    private var numericJob: Job? = null
    private var switchJob: Job? = null
    private var retryJob: Job? = null
    private var bufferingJob: Job? = null
    private var retryCount = 0

    init {
        playerManager.player.addListener(this)
        viewModelScope.launch {
            settings.config.collect { config -> _uiState.value = _uiState.value.copy(config = config) }
        }
        viewModelScope.launch {
            settings.lastChannelId.collect { _uiState.value = _uiState.value.copy(lastChannelId = it) }
        }
        viewModelScope.launch {
            settings.lastEpgSyncAt.collect { _uiState.value = _uiState.value.copy(lastEpgSyncAt = it) }
        }
        viewModelScope.launch {
            settings.subtitlesEnabled.collect {
                playerManager.setSubtitlesEnabled(it)
                _uiState.value = _uiState.value.copy(subtitlesEnabled = it)
            }
        }
        viewModelScope.launch {
            settings.audioLanguage.collect {
                playerManager.setPreferredAudioLanguage(it)
                _uiState.value = _uiState.value.copy(audioLabel = it)
            }
        }
        viewModelScope.launch {
            repository.channels.collect { channels ->
                val current = _uiState.value
                _uiState.value = current.copy(
                    channels = channels,
                    screen = if (channels.isNotEmpty() && current.screen == AppScreen.SETUP) AppScreen.HOME else current.screen,
                )
            }
        }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repository.programmes(now - 6 * 60 * 60_000L, now + 7 * 24 * 60 * 60_000L).collect {
                _uiState.value = _uiState.value.copy(programmes = it)
            }
        }
        viewModelScope.launch {
            delay(250)
            val config = settings.config.first()
            val stale = System.currentTimeMillis() - settings.lastSyncAt.first() >= REFRESH_INTERVAL_MS
            if (repository.channels.first().isEmpty() || stale) sync(config.playlistUrl, config.epgUrl)
            while (true) {
                delay(REFRESH_INTERVAL_MS)
                sync()
            }
        }
    }

    fun sync(playlistUrl: String = _uiState.value.config.playlistUrl, epgUrl: String = _uiState.value.config.epgUrl) {
        if (_uiState.value.isSyncing) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncing = true, message = null)
            runCatching { repository.sync(playlistUrl.trim(), epgUrl.trim()) }
                .onSuccess {
                    val syncMessage = when {
                        it.playlistUpdated && it.epgUpdated -> "已同步 ${it.channelCount} 个频道、${it.programmeCount} 条节目"
                        it.playlistUpdated -> "已更新 ${it.channelCount} 个频道，节目单无变化"
                        it.epgUpdated -> "频道列表无变化，已更新 ${it.programmeCount} 条节目"
                        else -> "已检查 ${it.channelCount} 个频道，数据均为最新"
                    }
                    _uiState.value = _uiState.value.copy(
                        isSyncing = false,
                        screen = AppScreen.HOME,
                        message = it.warning ?: syncMessage,
                    )
                }
                .onFailure {
                    val cached = _uiState.value.channels.isNotEmpty()
                    _uiState.value = _uiState.value.copy(
                        isSyncing = false,
                        screen = if (cached) _uiState.value.screen else AppScreen.SETUP,
                        message = it.message ?: "同步失败",
                    )
                }
        }
    }

    fun openHome() {
        if (_uiState.value.screen == AppScreen.PLAYER) playerManager.stop()
        _uiState.value = _uiState.value.copy(screen = AppScreen.HOME, controlsVisible = false)
    }
    fun openSettings() { _uiState.value = _uiState.value.copy(screen = AppScreen.SETTINGS) }
    fun selectGroup(group: String) { _uiState.value = _uiState.value.copy(selectedGroup = group) }
    fun setSearch(query: String) { _uiState.value = _uiState.value.copy(searchQuery = query) }

    fun play(channel: Channel) {
        switchJob?.cancel()
        retryJob?.cancel()
        retryCount = 0
        playerManager.play(channel)
        viewModelScope.launch { repository.markWatched(channel.id) }
        _uiState.value = _uiState.value.copy(screen = AppScreen.PLAYER, selectedChannel = channel, controlsVisible = true, playbackError = null)
        scheduleControlsHide()
    }

    fun switchChannel(delta: Int) {
        val channels = _uiState.value.channels
        if (channels.isEmpty()) return
        val index = channels.indexOfFirst { it.id == _uiState.value.selectedChannel?.id }.coerceAtLeast(0)
        val candidate = channels[(index + delta + channels.size) % channels.size]
        retryJob?.cancel()
        retryCount = 0
        _uiState.value = _uiState.value.copy(selectedChannel = candidate, controlsVisible = true, playbackError = null)
        switchJob?.cancel()
        switchJob = viewModelScope.launch {
            delay(350)
            playerManager.play(candidate)
            repository.markWatched(candidate.id)
            scheduleControlsHide()
        }
    }

    fun toggleFavorite(channelId: String) = viewModelScope.launch { repository.toggleFavorite(channelId) }
    fun retry() {
        retryJob?.cancel()
        retryCount = 0
        playerManager.retry()
    }
    fun toggleControls() {
        val visible = !_uiState.value.controlsVisible
        _uiState.value = _uiState.value.copy(controlsVisible = visible)
        if (visible) scheduleControlsHide()
    }
    fun showControls() {
        _uiState.value = _uiState.value.copy(controlsVisible = true)
        scheduleControlsHide()
    }
    fun holdControls() { controlsJob?.cancel() }
    fun resumeControlsTimeout() { if (_uiState.value.controlsVisible) scheduleControlsHide() }
    fun hideControls() { _uiState.value = _uiState.value.copy(controlsVisible = false) }

    fun enterDigit(digit: Int) {
        val next = (_uiState.value.numericBuffer + digit).takeLast(3)
        _uiState.value = _uiState.value.copy(numericBuffer = next, controlsVisible = true)
        numericJob?.cancel()
        numericJob = viewModelScope.launch {
            delay(1_200)
            val number = _uiState.value.numericBuffer.toIntOrNull()
            val channel = _uiState.value.channels.firstOrNull { it.number == number }
            _uiState.value = _uiState.value.copy(numericBuffer = "")
            if (channel != null) play(channel) else _uiState.value = _uiState.value.copy(message = "没有 $number 号频道")
        }
    }

    fun cycleAudio() {
        val language = playerManager.cycleAudioLanguage()
        _uiState.value = _uiState.value.copy(audioLabel = language)
        viewModelScope.launch { settings.setAudioLanguage(language) }
    }
    fun toggleSubtitles() {
        val enabled = playerManager.setSubtitlesEnabled(!_uiState.value.subtitlesEnabled)
        _uiState.value = _uiState.value.copy(subtitlesEnabled = enabled)
        viewModelScope.launch { settings.setSubtitlesEnabled(enabled) }
    }

    fun clearEpg() = viewModelScope.launch {
        repository.clearEpg()
        _uiState.value = _uiState.value.copy(message = "本地节目单缓存已清空")
    }

    fun clearHistory() = viewModelScope.launch {
        repository.clearHistory()
        _uiState.value = _uiState.value.copy(message = "最近观看记录已清空")
    }

    fun clearFavorites() = viewModelScope.launch {
        repository.clearFavorites()
        _uiState.value = _uiState.value.copy(message = "收藏已重置")
    }

    fun runDiagnostics(playlistUrl: String = _uiState.value.config.playlistUrl, epgUrl: String = _uiState.value.config.epgUrl) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(diagnostics = listOf(DiagnosticItem("检查中", DiagnosticStatus.CHECKING, "正在访问局域网服务")))
            val hlsHealth = runCatching {
                val uri = URI(playlistUrl)
                "${uri.scheme}://${uri.host}:8090/healthz"
            }.getOrDefault("http://192.168.31.200:8090/healthz")
            val epgHealth = runCatching {
                val uri = URI(epgUrl)
                "${uri.scheme}://${uri.host}${if (uri.port > 0) ":${uri.port}" else ""}/healthz"
            }.getOrDefault("http://192.168.31.200:8091/healthz")
            val epgStatus = epgHealth.substringBeforeLast('/') + "/status.json"
            val results = mutableListOf(
                networkMonitor.diagnose(),
                repository.checkUrl("频道列表", playlistUrl),
                repository.checkUrl("节目单", epgUrl),
                repository.checkUrl("EPG 服务", epgHealth),
                repository.checkUrl("EPG 状态", epgStatus),
                repository.checkUrl("HLS 服务", hlsHealth),
            )
            _uiState.value.channels.firstOrNull()?.let {
                results += repository.checkUrl("样例频道 HLS", it.streamUrl)
            }
            _uiState.value = _uiState.value.copy(diagnostics = results)
        }
    }

    fun openDiagnostics() {
        playerManager.stop()
        _uiState.value = _uiState.value.copy(screen = AppScreen.SETTINGS)
        runDiagnostics()
    }

    fun pauseForBackground() = playerManager.stop()
    fun resumeAfterForeground() {
        if (_uiState.value.screen == AppScreen.PLAYER) _uiState.value.selectedChannel?.let(playerManager::play)
    }
    fun releasePlayer() = playerManager.release()

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_READY) retryCount = 0
        bufferingJob?.cancel()
        _uiState.value = _uiState.value.copy(playbackState = playbackState, showBuffering = false)
        if (playbackState == Player.STATE_BUFFERING) {
            bufferingJob = viewModelScope.launch {
                delay(800)
                if (_uiState.value.playbackState == Player.STATE_BUFFERING) {
                    _uiState.value = _uiState.value.copy(showBuffering = true)
                }
            }
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        val httpStatus = (error.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode
        val conclusion = when {
            httpStatus == 404 -> "频道配置已失效"
            httpStatus == 502 || httpStatus == 503 -> "NAS 正在启动频道或媒体服务异常"
            error.errorCodeName.contains("DECOD", ignoreCase = true) -> "电视不支持该频道的媒体编码"
            error.errorCodeName.contains("NETWORK", ignoreCase = true) || error.errorCodeName.contains("TIMEOUT", ignoreCase = true) -> "电视无法连接直播服务"
            else -> "播放失败"
        }
        val detail = "$conclusion（${error.errorCodeName}）：${error.message ?: "无更多信息"}"
        _uiState.value = _uiState.value.copy(playbackError = detail, controlsVisible = true)
        if (httpStatus != 404 && retryCount < 3) {
            val delayMillis = listOf(1_000L, 2_000L, 4_000L)[retryCount]
            retryCount += 1
            retryJob?.cancel()
            retryJob = viewModelScope.launch {
                _uiState.value = _uiState.value.copy(playbackError = "$detail；${delayMillis / 1000} 秒后第 $retryCount/3 次重试")
                delay(delayMillis)
                playerManager.retry()
            }
        }
    }

    private fun scheduleControlsHide() {
        controlsJob?.cancel()
        controlsJob = viewModelScope.launch { delay(5_000); hideControls() }
    }

    override fun onCleared() {
        playerManager.player.removeListener(this)
    }

    private companion object {
        const val REFRESH_INTERVAL_MS = 6 * 60 * 60_000L
    }
}
