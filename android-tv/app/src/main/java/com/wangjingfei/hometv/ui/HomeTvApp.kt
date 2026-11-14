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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.wangjingfei.hometv.data.Channel
import com.wangjingfei.hometv.data.DiagnosticStatus
import com.wangjingfei.hometv.data.Programme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    Surface(color = Ink, modifier = Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize().padding(horizontal = 72.dp, vertical = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(0.9f)) {
                Icon(Icons.Default.LiveTv, contentDescription = null, tint = Mint, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(24.dp))
                Text("家庭电视", fontSize = 42.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text("连接家庭 IPTV 服务", fontSize = 22.sp, color = TextMuted)
                Spacer(Modifier.height(28.dp))
                Text("默认地址已填写，选择“连接并同步”即可开始。", color = TextMuted)
            }
            Column(Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = playlist,
                    onValueChange = { playlist = it },
                    label = { Text("频道列表 URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = epg,
                    onValueChange = { epg = it },
                    label = { Text("XMLTV URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                state.message?.let { Text(it, color = if (state.channels.isEmpty()) ErrorRed else Mint) }
                Button(onClick = { viewModel.sync(playlist, epg) }, enabled = !state.isSyncing, modifier = Modifier.height(52.dp)) {
                    if (state.isSyncing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Refresh, null)
                    Spacer(Modifier.width(10.dp))
                    Text(if (state.isSyncing) "正在连接" else "连接并同步")
                }
                if (state.channels.isNotEmpty()) Button(onClick = viewModel::openHome) { Text("使用本地缓存进入") }
            }
        }
    }
}

@Composable
private fun HomeScreen(state: MainUiState, viewModel: MainViewModel) {
    val firstCardFocus = remember { FocusRequester() }
    var previewChannelId by remember { mutableStateOf<String?>(null) }
    Surface(color = Ink, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(start = 44.dp, end = 44.dp, top = 28.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LiveTv, null, tint = Mint, modifier = Modifier.size(34.dp))
                Spacer(Modifier.width(12.dp))
                Text("家庭电视", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                SearchField(state.searchQuery, viewModel::setSearch)
                Spacer(Modifier.width(12.dp))
                IconButton(onClick = { viewModel.sync() }, enabled = !state.isSyncing) {
                    if (state.isSyncing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Refresh, "同步")
                }
                IconButton(onClick = viewModel::openSettings) { Icon(Icons.Default.Settings, "设置") }
            }
            state.message?.let { Text(it, color = TextMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)) }
            Spacer(Modifier.height(20.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.groups) { group ->
                    GroupButton(group, selected = group == state.selectedGroup) { viewModel.selectGroup(group) }
                }
            }
            Spacer(Modifier.height(18.dp))
            if (state.visibleChannels.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (state.searchQuery.isBlank()) "这个分组暂无频道" else "没有匹配的频道", color = TextMuted, fontSize = 20.sp)
                }
            } else {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(0.72f).fillMaxHeight(),
                    ) {
                        items(state.visibleChannels, key = { it.id }) { channel ->
                            ChannelCard(
                                channel = channel,
                                now = state.currentProgramme(channel.id),
                                next = state.nextProgramme(channel.id),
                                onPlay = { viewModel.play(channel) },
                                onFavorite = { viewModel.toggleFavorite(channel.id) },
                                onFocus = { previewChannelId = channel.id },
                                modifier = if (channel == state.visibleChannels.first()) Modifier.focusRequester(firstCardFocus) else Modifier,
                            )
                        }
                    }
                    val preview = state.channels.firstOrNull { it.id == previewChannelId } ?: state.visibleChannels.first()
                    ProgrammePanel(preview, state.programmes.filter { it.channelId == preview.id && it.stopAt >= System.currentTimeMillis() }, Modifier.weight(0.28f))
                }
                LaunchedEffect(state.selectedGroup, state.searchQuery) { runCatching { firstCardFocus.requestFocus() } }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Default.Close, "清除") } },
        placeholder = { Text("搜索频道") },
        singleLine = true,
        modifier = Modifier.width(260.dp).height(54.dp),
    )
}

@Composable
private fun GroupButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
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
                    Icon(if (channel.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "收藏", tint = if (channel.favorite) Warm else TextMuted)
                }
            }
            Text(channel.name, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Text(now?.title ?: "暂无节目单", color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
            next?.let { Text("接下来  ${it.title}", color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp) }
        }
    }
}

@Composable
private fun ProgrammePanel(channel: Channel, programmes: List<Programme>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxHeight().background(Panel, RoundedCornerShape(6.dp)).padding(18.dp)) {
        Text(channel.name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("节目单", color = Mint, fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))
        if (programmes.isEmpty()) Text("暂无未来节目", color = TextMuted)
        programmes.take(9).forEachIndexed { index, programme ->
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.Top) {
                Text(formatTime(programme.startAt), color = if (index == 0) Mint else TextMuted, fontSize = 13.sp, modifier = Modifier.width(48.dp))
                Text(programme.title, color = if (index == 0) TextPrimary else TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun PlayerScreen(state: MainUiState, viewModel: MainViewModel) {
    val focus = remember { FocusRequester() }
    val channel = state.selectedChannel ?: return
    Box(
        Modifier.fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event -> handlePlayerKey(event.nativeKeyEvent, viewModel) },
    ) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    player = viewModel.playerManager.player
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    keepScreenOn = true
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                }
            },
            update = { it.player = viewModel.playerManager.player },
            modifier = Modifier.fillMaxSize(),
        )
        if (state.playbackState == Player.STATE_BUFFERING) CircularProgressIndicator(Modifier.align(Alignment.Center).size(54.dp), color = Mint)
        AnimatedVisibility(state.controlsVisible, modifier = Modifier.fillMaxSize()) {
            PlayerOverlay(state, viewModel, channel)
        }
        if (state.numericBuffer.isNotEmpty()) {
            Text(state.numericBuffer, fontSize = 68.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopEnd).padding(48.dp))
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

