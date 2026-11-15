@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.wangjingfei.hometv.ui

import android.view.KeyEvent
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button as TvButton
import com.wangjingfei.hometv.BuildConfig
import com.wangjingfei.hometv.R
import com.wangjingfei.hometv.data.Channel
import com.wangjingfei.hometv.data.DiagnosticStatus
import com.wangjingfei.hometv.data.Programme
import java.util.Date
import kotlinx.coroutines.flow.first

@Composable
fun HomeTvApp(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(enabled = state.screen != AppScreen.HOME && state.screen != AppScreen.SETUP) {
        if (state.screen == AppScreen.PLAYER && state.controlsVisible) viewModel.hideControls() else viewModel.openHome()
    }
    when (state.screen) {
        AppScreen.SETUP -> SetupScreen(state, viewModel)
        AppScreen.HOME -> HomeScreen(state, viewModel)
        AppScreen.PLAYER -> PlayerScreen(state, viewModel)
        AppScreen.SETTINGS -> SettingsScreen(state, viewModel)
    }
}

@Composable
private fun SetupScreen(state: MainUiState, viewModel: MainViewModel) {
    var playlist by remember(state.config.playlistUrl) { mutableStateOf(state.config.playlistUrl) }
    var epg by remember(state.config.epgUrl) { mutableStateOf(state.config.epgUrl) }
    val playlistFocus = remember { FocusRequester() }
    val epgFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(state.sourceDraftVersion) {
        state.sourceDraft?.let {
            playlist = it.playlistUrl
            epg = it.epgUrl
        }
    }
    Surface(color = Ink, modifier = Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize().padding(horizontal = 72.dp, vertical = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(0.9f)) {
                Icon(Icons.Default.LiveTv, contentDescription = null, tint = Mint, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(24.dp))
                Text(stringResource(R.string.app_name), fontSize = 42.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.setup_subtitle), fontSize = 22.sp, color = TextMuted)
                Spacer(Modifier.height(28.dp))
                Text(stringResource(R.string.setup_hint), color = TextMuted, fontSize = 18.sp)
            }
            Column(Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = playlist,
                    onValueChange = { playlist = it; viewModel.invalidateSourceTest() },
                    label = { Text(stringResource(R.string.playlist_url)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                        .onPreviewKeyEvent { event ->
                            if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                                epgFocus.requestFocus()
                                true
                            } else false
                        }
                        .focusRequester(playlistFocus)
                        .focusProperties { down = epgFocus }
                        .testTag("setup-playlist"),
                )
                OutlinedTextField(
                    value = epg,
                    onValueChange = { epg = it; viewModel.invalidateSourceTest() },
                    label = { Text(stringResource(R.string.xmltv_url)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                        .onPreviewKeyEvent { event ->
                            if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                            when (event.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_DPAD_UP -> { playlistFocus.requestFocus(); true }
                                KeyEvent.KEYCODE_DPAD_DOWN -> focusManager.moveFocus(FocusDirection.Down)
                                else -> false
                            }
                        }
                        .focusRequester(epgFocus)
                        .focusProperties { up = playlistFocus }
                        .testTag("setup-epg"),
                )
                state.message?.let {
                    val messageColor = when {
                        state.testedSource == null -> ErrorRed
                        state.testedSource.warning != null -> Warm
                        else -> Mint
                    }
                    Text(it, color = messageColor)
                }
                TvButton(
                    onClick = { viewModel.testSources(playlist, epg) },
                    enabled = !state.isTestingSources && !state.isSyncing,
                    modifier = Modifier.height(52.dp)
                        .focusProperties { up = epgFocus }
                        .testTag("setup-test"),
                ) {
                    if (state.isTestingSources) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Icon(Icons.Default.CheckCircle, null)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(if (state.isTestingSources) R.string.testing_sources else R.string.test_connection))
                }
                TvButton(
                    onClick = { viewModel.startRemoteSetup(playlist, epg) },
                    enabled = !state.isTestingSources && !state.isSyncing,
                    modifier = Modifier.height(52.dp).testTag("setup-phone"),
                ) {
                    Icon(Icons.Default.QrCode2, null)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.phone_setup))
                }
                if (state.testedSource?.matches(playlist, epg) == true) {
                    Button(onClick = viewModel::applyTestedSource, enabled = !state.isSyncing, modifier = Modifier.height(52.dp)) {
                        if (state.isSyncing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Icon(Icons.Default.LiveTv, null)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.save_and_enter))
                    }
                }
                if (state.channels.isNotEmpty()) Button(onClick = viewModel::openHome) { Text(stringResource(R.string.use_cache)) }
            }
        }
    }
    state.remoteSetup?.let { session ->
        RemoteSetupDialog(
            session = session,
            canApply = state.testedSource?.matches(playlist, epg) == true,
            onApply = viewModel::applyTestedSource,
            onDismiss = viewModel::stopRemoteSetup,
        )
    }
    LaunchedEffect(Unit) { playlistFocus.requestFocus() }
}

