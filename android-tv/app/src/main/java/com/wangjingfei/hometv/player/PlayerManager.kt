@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.wangjingfei.hometv.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.wangjingfei.hometv.data.Channel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Singleton
class PlayerManager @Inject constructor(@ApplicationContext context: Context) {
    val player: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        repeatMode = Player.REPEAT_MODE_ONE
        playWhenReady = true
    }
    private val mediaSession = MediaSession.Builder(context, player).build()
    private val _currentChannel = MutableStateFlow<Channel?>(null)
    val currentChannel: StateFlow<Channel?> = _currentChannel

    fun play(channel: Channel) {
        if (_currentChannel.value?.id == channel.id && player.mediaItemCount > 0) {
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            player.play()
            return
        }
        _currentChannel.value = channel
        player.setMediaItem(
            MediaItem.Builder()
                .setMediaId(channel.id)
                .setUri(channel.streamUrl)
                .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(channel.name).build())
                .build(),
        )
        player.prepare()
        player.play()
    }

    fun retry() {
        player.prepare()
        player.play()
    }

    fun cycleAudioLanguage(): String {
        val languages = player.currentTracks.groups
            .filter { it.type == C.TRACK_TYPE_AUDIO }
            .flatMap { group -> (0 until group.mediaTrackGroup.length).mapNotNull { group.mediaTrackGroup.getFormat(it).language } }
            .distinct()
        if (languages.isEmpty()) return "默认音轨"
        val current = player.trackSelectionParameters.preferredAudioLanguages.firstOrNull()
        val next = languages[(languages.indexOf(current).takeIf { it >= 0 }?.plus(1) ?: 0) % languages.size]
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setPreferredAudioLanguage(next)
            .build()
        return next
    }

    fun toggleSubtitles(): Boolean {
        val disabled = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !disabled)
            .build()
        return disabled
    }

    fun pauseForBackground() = player.stop()

    fun release() {
        mediaSession.release()
        player.release()
    }
}