private fun handlePlayerKey(event: KeyEvent, viewModel: MainViewModel): Boolean {
    if (event.action != KeyEvent.ACTION_DOWN) return false
    return when (event.keyCode) {
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> { viewModel.switchChannel(-1); true }
        KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> { viewModel.switchChannel(1); true }
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> { viewModel.showControls(); true }
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MENU -> { viewModel.toggleControls(); true }
        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> { viewModel.enterDigit(event.keyCode - KeyEvent.KEYCODE_0); true }
        else -> false
    }
}

@Composable
private fun PlayerOverlay(state: MainUiState, viewModel: MainViewModel, channel: Channel) {
    Box(Modifier.fillMaxSize().background(Color(0x55000000))) {
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color(0xE6111517)).padding(horizontal = 44.dp, vertical = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(channel.number.toString().padStart(3, '0'), color = Mint, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(16.dp))
                Text(channel.name, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { viewModel.toggleFavorite(channel.id) }) {
                    Icon(if (channel.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "收藏", tint = if (channel.favorite) Warm else TextPrimary)
                }
                Button(onClick = viewModel::cycleAudio, colors = ButtonDefaults.buttonColors(containerColor = PanelRaised)) {
                    Icon(Icons.Default.Headphones, null); Spacer(Modifier.width(6.dp)); Text(state.audioLabel)
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = viewModel::toggleSubtitles, colors = ButtonDefaults.buttonColors(containerColor = PanelRaised)) {
                    Icon(Icons.Default.Subtitles, null); Spacer(Modifier.width(6.dp)); Text(if (state.subtitlesEnabled) "字幕开" else "字幕关")
                }
            }
            val now = state.currentProgramme(channel.id)
            val next = state.nextProgramme(channel.id)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(now?.title ?: "正在直播", fontSize = 19.sp)
                    if (now != null) {
                        Spacer(Modifier.height(7.dp))
                        val progress = ((System.currentTimeMillis() - now.startAt).toFloat() / (now.stopAt - now.startAt).coerceAtLeast(1)).coerceIn(0f, 1f)
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(4.dp), color = Mint)
                    }
                }
                next?.let { Text("接下来 ${formatTime(it.startAt)}  ${it.title}", color = TextMuted, modifier = Modifier.padding(start = 28.dp)) }
            }
            state.playbackError?.let {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = ErrorRed)
                    Spacer(Modifier.width(8.dp)); Text(it, color = ErrorRed, modifier = Modifier.weight(1f))
                    Button(onClick = viewModel::retry) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text("重试") }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(state: MainUiState, viewModel: MainViewModel) {
    var playlist by remember(state.config.playlistUrl) { mutableStateOf(state.config.playlistUrl) }
    var epg by remember(state.config.epgUrl) { mutableStateOf(state.config.epgUrl) }
    Surface(color = Ink, modifier = Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize().padding(44.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            Column(Modifier.weight(1.15f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = viewModel::openHome) { Icon(Icons.Default.Home, "返回首页") }
                    Spacer(Modifier.width(8.dp)); Text("来源与同步", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(24.dp))
                OutlinedTextField(playlist, { playlist = it }, label = { Text("频道列表 URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(epg, { epg = it }, label = { Text("XMLTV URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { viewModel.sync(playlist, epg) }, enabled = !state.isSyncing) {
                        Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(7.dp)); Text("保存并同步")
                    }
                    Button(onClick = { viewModel.runDiagnostics(playlist, epg) }, colors = ButtonDefaults.buttonColors(containerColor = PanelRaised)) {
                        Icon(Icons.Default.CheckCircle, null); Spacer(Modifier.width(7.dp)); Text("运行诊断")
                    }
                }
                state.message?.let { Text(it, color = TextMuted, modifier = Modifier.padding(top = 14.dp)) }
            }
            Column(Modifier.weight(0.85f).fillMaxHeight()) {
                Text("连接诊断", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(18.dp))
                if (state.diagnostics.isEmpty()) Text("运行诊断后显示频道列表、节目单和 HLS 服务的连接结果。", color = TextMuted)
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
                        Column { Text(item.name, fontWeight = FontWeight.SemiBold); Text(item.detail, color = TextMuted, fontSize = 13.sp) }
                    }
                }
                Spacer(Modifier.weight(1f))
                Text("应用版本 0.1.0", color = TextMuted, fontSize = 12.sp)
            }
        }
    }
}

private fun formatTime(epochMillis: Long): String = SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(epochMillis))