@Composable
private fun HomeScreen(state: MainUiState, viewModel: MainViewModel) {
    val firstCardFocus = remember { FocusRequester() }
    val selectedGroupFocus = remember { FocusRequester() }
    val settingsFocus = remember { FocusRequester() }
    var previewChannelId by remember { mutableStateOf<String?>(null) }
    val gridState = rememberLazyGridState()
    val visibleChannels = state.visibleChannels
    val preferredChannelId = state.homeFocusChannelId ?: state.lastChannelId
    val focusIndex = visibleChannels.indexOfFirst { it.id == preferredChannelId }.takeIf { it >= 0 } ?: 0
    Surface(
        color = Ink,
        modifier = Modifier.fillMaxSize()
            .onPreviewKeyEvent { event -> handleHomeKey(event.nativeKeyEvent, viewModel) },
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(start = 44.dp, end = 44.dp, top = 28.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LiveTv, null, tint = Mint, modifier = Modifier.size(34.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.app_name), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    SearchField(state.searchQuery, viewModel::setSearch)
                    Spacer(Modifier.width(12.dp))
                    IconButton(onClick = { viewModel.sync() }, enabled = !state.isSyncing) {
                        if (state.isSyncing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Refresh, stringResource(R.string.sync))
                    }
                    IconButton(
                        onClick = viewModel::openSettings,
                        modifier = Modifier.focusRequester(settingsFocus)
                            .focusProperties { down = selectedGroupFocus }
                            .testTag("home-settings"),
                    ) { Icon(Icons.Default.Settings, stringResource(R.string.settings)) }
                }
                state.message?.let { Text(it, color = TextMuted, fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp)) }
                if (state.lastEpgSyncAt == 0L || System.currentTimeMillis() - state.lastEpgSyncAt > 12 * 60 * 60_000L) {
                    Text(stringResource(R.string.epg_stale), color = Warm, fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp))
                }
                Text(
                    stringResource(
                        R.string.epg_last_sync,
                        if (state.lastEpgSyncAt == 0L) stringResource(R.string.never_synced) else formatDateTime(state.lastEpgSyncAt),
                    ),
                    color = TextMuted,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(20.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.groups) { group ->
                        GroupButton(
                            label = group,
                            selected = group == state.selectedGroup,
                            onClick = { viewModel.selectGroup(group) },
                            modifier = if (group == state.selectedGroup) {
                                Modifier.focusRequester(selectedGroupFocus)
                                    .focusProperties {
                                        up = settingsFocus
                                        if (visibleChannels.isNotEmpty()) down = firstCardFocus
                                    }
                                    .testTag("home-selected-group")
                            } else {
                                Modifier
                            },
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                if (visibleChannels.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(if (state.searchQuery.isBlank()) R.string.group_empty else R.string.search_empty), color = TextMuted, fontSize = 20.sp)
                    }
                } else {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            state = gridState,
                            modifier = Modifier.weight(0.72f).fillMaxHeight(),
                        ) {
                            items(visibleChannels, key = { it.id }) { channel ->
                                ChannelCard(
                                    channel = channel,
                                    now = state.currentProgramme(channel.id),
                                    next = state.nextProgramme(channel.id),
                                    onPlay = { viewModel.play(channel) },
                                    onFavorite = { viewModel.toggleFavorite(channel.id) },
                                    onFocus = { previewChannelId = channel.id },
                                    modifier = if (channel == visibleChannels[focusIndex]) {
                                        Modifier.focusRequester(firstCardFocus)
                                            .focusProperties { up = selectedGroupFocus }
                                            .testTag("home-current-channel")
                                    } else {
                                        Modifier
                                    },
                                )
                            }
                        }
                        val preview = state.channels.firstOrNull { it.id == previewChannelId } ?: visibleChannels[focusIndex]
                        ProgrammePanel(
                            preview,
                            state.programmes.filter { it.channelId == preview.id && (it.stopAt == null || it.stopAt >= System.currentTimeMillis()) },
                            Modifier.weight(0.28f),
                        )
                    }
                    LaunchedEffect(state.selectedGroup, state.searchQuery, state.channels.size, state.homeFocusRequestVersion) {
                        gridState.scrollToItem(focusIndex)
                        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.any { it.index == focusIndex } }
                            .first { it }
                        firstCardFocus.requestFocus()
                    }
                }
            }
            if (state.numericBuffer.isNotEmpty()) {
                ChannelNumberOverlay(
                    state.numericBuffer,
                    Modifier.align(Alignment.TopCenter).padding(top = 94.dp),
                )
            }
        }
    }
}

