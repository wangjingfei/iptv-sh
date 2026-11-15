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
import com.wangjingfei.hometv.data.SourceProbe
import com.wangjingfei.hometv.data.networkOrigin
import com.wangjingfei.hometv.player.PlayerManager
import com.wangjingfei.hometv.player.PlaybackErrorPolicy
import com.wangjingfei.hometv.player.PlaybackFailure
import com.wangjingfei.hometv.remote.RemoteSetupCoordinator
import com.wangjingfei.hometv.remote.RemoteSetupPhase
import com.wangjingfei.hometv.remote.RemoteSetupSession
import com.wangjingfei.hometv.remote.RemoteSourceSubmission
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.net.URI
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class AppScreen { SETUP, HOME, PLAYER, SETTINGS }

data class SourceDraft(val playlistUrl: String, val epgUrl: String)

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
    val playWhenReady: Boolean = true,
    val isPlaying: Boolean = false,
    val playbackError: String? = null,
    val diagnostics: List<DiagnosticItem> = emptyList(),
    val audioLabel: String? = null,
    val subtitlesEnabled: Boolean = true,
    val lastChannelId: String? = null,
    val homeFocusChannelId: String? = null,
    val homeFocusRequestVersion: Long = 0L,
    val lastEpgSyncAt: Long = 0L,
    val showBuffering: Boolean = false,
    val lastPlaybackFailure: PlaybackFailure? = null,
    val lastPlaybackFailureAt: Long = 0L,
    val lastSyncWarning: String? = null,
    val isTestingSources: Boolean = false,
    val testedSource: SourceProbe? = null,
    val sourceDraft: SourceDraft? = null,
    val sourceDraftVersion: Long = 0L,
    val remoteSetup: RemoteSetupSession? = null,
) {
    val shouldKeepScreenOn: Boolean get() = isPlaying
    val isPlaybackActive: Boolean get() = playWhenReady && playbackError == null
    val shouldAutoHideControls: Boolean get() = playbackError == null
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

    fun quickChannels(channelId: String, radius: Int = 2): List<Channel> {
        if (channels.isEmpty()) return emptyList()
        val currentIndex = channels.indexOfFirst { it.id == channelId }.coerceAtLeast(0)
        return (-radius..radius)
            .map { channels[(currentIndex + it + channels.size) % channels.size] }
            .distinctBy { it.id }
    }
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: IptvRepository,
    private val settings: SettingsRepository,
    val playerManager: PlayerManager,
    private val networkMonitor: NetworkMonitor,
    private val remoteSetupCoordinator: RemoteSetupCoordinator,
) : ViewModel(), Player.Listener {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    private var controlsJob: Job? = null
    private var numericJob: Job? = null
    private var switchJob: Job? = null
    private var retryJob: Job? = null
    private var bufferingJob: Job? = null
    private var firstFrameJob: Job? = null
    private var remoteSetupExpiryJob: Job? = null
    private var firstFrameChannelId: String? = null
    private var retryCount = 0

    init {
        playerManager.player.addListener(this)
        viewModelScope.launch {
            settings.config.collect { config ->
                val current = _uiState.value
                _uiState.value = current.copy(
                    config = config,
                    screen = if (config.setupComplete && current.channels.isNotEmpty() && current.screen == AppScreen.SETUP) AppScreen.HOME else current.screen,
                )
            }
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
            remoteSetupCoordinator.session.collect { session ->
                _uiState.value = _uiState.value.copy(remoteSetup = session)
            }
        }
        viewModelScope.launch {
            remoteSetupCoordinator.submissions.collect(::handleRemoteSubmission)
        }
        viewModelScope.launch {
            repository.channels.collect { channels ->
                val current = _uiState.value
                _uiState.value = current.copy(
                    channels = channels,
                    screen = if (channels.isNotEmpty() && current.config.setupComplete && current.screen == AppScreen.SETUP) AppScreen.HOME else current.screen,
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
            if (config.setupComplete && (repository.channels.first().isEmpty() || stale)) sync(config.playlistUrl, config.epgUrl)
            while (true) {
                delay(REFRESH_INTERVAL_MS)
                if (settings.config.first().setupComplete) sync()
            }
        }
    }

    fun testSources(playlistUrl: String, epgUrl: String) {
        if (_uiState.value.isTestingSources || _uiState.value.isSyncing) return
        viewModelScope.launch { probeSources(playlistUrl, epgUrl) }
    }

    fun applyTestedSource() {
        val probe = _uiState.value.testedSource ?: return
        stopRemoteSetup()
        sync(probe.playlistUrl, probe.resolvedEpgUrl)
    }

    fun startRemoteSetup(playlistUrl: String, epgUrl: String) {
        val result = remoteSetupCoordinator.start(playlistUrl, epgUrl)
        result.onSuccess { session ->
            remoteSetupExpiryJob?.cancel()
            remoteSetupExpiryJob = viewModelScope.launch {
                delay((session.expiresAt - System.currentTimeMillis()).coerceAtLeast(0L))
                if (_uiState.value.remoteSetup?.id == session.id) {
                    remoteSetupCoordinator.stop()
                    _uiState.value = _uiState.value.copy(message = "手机设置已过期，请重新打开二维码")
                }
            }
        }.onFailure { error ->
            _uiState.value = _uiState.value.copy(message = error.message ?: "无法启动手机设置")
        }
    }

    fun stopRemoteSetup() {
        remoteSetupExpiryJob?.cancel()
        remoteSetupExpiryJob = null
        remoteSetupCoordinator.stop()
    }

    fun invalidateSourceTest() {
        if (_uiState.value.testedSource != null) {
            _uiState.value = _uiState.value.copy(testedSource = null, message = null)
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
                        lastSyncWarning = it.warning,
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
        stopRemoteSetup()
        if (_uiState.value.screen == AppScreen.PLAYER) {
            cancelPendingPlayback()
            playerManager.stop()
        }
        _uiState.value = _uiState.value.copy(screen = AppScreen.HOME, controlsVisible = false)
    }
    fun openSettings() { _uiState.value = _uiState.value.copy(screen = AppScreen.SETTINGS) }
    fun selectGroup(group: String) { _uiState.value = _uiState.value.copy(selectedGroup = group) }
    fun setSearch(query: String) { _uiState.value = _uiState.value.copy(searchQuery = query) }

    fun play(channel: Channel) {
        stopRemoteSetup()
        switchJob?.cancel()
        retryJob?.cancel()
        retryCount = 0
        startPlayback(channel)
        viewModelScope.launch { repository.markWatched(channel.id) }
        _uiState.value = _uiState.value.copy(
            screen = AppScreen.PLAYER,
            selectedChannel = channel,
            homeFocusChannelId = channel.id,
            controlsVisible = true,
            playbackError = null,
        )
        scheduleControlsHide()
    }

    fun switchChannel(delta: Int) {
        val channels = _uiState.value.channels
        if (channels.isEmpty()) return
        val index = channels.indexOfFirst { it.id == _uiState.value.selectedChannel?.id }.coerceAtLeast(0)
        val candidate = channels[(index + delta + channels.size) % channels.size]
        retryJob?.cancel()
        cancelFirstFrameTimeout()
        retryCount = 0
        _uiState.value = _uiState.value.copy(selectedChannel = candidate, controlsVisible = true, playbackError = null)
        switchJob?.cancel()
        switchJob = viewModelScope.launch {
            delay(350)
            startPlayback(candidate)
            repository.markWatched(candidate.id)
            scheduleControlsHide()
        }
    }

    fun toggleFavorite(channelId: String) = viewModelScope.launch { repository.toggleFavorite(channelId) }
    fun retry() {
        retryJob?.cancel()
        retryCount = 0
        _uiState.value = _uiState.value.copy(playbackError = null)
        armFirstFrameTimeout(_uiState.value.selectedChannel?.id)
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
        _uiState.value = _uiState.value.copy(
            numericBuffer = next,
            controlsVisible = _uiState.value.screen == AppScreen.PLAYER || _uiState.value.controlsVisible,
        )
        numericJob?.cancel()
        numericJob = viewModelScope.launch {
            delay(1_200)
            val number = _uiState.value.numericBuffer.toIntOrNull()
            val channel = _uiState.value.channels.firstOrNull { it.number == number }
            if (channel == null) {
                _uiState.value = _uiState.value.copy(numericBuffer = "", message = "没有 $number 号频道")
            } else {
                when (_uiState.value.screen) {
                    AppScreen.HOME -> {
                        _uiState.value = _uiState.value.copy(
                            numericBuffer = "",
                            selectedGroup = "全部",
                            searchQuery = "",
                            homeFocusChannelId = channel.id,
                            homeFocusRequestVersion = _uiState.value.homeFocusRequestVersion + 1,
                            message = null,
                        )
                    }
                    AppScreen.PLAYER -> {
                        _uiState.value = _uiState.value.copy(numericBuffer = "")
                        play(channel)
                    }
                    AppScreen.SETUP, AppScreen.SETTINGS -> {
                        _uiState.value = _uiState.value.copy(numericBuffer = "")
                    }
                }
            }
        }
    }

    fun togglePlayback() {
        playerManager.togglePlayback()
        showControls()
    }

    fun resumePlayback() {
        playerManager.resumeAtLiveEdge()
        showControls()
    }

    fun pausePlayback() {
        playerManager.pause()
        showControls()
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
    fun resetAudioPreference() {
        playerManager.setPreferredAudioLanguage(null)
        _uiState.value = _uiState.value.copy(audioLabel = null)
        viewModelScope.launch { settings.setAudioLanguage(null) }
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
            val hlsHealth = (_uiState.value.channels.firstOrNull()?.streamUrl?.let(::networkOrigin)
                ?: networkOrigin(playlistUrl)
                ?: "http://192.168.31.200:8090") + "/healthz"
            val epgHealth = (networkOrigin(epgUrl)
                ?: networkOrigin(playlistUrl)
                ?: "http://192.168.31.200:8091") + "/healthz"
            val epgStatus = epgHealth.substringBeforeLast('/') + "/status.json"
            val results = mutableListOf(
                networkMonitor.diagnose(),
                repository.checkUrl("频道列表", playlistUrl),
                repository.checkUrl("节目单", epgUrl),
                repository.checkUrl("EPG 服务", epgHealth),
                repository.checkUrl("EPG 状态", epgStatus),
                repository.checkUrl("HLS 服务", hlsHealth),
            )
            _uiState.value.lastSyncWarning?.let {
                results += DiagnosticItem("最近导入警告", DiagnosticStatus.WARNING, it)
            }
            _uiState.value.lastPlaybackFailure?.let {
                val channelName = _uiState.value.selectedChannel?.name ?: "未知频道"
                results += DiagnosticItem(
                    "最近播放错误",
                    DiagnosticStatus.ERROR,
                    "$channelName，${DIAGNOSTIC_TIME_FORMAT.format(Instant.ofEpochMilli(_uiState.value.lastPlaybackFailureAt))}：${it.detail}",
                )
            }
            _uiState.value.channels.firstOrNull()?.let {
                results += repository.checkUrl("样例频道 HLS", it.streamUrl)
            }
            _uiState.value = _uiState.value.copy(diagnostics = results)
        }
    }

    fun openDiagnostics() {
        cancelPendingPlayback()
        playerManager.stop()
        _uiState.value = _uiState.value.copy(screen = AppScreen.SETTINGS)
        runDiagnostics()
    }

    fun pauseForBackground() {
        stopRemoteSetup()
        cancelPendingPlayback()
        playerManager.stop()
    }
    fun resumeAfterForeground() {
        if (_uiState.value.screen == AppScreen.PLAYER) _uiState.value.selectedChannel?.let(::startPlayback)
    }
    fun releasePlayer() {
        cancelPendingPlayback()
        playerManager.release()
    }

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

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        _uiState.value = _uiState.value.copy(playWhenReady = playWhenReady)
    }

    override fun onPlayerError(error: PlaybackException) {
        cancelFirstFrameTimeout()
        controlsJob?.cancel()
        val httpStatus = generateSequence(error as Throwable) { it.cause }
            .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
            .firstOrNull()
            ?.responseCode
        val failure = PlaybackErrorPolicy.classify(error.errorCodeName, httpStatus, error.message)
        _uiState.value = _uiState.value.copy(
            playbackError = failure.detail,
            controlsVisible = true,
            lastPlaybackFailure = failure,
            lastPlaybackFailureAt = System.currentTimeMillis(),
        )
        if (retryCount < failure.retryDelaysMillis.size) {
            val delayMillis = failure.retryDelaysMillis[retryCount]
            retryCount += 1
            retryJob?.cancel()
            retryJob = viewModelScope.launch {
                _uiState.value = _uiState.value.copy(playbackError = "${failure.detail}；${delayMillis / 1000} 秒后第 $retryCount/${failure.retryDelaysMillis.size} 次重试")
                delay(delayMillis)
                armFirstFrameTimeout(_uiState.value.selectedChannel?.id)
                playerManager.retry()
            }
        }
    }

    override fun onRenderedFirstFrame() {
        if (playerManager.player.currentMediaItem?.mediaId != firstFrameChannelId) return
        cancelFirstFrameTimeout()
        _uiState.value = _uiState.value.copy(showBuffering = false, playbackError = null)
        if (_uiState.value.controlsVisible) scheduleControlsHide()
    }

    private fun startPlayback(channel: Channel) {
        val alreadyPlaying = playerManager.currentChannel.value?.id == channel.id &&
            playerManager.player.playbackState == Player.STATE_READY
        if (alreadyPlaying) cancelFirstFrameTimeout() else armFirstFrameTimeout(channel.id)
        playerManager.play(channel)
    }

    private fun armFirstFrameTimeout(channelId: String?) {
        cancelFirstFrameTimeout()
        if (channelId == null) return
        firstFrameChannelId = channelId
        firstFrameJob = viewModelScope.launch {
            delay(FIRST_FRAME_TIMEOUT_MS)
            val isCurrentChannel = firstFrameChannelId == channelId &&
                playerManager.player.currentMediaItem?.mediaId == channelId
            if (!isCurrentChannel || _uiState.value.screen != AppScreen.PLAYER) return@launch
            firstFrameJob = null
            firstFrameChannelId = null
            playerManager.stop()
            val failure = PlaybackErrorPolicy.firstFrameTimeout()
            controlsJob?.cancel()
            _uiState.value = _uiState.value.copy(
                showBuffering = false,
                controlsVisible = true,
                playbackError = failure.detail,
                lastPlaybackFailure = failure,
                lastPlaybackFailureAt = System.currentTimeMillis(),
            )
        }
    }

    private fun cancelFirstFrameTimeout() {
        firstFrameJob?.cancel()
        firstFrameJob = null
        firstFrameChannelId = null
    }

    private fun cancelPendingPlayback() {
        switchJob?.cancel()
        switchJob = null
        retryJob?.cancel()
        retryJob = null
        cancelFirstFrameTimeout()
    }

    private fun scheduleControlsHide() {
        controlsJob?.cancel()
        controlsJob = viewModelScope.launch {
            delay(5_000)
            if (_uiState.value.shouldAutoHideControls) hideControls()
        }
    }

    override fun onCleared() {
        stopRemoteSetup()
        cancelPendingPlayback()
        playerManager.player.removeListener(this)
    }

    private fun handleRemoteSubmission(submission: RemoteSourceSubmission) {
        if (remoteSetupCoordinator.session.value?.id != submission.sessionId) return
        if (_uiState.value.isTestingSources || _uiState.value.isSyncing) {
            remoteSetupCoordinator.updateResult(submission.sessionId, RemoteSetupPhase.ERROR, "电视正在执行另一项任务，请稍后重试")
            return
        }
        _uiState.value = _uiState.value.copy(
            sourceDraft = SourceDraft(submission.playlistUrl, submission.epgUrl),
            sourceDraftVersion = _uiState.value.sourceDraftVersion + 1,
        )
        viewModelScope.launch { probeSources(submission.playlistUrl, submission.epgUrl, submission.sessionId) }
    }

    private suspend fun probeSources(playlistUrl: String, epgUrl: String, remoteSessionId: String? = null) {
        _uiState.value = _uiState.value.copy(isTestingSources = true, testedSource = null, message = null)
        runCatching { repository.probe(playlistUrl, epgUrl) }
            .onSuccess { probe ->
                val epgSummary = probe.programmeCount?.let { "，节目单 $it 条" } ?: "，节目单不可用或未配置"
                val message = probe.warning ?: "测试通过：${probe.channelCount} 个频道$epgSummary，样例 HLS 可播放"
                _uiState.value = _uiState.value.copy(
                    isTestingSources = false,
                    testedSource = probe,
                    message = message,
                )
                if (remoteSessionId != null) {
                    remoteSetupCoordinator.updateResult(remoteSessionId, RemoteSetupPhase.SUCCESS, message)
                }
            }
            .onFailure { error ->
                val message = error.message ?: "来源测试失败"
                _uiState.value = _uiState.value.copy(
                    isTestingSources = false,
                    testedSource = null,
                    message = message,
                )
                if (remoteSessionId != null) {
                    remoteSetupCoordinator.updateResult(remoteSessionId, RemoteSetupPhase.ERROR, message)
                }
            }
    }

    private companion object {
        const val REFRESH_INTERVAL_MS = 6 * 60 * 60_000L
        const val FIRST_FRAME_TIMEOUT_MS = 30_000L
        val DIAGNOSTIC_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())
    }
}
