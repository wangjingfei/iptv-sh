@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.wangjingfei.hometv.ui

import android.view.KeyEvent
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.wangjingfei.hometv.BuildConfig
import com.wangjingfei.hometv.R
import com.wangjingfei.hometv.data.Channel
import com.wangjingfei.hometv.data.DiagnosticStatus
import com.wangjingfei.hometv.data.Programme
import com.wangjingfei.hometv.update.UpdateState
import java.util.Date
import kotlinx.coroutines.flow.first

@Composable
fun HomeTvApp(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(enabled = state.screen != AppScreen.HOME && state.screen != AppScreen.SETUP, onBack = viewModel::navigateBack)
    when (state.screen) {
        AppScreen.SETUP -> SetupScreen(state, viewModel)
        AppScreen.HOME -> HomeScreen(state, viewModel)
        AppScreen.PLAYER -> PlayerScreen(state, viewModel)
        AppScreen.SETTINGS -> SettingsScreen(state, viewModel)
    }
    AppUpdateDialog(state.updateState, viewModel)
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
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
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
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
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
                TvActionButton(
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
                TvActionButton(
                    onClick = { viewModel.startRemoteSetup(playlist, epg) },
                    enabled = !state.isTestingSources && !state.isSyncing,
                    modifier = Modifier.height(52.dp).testTag("setup-phone"),
                ) {
                    Icon(Icons.Default.QrCode2, null)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.phone_setup))
                }
                if (state.testedSource?.matches(playlist, epg) == true) {
                    TvActionButton(onClick = viewModel::applyTestedSource, enabled = !state.isSyncing, modifier = Modifier.height(52.dp)) {
                        if (state.isSyncing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Icon(Icons.Default.LiveTv, null)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.save_and_enter))
                    }
                }
                if (state.channels.isNotEmpty()) TvActionButton(onClick = viewModel::openHome) { Text(stringResource(R.string.use_cache)) }
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
    val programmeFocus = remember { FocusRequester() }
    var previewChannelId by remember { mutableStateOf<String?>(null) }
    var selectedProgramme by remember { mutableStateOf<Programme?>(null) }
    val channelListState = rememberLazyListState()
    val visibleChannels = state.visibleChannels
    val preferredChannelId = state.homeFocusChannelId ?: state.lastChannelId
    val focusIndex = visibleChannels.indexOfFirst { it.id == preferredChannelId }.takeIf { it >= 0 } ?: 0
    Surface(
        color = Ink,
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(horizontal = 44.dp, vertical = 28.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LiveTv, null, tint = Mint, modifier = Modifier.size(34.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.app_name), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    SearchField(state.searchQuery, viewModel::setSearch)
                    Spacer(Modifier.width(12.dp))
                    TvIconButton(onClick = { viewModel.sync() }, enabled = !state.isSyncing) {
                        if (state.isSyncing) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, stringResource(R.string.sync))
                        }
                    }
                    TvIconButton(
                        onClick = viewModel::openSettings,
                        modifier = Modifier.focusRequester(settingsFocus)
                            .focusProperties { down = selectedGroupFocus }
                            .testTag("home-settings"),
                    ) { Icon(Icons.Default.Settings, stringResource(R.string.settings)) }
                }
                Column(Modifier.height(52.dp).padding(top = 4.dp)) {
                    state.message?.let { Text(it, color = TextMuted, fontSize = 18.sp, maxLines = 1) }
                    if (state.lastEpgSyncAt == 0L || System.currentTimeMillis() - state.lastEpgSyncAt > 12 * 60 * 60_000L) {
                        Text(stringResource(R.string.epg_stale), color = Warm, fontSize = 18.sp, maxLines = 1)
                    } else {
                        Text(
                            stringResource(R.string.epg_last_sync, formatDateTime(state.lastEpgSyncAt)),
                            color = TextMuted,
                            fontSize = 18.sp,
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LazyColumn(
                        modifier = Modifier.weight(0.18f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.groups, key = { it }) { group ->
                            GroupButton(
                                label = group,
                                selected = group == state.selectedGroup,
                                onClick = { viewModel.selectGroup(group) },
                                modifier = Modifier.fillMaxWidth()
                                    .then(if (group == state.selectedGroup) Modifier.focusRequester(selectedGroupFocus).testTag("home-selected-group") else Modifier)
                                    .focusProperties {
                                        if (group == state.selectedGroup) up = settingsFocus
                                        if (visibleChannels.isNotEmpty()) right = firstCardFocus
                                    },
                            )
                        }
                    }
                    if (visibleChannels.isEmpty()) {
                        Box(Modifier.weight(0.82f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(if (state.searchQuery.isBlank()) R.string.group_empty else R.string.search_empty),
                                color = TextMuted,
                                fontSize = 20.sp,
                            )
                        }
                    } else {
                        LazyColumn(
                            state = channelListState,
                            modifier = Modifier.weight(0.36f).fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(visibleChannels, key = { it.id }) { channel ->
                                ChannelRow(
                                    channel = channel,
                                    now = state.currentProgramme(channel.id),
                                    next = state.nextProgramme(channel.id),
                                    onPlay = { viewModel.play(channel) },
                                    onFocus = { previewChannelId = channel.id },
                                    focusTarget = channel == visibleChannels[focusIndex],
                                    modifier = Modifier.fillMaxWidth()
                                        .then(
                                            if (channel == visibleChannels[focusIndex]) {
                                                Modifier.focusRequester(firstCardFocus).testTag("home-current-channel")
                                            } else {
                                                Modifier
                                            },
                                        )
                                        .focusProperties {
                                            left = selectedGroupFocus
                                            right = programmeFocus
                                        },
                                )
                            }
                        }
                        val preview = state.channels.firstOrNull { it.id == previewChannelId } ?: visibleChannels[focusIndex]
                        ProgrammePanel(
                            channel = preview,
                            programmes = state.programmes.filter {
                                it.channelId == preview.id && (it.stopAt == null || it.stopAt >= System.currentTimeMillis())
                            },
                            onFavorite = { viewModel.toggleFavorite(preview.id) },
                            onProgrammeClick = { selectedProgramme = it },
                            focusRequester = programmeFocus,
                            channelFocusRequester = firstCardFocus,
                            modifier = Modifier.weight(0.46f),
                        )
                        LaunchedEffect(state.selectedGroup, state.searchQuery, state.channels.size, state.homeFocusRequestVersion) {
                            channelListState.scrollToItem(focusIndex)
                            snapshotFlow { channelListState.layoutInfo.visibleItemsInfo.any { it.index == focusIndex } }
                                .first { it }
                            firstCardFocus.requestFocus()
                        }
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
    selectedProgramme?.let { programme ->
        ProgrammeDetailsDialog(programme = programme, onDismiss = { selectedProgramme = null })
    }
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
        trailingIcon = { if (query.isNotEmpty()) TvIconButton(onClick = { onChange("") }) { Icon(Icons.Default.Close, stringResource(R.string.clear)) } },
        placeholder = { Text(stringResource(R.string.search_channel)) },
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
        singleLine = true,
        modifier = Modifier.width(260.dp).height(54.dp),
    )
}

@Composable
private fun GroupButton(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TvActionButton(
        onClick = onClick,
        modifier = modifier,
        selected = selected,
    ) { Text(label) }
}

@Composable
private fun ChannelRow(
    channel: Channel,
    now: Programme?,
    next: Programme?,
    onPlay: () -> Unit,
    onFocus: () -> Unit,
    focusTarget: Boolean,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.045f else 1f, label = "频道卡片焦点")
    Card(
        onClick = onPlay,
        modifier = modifier.height(102.dp).scale(scale).onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() },
        colors = CardDefaults.cardColors(containerColor = if (focused) PanelRaised else Panel),
        shape = RoundedCornerShape(6.dp),
        border = if (focused) BorderStroke(2.dp, Mint) else null,
    ) {
        Row(
            Modifier.fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag(if (focusTarget) "home-current-channel-${channel.number}" else "home-channel-${channel.number}"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.width(54.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(channel.number.toString().padStart(3, '0'), color = Mint, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (channel.favorite) Icon(Icons.Default.Favorite, stringResource(R.string.favorite), tint = Warm, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(channel.name, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(5.dp))
                Text(now?.title ?: stringResource(R.string.no_epg), color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 18.sp)
                next?.let { Text(stringResource(R.string.next_program, it.title), color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 18.sp) }
            }
        }
    }
}

@Composable
private fun ProgrammePanel(
    channel: Channel,
    programmes: List<Programme>,
    onFavorite: () -> Unit,
    onProgrammeClick: (Programme) -> Unit,
    focusRequester: FocusRequester,
    channelFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxHeight().background(Panel, RoundedCornerShape(6.dp)).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(channel.name, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.programme_guide), color = Mint, fontSize = 18.sp)
            }
            TvIconButton(
                onClick = onFavorite,
                selected = channel.favorite,
                modifier = Modifier.focusRequester(focusRequester)
                    .focusProperties { left = channelFocusRequester }
                    .testTag("home-programme-action"),
            ) {
                Icon(
                    if (channel.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    stringResource(R.string.favorite),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        if (programmes.isEmpty()) {
            Text(stringResource(R.string.no_future_programme), color = TextMuted, fontSize = 18.sp)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(programmes, key = { "${it.channelId}-${it.startAt}" }) { programme ->
                    ProgrammeRow(
                        programme = programme,
                        current = programme.startAt <= System.currentTimeMillis() && (programme.stopAt == null || programme.stopAt > System.currentTimeMillis()),
                        onClick = { onProgrammeClick(programme) },
                        channelFocusRequester = channelFocusRequester,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgrammeRow(
    programme: Programme,
    current: Boolean,
    onClick: () -> Unit,
    channelFocusRequester: FocusRequester,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.025f else 1f, label = "节目焦点")
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
            .focusProperties { left = channelFocusRequester }
            .scale(scale)
            .onFocusChanged { focused = it.isFocused },
        colors = CardDefaults.cardColors(containerColor = if (focused) PanelRaised else Color.Transparent),
        shape = RoundedCornerShape(6.dp),
        border = if (focused) BorderStroke(2.dp, Mint) else null,
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
            Text(formatTime(programme.startAt), color = if (current) Mint else TextMuted, fontSize = 18.sp, modifier = Modifier.width(76.dp))
            Text(
                programme.title,
                color = if (current || focused) TextPrimary else TextMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontSize = 18.sp,
            )
        }
    }
}

@Composable
private fun ProgrammeDetailsDialog(programme: Programme, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(programme.title, fontSize = 24.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (programme.stopAt == null) formatTime(programme.startAt)
                    else stringResource(R.string.programme_time_range, formatTime(programme.startAt), formatTime(programme.stopAt)),
                    color = Mint,
                    fontSize = 18.sp,
                )
                Text(
                    programme.description.ifBlank { stringResource(R.string.no_programme_description) },
                    color = TextMuted,
                    fontSize = 18.sp,
                )
            }
        },
        confirmButton = { TvActionButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        containerColor = Panel,
        shape = RoundedCornerShape(6.dp),
    )
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
        if (state.controlsVisible) {
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
                    TvActionButton(
                        onClick = { viewModel.play(quickChannel) },
                        modifier = if (quickChannel.id == channel.id) {
                            Modifier.focusRequester(focusRequester).testTag("player-current-channel")
                        } else {
                            Modifier
                        },
                        selected = quickChannel.id == channel.id,
                    ) { Text(stringResource(R.string.quick_channel, quickChannel.number, quickChannel.name), maxLines = 1) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(channel.number.toString().padStart(3, '0'), color = Mint, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(16.dp))
                Text(
                    channel.name,
                    color = TextPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TvIconButton(
                    onClick = viewModel::togglePlayback,
                    modifier = Modifier.testTag("player-play-pause"),
                ) {
                    Icon(
                        if (state.isPlaybackActive) Icons.Default.Pause else Icons.Default.PlayArrow,
                        stringResource(if (state.isPlaybackActive) R.string.pause_live else R.string.resume_live),
                    )
                }
                TvIconButton(onClick = { viewModel.toggleFavorite(channel.id) }, selected = channel.favorite) {
                    Icon(
                        if (channel.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        stringResource(R.string.favorite),
                    )
                }
                TvActionButton(onClick = viewModel::cycleAudio) {
                    Icon(Icons.Default.Headphones, null); Spacer(Modifier.width(6.dp)); Text(state.audioLabel ?: stringResource(R.string.default_audio))
                }
                Spacer(Modifier.width(8.dp))
                TvActionButton(onClick = viewModel::toggleSubtitles, selected = state.subtitlesEnabled) {
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
                    TvActionButton(onClick = viewModel::retry) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.retry)) }
                    Spacer(Modifier.width(8.dp))
                    TvActionButton(
                        onClick = viewModel::openDiagnostics,
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
    val testFocus = remember { FocusRequester() }
    val diagnosticsFocus = remember { FocusRequester() }
    val phoneFocus = remember { FocusRequester() }
    val updateFocus = remember { FocusRequester() }
    LaunchedEffect(state.sourceDraftVersion) {
        state.sourceDraft?.let {
            playlist = it.playlistUrl
            epg = it.epgUrl
        }
    }
    Surface(color = Ink, modifier = Modifier.fillMaxSize().testTag("settings-root")) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 44.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(40.dp),
        ) {
            Column(Modifier.weight(1.15f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TvIconButton(
                        onClick = viewModel::openHome,
                        modifier = Modifier.focusRequester(backFocus)
                            .focusProperties { down = playlistFocus }
                            .testTag("settings-back"),
                    ) { Icon(Icons.Default.Home, stringResource(R.string.back_home)) }
                    Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.sources_and_sync), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    playlist,
                    { playlist = it; viewModel.invalidateSourceTest() },
                    label = { Text(stringResource(R.string.playlist_url)) },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
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
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    epg,
                    { epg = it; viewModel.invalidateSourceTest() },
                    label = { Text(stringResource(R.string.xmltv_url)) },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                        .onPreviewKeyEvent { event ->
                            if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                            when (event.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_DPAD_UP -> { playlistFocus.requestFocus(); true }
                                KeyEvent.KEYCODE_DPAD_DOWN -> { testFocus.requestFocus(); true }
                                else -> false
                            }
                        }
                        .focusRequester(epgFocus)
                        .focusProperties {
                            up = playlistFocus
                            down = testFocus
                        }
                        .testTag("settings-epg"),
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvActionButton(
                        onClick = { viewModel.testSources(playlist, epg) },
                        enabled = !state.isTestingSources && !state.isSyncing,
                        modifier = Modifier.focusRequester(testFocus)
                            .focusProperties {
                                up = epgFocus
                                right = diagnosticsFocus
                            }
                            .testTag("settings-test"),
                    ) {
                        if (state.isTestingSources) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Default.CheckCircle, null)
                        Spacer(Modifier.width(7.dp)); Text(stringResource(R.string.test_connection))
                    }
                    TvActionButton(
                        onClick = { viewModel.runDiagnostics(playlist, epg) },
                        modifier = Modifier.focusRequester(diagnosticsFocus)
                            .focusProperties {
                                left = testFocus
                                right = phoneFocus
                            }
                            .testTag("settings-diagnostics"),
                    ) {
                        Icon(Icons.Default.CheckCircle, null); Spacer(Modifier.width(7.dp)); Text(stringResource(R.string.run_diagnostics))
                    }
                    TvActionButton(
                        onClick = { viewModel.startRemoteSetup(playlist, epg) },
                        enabled = !state.isTestingSources && !state.isSyncing,
                        modifier = Modifier.focusRequester(phoneFocus)
                            .focusProperties {
                                left = diagnosticsFocus
                                right = updateFocus
                            }
                            .testTag("settings-phone"),
                    ) {
                        Icon(Icons.Default.QrCode2, null); Spacer(Modifier.width(7.dp)); Text(stringResource(R.string.phone_setup))
                    }
                }
                if (state.testedSource?.matches(playlist, epg) == true) {
                    Spacer(Modifier.height(10.dp))
                    TvActionButton(onClick = viewModel::applyTestedSource, enabled = !state.isSyncing) {
                        Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(7.dp)); Text(stringResource(R.string.apply_tested_source))
                    }
                }
                state.message?.let { Text(it, color = TextMuted, modifier = Modifier.padding(top = 14.dp)) }
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.playback_preferences), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvActionButton(onClick = viewModel::toggleSubtitles, selected = state.subtitlesEnabled) {
                        Icon(Icons.Default.Subtitles, null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(if (state.subtitlesEnabled) R.string.subtitles_default_on else R.string.subtitles_default_off))
                    }
                    TvActionButton(
                        onClick = viewModel::resetAudioPreference,
                        enabled = state.audioLabel != null,
                    ) {
                        Icon(Icons.Default.Headphones, null)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            state.audioLabel?.let { stringResource(R.string.audio_preference_value, it) }
                                ?: stringResource(R.string.audio_preference_auto),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.local_data), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(
                        R.string.epg_last_sync,
                        if (state.lastEpgSyncAt == 0L) stringResource(R.string.never_synced) else formatDateTime(state.lastEpgSyncAt),
                    ),
                    color = TextMuted,
                    fontSize = 18.sp,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvActionButton(onClick = { confirmAction = "epg" }) {
                        Icon(Icons.Default.DeleteSweep, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.clear_epg))
                    }
                    TvActionButton(onClick = { confirmAction = "history" }) {
                        Icon(Icons.Default.History, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.clear_history))
                    }
                    TvActionButton(onClick = { confirmAction = "favorites" }) {
                        Icon(Icons.Default.RestartAlt, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.reset_favorites))
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            Column(Modifier.weight(0.85f).fillMaxHeight()) {
                Text(stringResource(R.string.connection_diagnostics), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                LazyColumn(Modifier.weight(1f)) {
                    if (state.diagnostics.isEmpty()) {
                        item {
                            Text(stringResource(R.string.diagnostics_hint), color = TextMuted, fontSize = 18.sp)
                        }
                    }
                    items(state.diagnostics) { item ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                            val color = when (item.status) {
                                DiagnosticStatus.OK -> Mint
                                DiagnosticStatus.WARNING -> Warm
                                DiagnosticStatus.ERROR -> ErrorRed
                                DiagnosticStatus.CHECKING -> TextMuted
                            }
                            Icon(if (item.status == DiagnosticStatus.OK) Icons.Default.CheckCircle else Icons.Default.Warning, null, tint = color)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(item.name, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                                Text(item.detail, color = TextMuted, fontSize = 18.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                AppUpdatePanel(
                    state.updateState,
                    viewModel,
                    Modifier.focusRequester(updateFocus).focusProperties { left = phoneFocus },
                )
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.app_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE), color = TextMuted, fontSize = 18.sp)
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
private fun AppUpdatePanel(
    updateState: UpdateState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    Text(stringResource(R.string.app_update), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    Text(updateStatusText(updateState), color = TextMuted, fontSize = 18.sp)
    Spacer(Modifier.height(8.dp))
    val checking = updateState is UpdateState.Checking
    val downloading = updateState is UpdateState.Downloading || updateState is UpdateState.Verifying
    TvActionButton(
        onClick = {
            when (updateState) {
                is UpdateState.Available, is UpdateState.Error -> {
                    if (updateState is UpdateState.Error && updateState.apk != null) {
                        viewModel.requestUpdateInstall()
                        return@TvActionButton
                    }
                    val hasDownload = updateState is UpdateState.Available ||
                        (updateState is UpdateState.Error && updateState.manifest != null)
                    if (hasDownload) viewModel.downloadUpdate() else viewModel.checkForUpdate()
                }
                is UpdateState.Ready, is UpdateState.PermissionRequired -> viewModel.requestUpdateInstall()
                else -> viewModel.checkForUpdate()
            }
        },
        enabled = !checking && !downloading,
        modifier = modifier.testTag("settings-update"),
    ) {
        if (checking || downloading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        else Icon(Icons.Default.Refresh, null)
        Spacer(Modifier.width(7.dp))
        Text(
            when (updateState) {
                is UpdateState.Available -> stringResource(R.string.download_update)
                is UpdateState.Error -> when {
                    updateState.apk != null -> stringResource(R.string.retry_install)
                    updateState.manifest != null -> stringResource(R.string.retry_download)
                    else -> stringResource(R.string.check_update)
                }
                is UpdateState.Ready, is UpdateState.PermissionRequired -> stringResource(R.string.install_update)
                is UpdateState.Checking -> stringResource(R.string.checking_update)
                is UpdateState.Downloading, is UpdateState.Verifying -> stringResource(R.string.updating)
                else -> stringResource(R.string.check_update)
            },
        )
    }
}

@Composable
private fun updateStatusText(updateState: UpdateState): String = when (updateState) {
    UpdateState.Idle -> stringResource(R.string.update_not_checked)
    is UpdateState.Checking -> stringResource(R.string.checking_update)
    is UpdateState.UpToDate -> stringResource(R.string.already_latest)
    is UpdateState.Available -> stringResource(R.string.update_available, updateState.manifest.versionName)
    is UpdateState.Downloading -> stringResource(R.string.downloading_update, (updateState.progress * 100).toInt())
    is UpdateState.Verifying -> stringResource(R.string.verifying_update)
    is UpdateState.Ready -> stringResource(R.string.update_ready, updateState.manifest.versionName)
    is UpdateState.PermissionRequired -> stringResource(R.string.install_permission_required)
    is UpdateState.Error -> updateState.message
}

@Composable
private fun AppUpdateDialog(updateState: UpdateState, viewModel: MainViewModel) {
    when (updateState) {
        is UpdateState.Available -> if (updateState.showPrompt) {
            AlertDialog(
                onDismissRequest = viewModel::dismissUpdatePrompt,
                title = { Text(stringResource(R.string.update_available_title, updateState.manifest.versionName)) },
                text = {
                    Column {
                        Text(stringResource(R.string.update_available_detail))
                        if (updateState.manifest.releaseNotes.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Text(updateState.manifest.releaseNotes, color = TextMuted)
                        }
                    }
                },
                confirmButton = { TvActionButton(onClick = viewModel::downloadUpdate) { Text(stringResource(R.string.download_and_install)) } },
                dismissButton = { TvActionButton(onClick = viewModel::dismissUpdatePrompt) { Text(stringResource(R.string.later)) } },
            )
        }
        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.downloading_title, updateState.manifest.versionName)) },
            text = {
                Column {
                    LinearProgressIndicator(progress = { updateState.progress }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.downloading_update, (updateState.progress * 100).toInt()), color = TextMuted)
                }
            },
            confirmButton = {},
        )
        is UpdateState.Verifying -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.verifying_update)) },
            text = { Text(stringResource(R.string.verifying_update_detail)) },
            confirmButton = {},
        )
        is UpdateState.Ready -> if (updateState.showPrompt) AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.update_ready_title)) },
            text = { Text(stringResource(R.string.update_ready_detail, updateState.manifest.versionName)) },
            confirmButton = { TvActionButton(onClick = viewModel::requestUpdateInstall) { Text(stringResource(R.string.install_update)) } },
            dismissButton = { TvActionButton(onClick = viewModel::dismissUpdatePrompt) { Text(stringResource(R.string.later)) } },
        )
        is UpdateState.PermissionRequired -> if (updateState.showPrompt) AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.install_permission_title)) },
            text = { Text(stringResource(R.string.install_permission_detail)) },
            confirmButton = { TvActionButton(onClick = viewModel::requestUpdateInstall) { Text(stringResource(R.string.open_system_settings)) } },
            dismissButton = { TvActionButton(onClick = viewModel::dismissUpdatePrompt) { Text(stringResource(R.string.later)) } },
        )
        is UpdateState.Error -> if (updateState.manifest != null && updateState.showPrompt) {
            AlertDialog(
                onDismissRequest = viewModel::dismissUpdatePrompt,
                title = { Text(stringResource(R.string.update_failed_title)) },
                text = { Text(updateState.message) },
                confirmButton = {
                    TvActionButton(onClick = if (updateState.apk != null) viewModel::requestUpdateInstall else viewModel::downloadUpdate) {
                        Text(stringResource(if (updateState.apk != null) R.string.retry_install else R.string.retry_download))
                    }
                },
                dismissButton = { TvActionButton(onClick = viewModel::dismissUpdatePrompt) { Text(stringResource(R.string.later)) } },
            )
        }
        else -> Unit
    }
}

@Composable
private fun ConfirmDialog(title: String, detail: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(detail) },
        confirmButton = { TvActionButton(onClick = onConfirm) { Text(stringResource(R.string.confirm)) } },
        dismissButton = { TvActionButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
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