private fun handleHomeKey(event: KeyEvent, viewModel: MainViewModel): Boolean {
    if (event.action != KeyEvent.ACTION_DOWN) return false
    val digit = digitFromKeyCode(event.keyCode) ?: return false
    viewModel.enterDigit(digit)
    return true
}

@Composable
private fun ChannelNumberOverlay(buffer: String, modifier: Modifier = Modifier) {
    Box(
        modifier.width(160.dp)
            .height(86.dp)
            .background(Color(0xF2111517), RoundedCornerShape(6.dp))
            .border(2.dp, Mint, RoundedCornerShape(6.dp))
            .testTag("channel-number-overlay"),
        contentAlignment = Alignment.Center,
    ) {
        Text(buffer, fontSize = 48.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Default.Close, stringResource(R.string.clear)) } },
        placeholder = { Text(stringResource(R.string.search_channel)) },
        singleLine = true,
        modifier = Modifier.width(260.dp).height(54.dp),
    )
}

@Composable
private fun GroupButton(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) Mint else PanelRaised,
            contentColor = if (selected) Ink else TextPrimary,
        ),
        shape = RoundedCornerShape(6.dp),
    ) { Text(label) }
}

@Composable
private fun ChannelCard(
    channel: Channel,
    now: Programme?,
    next: Programme?,
    onPlay: () -> Unit,
    onFavorite: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.045f else 1f, label = "频道卡片焦点")
    Card(
        onClick = onPlay,
        modifier = modifier.height(150.dp).scale(scale).onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() },
        colors = CardDefaults.cardColors(containerColor = if (focused) PanelRaised else Panel),
        shape = RoundedCornerShape(6.dp),
        border = if (focused) BorderStroke(2.dp, Mint) else null,
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(channel.number.toString().padStart(3, '0'), color = Mint, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onFavorite, modifier = Modifier.size(30.dp)) {
                    Icon(if (channel.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, stringResource(R.string.favorite), tint = if (channel.favorite) Warm else TextMuted)
                }
            }
            Text(channel.name, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Text(now?.title ?: stringResource(R.string.no_epg), color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 16.sp)
            next?.let { Text(stringResource(R.string.next_program, it.title), color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 16.sp) }
        }
    }
}

@Composable
private fun ProgrammePanel(channel: Channel, programmes: List<Programme>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxHeight().background(Panel, RoundedCornerShape(6.dp)).padding(18.dp)) {
        Text(channel.name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.programme_guide), color = Mint, fontSize = 18.sp)
        Spacer(Modifier.height(14.dp))
        if (programmes.isEmpty()) Text(stringResource(R.string.no_future_programme), color = TextMuted, fontSize = 18.sp)
        programmes.take(9).forEachIndexed { index, programme ->
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.Top) {
                Text(formatTime(programme.startAt), color = if (index == 0) Mint else TextMuted, fontSize = 16.sp, modifier = Modifier.width(68.dp))
                Text(programme.title, color = if (index == 0) TextPrimary else TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 17.sp)
            }
        }
    }
}

@Composable
private fun PlayerScreen(state: MainUiState, viewModel: MainViewModel) {
    val rootFocus = remember { FocusRequester() }
    val controlsFocus = remember { FocusRequester() }
    val channel = state.selectedChannel ?: return
    Box(
        Modifier.fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .testTag("player-root")
            .onPreviewKeyEvent { event -> handlePlayerKey(event.nativeKeyEvent, state.controlsVisible, viewModel) },
    ) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    player = viewModel.playerManager.player
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    keepScreenOn = false
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                }
            },
            update = {
                it.player = viewModel.playerManager.player
                it.keepScreenOn = state.shouldKeepScreenOn
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (state.showBuffering) {
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CircularProgressIndicator(Modifier.size(54.dp), color = Mint)
                Text(stringResource(R.string.opening_channel), color = TextPrimary, fontSize = 18.sp)
            }
        }
        AnimatedVisibility(state.controlsVisible, modifier = Modifier.fillMaxSize()) {
            PlayerOverlay(state, viewModel, channel, controlsFocus)
        }
        if (state.numericBuffer.isNotEmpty()) {
            ChannelNumberOverlay(state.numericBuffer, Modifier.align(Alignment.TopEnd).padding(48.dp))
        }
    }
    LaunchedEffect(state.controlsVisible) {
        if (state.controlsVisible) runCatching { controlsFocus.requestFocus() }
        else rootFocus.requestFocus()
    }
}

private fun handlePlayerKey(event: KeyEvent, controlsVisible: Boolean, viewModel: MainViewModel): Boolean {
    if (event.action != KeyEvent.ACTION_DOWN) return false
    digitFromKeyCode(event.keyCode)?.let {
        viewModel.enterDigit(it)
        return true
    }
    return when (playerRemoteAction(event.keyCode, controlsVisible)) {
        PlayerRemoteAction.PREVIOUS_CHANNEL -> { viewModel.switchChannel(-1); true }
        PlayerRemoteAction.NEXT_CHANNEL -> { viewModel.switchChannel(1); true }
        PlayerRemoteAction.SHOW_CONTROLS -> { viewModel.showControls(); true }
        PlayerRemoteAction.HIDE_CONTROLS -> { viewModel.hideControls(); true }
        PlayerRemoteAction.TOGGLE_PLAYBACK -> { viewModel.togglePlayback(); true }
        PlayerRemoteAction.PLAY -> { viewModel.resumePlayback(); true }
        PlayerRemoteAction.PAUSE -> { viewModel.pausePlayback(); true }
        null -> false
    }
}

@Composable
private fun PlayerOverlay(state: MainUiState, viewModel: MainViewModel, channel: Channel, focusRequester: FocusRequester) {
    Box(Modifier.fillMaxSize().background(Color(0x55000000)).testTag("player-overlay")) {
        Column(
            Modifier.align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color(0xE6111517))
                .onFocusChanged { if (it.hasFocus) viewModel.holdControls() else viewModel.resumeControlsTimeout() }
                .padding(horizontal = 44.dp, vertical = 24.dp),
        ) {
            val quickChannels = state.quickChannels(channel.id)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(quickChannels, key = { it.id }) { quickChannel ->
                    Button(
                        onClick = { viewModel.play(quickChannel) },
                        modifier = if (quickChannel.id == channel.id) {
                            Modifier.focusRequester(focusRequester).testTag("player-current-channel")
                        } else {
                            Modifier
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (quickChannel.id == channel.id) Mint else PanelRaised,
                            contentColor = if (quickChannel.id == channel.id) Ink else TextPrimary,
                        ),
                        shape = RoundedCornerShape(6.dp),
                    ) { Text(stringResource(R.string.quick_channel, quickChannel.number, quickChannel.name), maxLines = 1) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(channel.number.toString().padStart(3, '0'), color = Mint, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(16.dp))
                Text(channel.name, color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = viewModel::togglePlayback,
                    modifier = Modifier.testTag("player-play-pause"),
                ) {
                    Icon(
                        if (state.isPlaybackActive) Icons.Default.Pause else Icons.Default.PlayArrow,
                        stringResource(if (state.isPlaybackActive) R.string.pause_live else R.string.resume_live),
                    )
                }
                IconButton(onClick = { viewModel.toggleFavorite(channel.id) }) {
                    Icon(if (channel.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, stringResource(R.string.favorite), tint = if (channel.favorite) Warm else TextPrimary)
                }
                Button(onClick = viewModel::cycleAudio, colors = ButtonDefaults.buttonColors(containerColor = PanelRaised, contentColor = TextPrimary)) {
                    Icon(Icons.Default.Headphones, null); Spacer(Modifier.width(6.dp)); Text(state.audioLabel ?: stringResource(R.string.default_audio))
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = viewModel::toggleSubtitles, colors = ButtonDefaults.buttonColors(containerColor = PanelRaised, contentColor = TextPrimary)) {
                    Icon(Icons.Default.Subtitles, null); Spacer(Modifier.width(6.dp)); Text(stringResource(if (state.subtitlesEnabled) R.string.subtitles_on else R.string.subtitles_off))
                }
            }
            val now = state.currentProgramme(channel.id)
            val next = state.nextProgramme(channel.id)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(now?.title ?: stringResource(R.string.live_now), color = TextPrimary, fontSize = 19.sp)
                    if (now?.stopAt != null) {
                        Spacer(Modifier.height(7.dp))
                        val progress = ((System.currentTimeMillis() - now.startAt).toFloat() / (now.stopAt - now.startAt).coerceAtLeast(1)).coerceIn(0f, 1f)
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(4.dp), color = Mint)
                    }
                }
                next?.let { Text(stringResource(R.string.next_program_with_time, formatTime(it.startAt), it.title), color = TextMuted, modifier = Modifier.padding(start = 28.dp)) }
            }
            state.playbackError?.let {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = ErrorRed)
                    Spacer(Modifier.width(8.dp)); Text(it, color = ErrorRed, modifier = Modifier.weight(1f))
                    Button(onClick = viewModel::retry) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.retry)) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = viewModel::openDiagnostics,
                        colors = ButtonDefaults.buttonColors(containerColor = PanelRaised, contentColor = TextPrimary),
                    ) { Text(stringResource(R.string.view_diagnostics)) }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(state: MainUiState, viewModel: MainViewModel) {
    var playlist by remember(state.config.playlistUrl) { mutableStateOf(state.config.playlistUrl) }
    var epg by remember(state.config.epgUrl) { mutableStateOf(state.config.epgUrl) }
    var confirmAction by remember { mutableStateOf<String?>(null) }
    val backFocus = remember { FocusRequester() }
    val playlistFocus = remember { FocusRequester() }
    val epgFocus = remember { FocusRequester() }
    LaunchedEffect(state.sourceDraftVersion) {
        state.sourceDraft?.let {
            playlist = it.playlistUrl
            epg = it.epgUrl
        }
    }
    Surface(color = Ink, modifier = Modifier.fillMaxSize().testTag("settings-root")) {
        Row(Modifier.fillMaxSize().padding(44.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            Column(Modifier.weight(1.15f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = viewModel::openHome,
                        modifier = Modifier.focusRequester(backFocus)
                            .focusProperties { down = playlistFocus }
                            .testTag("settings-back"),
                    ) { Icon(Icons.Default.Home, stringResource(R.string.back_home)) }
                    Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.sources_and_sync), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(24.dp))
                OutlinedTextField(
                    playlist,
                    { playlist = it; viewModel.invalidateSourceTest() },
                    label = { Text(stringResource(R.string.playlist_url)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                        .onPreviewKeyEvent { event ->
                            if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                            when (event.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_DPAD_UP -> { backFocus.requestFocus(); true }
                                KeyEvent.KEYCODE_DPAD_DOWN -> { epgFocus.requestFocus(); true }
                                else -> false
                            }
                        }
                        .focusRequester(playlistFocus)
                        .focusProperties {
                            up = backFocus
                            down = epgFocus
                        }
                        .testTag("settings-playlist"),
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    epg,
                    { epg = it; viewModel.invalidateSourceTest() },
                    label = { Text(stringResource(R.string.xmltv_url)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                        .onPreviewKeyEvent { event ->
                            if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                                playlistFocus.requestFocus()
                                true
                            } else {
                                false
                            }
                        }
                        .focusRequester(epgFocus)
                        .focusProperties { up = playlistFocus }
                        .testTag("settings-epg"),
                )
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { viewModel.testSources(playlist, epg) }, enabled = !state.isTestingSources && !state.isSyncing) {
                        if (state.isTestingSources) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Default.CheckCircle, null)
                        Spacer(Modifier.width(7.dp)); Text(stringResource(R.string.test_connection))
                    }
                    Button(onClick = { viewModel.runDiagnostics(playlist, epg) }, colors = ButtonDefaults.buttonColors(containerColor = PanelRaised)) {
                        Icon(Icons.Default.CheckCircle, null); Spacer(Modifier.width(7.dp)); Text(stringResource(R.string.run_diagnostics))
                    }
                    Button(
                        onClick = { viewModel.startRemoteSetup(playlist, epg) },
                        enabled = !state.isTestingSources && !state.isSyncing,
                        colors = ButtonDefaults.buttonColors(containerColor = PanelRaised),
                        modifier = Modifier.testTag("settings-phone"),
                    ) {
                        Icon(Icons.Default.QrCode2, null); Spacer(Modifier.width(7.dp)); Text(stringResource(R.string.phone_setup))
                    }
                }
                if (state.testedSource?.matches(playlist, epg) == true) {
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = viewModel::applyTestedSource, enabled = !state.isSyncing) {
                        Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(7.dp)); Text(stringResource(R.string.apply_tested_source))
                    }
                }
                state.message?.let { Text(it, color = TextMuted, modifier = Modifier.padding(top = 14.dp)) }
                Spacer(Modifier.height(22.dp))
                Text(stringResource(R.string.playback_preferences), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = viewModel::toggleSubtitles, colors = ButtonDefaults.buttonColors(containerColor = PanelRaised)) {
                        Icon(Icons.Default.Subtitles, null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(if (state.subtitlesEnabled) R.string.subtitles_default_on else R.string.subtitles_default_off))
                    }
                    Button(
                        onClick = viewModel::resetAudioPreference,
                        enabled = state.audioLabel != null,
                        colors = ButtonDefaults.buttonColors(containerColor = PanelRaised),
                    ) {
                        Icon(Icons.Default.Headphones, null)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            state.audioLabel?.let { stringResource(R.string.audio_preference_value, it) }
                                ?: stringResource(R.string.audio_preference_auto),
                        )
                    }
                }
                Spacer(Modifier.height(22.dp))
                Text(stringResource(R.string.local_data), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(
                        R.string.epg_last_sync,
                        if (state.lastEpgSyncAt == 0L) stringResource(R.string.never_synced) else formatDateTime(state.lastEpgSyncAt),
                    ),
                    color = TextMuted,
                    fontSize = 16.sp,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { confirmAction = "epg" }, colors = ButtonDefaults.buttonColors(containerColor = PanelRaised)) {
                        Icon(Icons.Default.DeleteSweep, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.clear_epg))
                    }
                    Button(onClick = { confirmAction = "history" }, colors = ButtonDefaults.buttonColors(containerColor = PanelRaised)) {
                        Icon(Icons.Default.History, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.clear_history))
                    }
                    Button(onClick = { confirmAction = "favorites" }, colors = ButtonDefaults.buttonColors(containerColor = PanelRaised)) {
                        Icon(Icons.Default.RestartAlt, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.reset_favorites))
                    }
                }
            }
            Column(Modifier.weight(0.85f).fillMaxHeight()) {
                Text(stringResource(R.string.connection_diagnostics), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(18.dp))
                if (state.diagnostics.isEmpty()) Text(stringResource(R.string.diagnostics_hint), color = TextMuted, fontSize = 18.sp)
                state.diagnostics.forEach { item ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
                        val color = when (item.status) {
                            DiagnosticStatus.OK -> Mint
                            DiagnosticStatus.WARNING -> Warm
                            DiagnosticStatus.ERROR -> ErrorRed
                            DiagnosticStatus.CHECKING -> TextMuted
                        }
                        Icon(if (item.status == DiagnosticStatus.OK) Icons.Default.CheckCircle else Icons.Default.Warning, null, tint = color)
                        Spacer(Modifier.width(10.dp))
                        Column { Text(item.name, fontWeight = FontWeight.SemiBold); Text(item.detail, color = TextMuted, fontSize = 16.sp) }
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.app_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE), color = TextMuted, fontSize = 16.sp)
            }
        }
    }
    state.remoteSetup?.let { session ->
        RemoteSetupDialog(
            session = session,
            canApply = state.testedSource?.matches(playlist, epg) == true,
            onApply = viewModel::applyTestedSource,
            onDismiss = viewModel::stopRemoteSetup,
        )
    }
    when (confirmAction) {
        "epg" -> ConfirmDialog(stringResource(R.string.confirm_clear_epg_title), stringResource(R.string.confirm_clear_epg_detail), {
            confirmAction = null
            viewModel.clearEpg()
        }) { confirmAction = null }
        "history" -> ConfirmDialog(stringResource(R.string.confirm_clear_history_title), stringResource(R.string.confirm_clear_history_detail), {
            confirmAction = null
            viewModel.clearHistory()
        }) { confirmAction = null }
        "favorites" -> ConfirmDialog(stringResource(R.string.confirm_reset_favorites_title), stringResource(R.string.confirm_reset_favorites_detail), {
            confirmAction = null
            viewModel.clearFavorites()
        }) { confirmAction = null }
    }
    LaunchedEffect(Unit) { backFocus.requestFocus() }
}

@Composable
private fun ConfirmDialog(title: String, detail: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(detail) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun formatTime(epochMillis: Long): String = android.text.format.DateFormat.getTimeFormat(LocalContext.current).format(Date(epochMillis))

@Composable
private fun formatDateTime(epochMillis: Long): String {
    val context = LocalContext.current
    val date = android.text.format.DateFormat.getMediumDateFormat(context).format(Date(epochMillis))
    val time = android.text.format.DateFormat.getTimeFormat(context).format(Date(epochMillis))
    return "$date $time"
}
