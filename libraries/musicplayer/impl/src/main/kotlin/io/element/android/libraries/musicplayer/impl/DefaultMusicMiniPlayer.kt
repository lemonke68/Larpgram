/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.musicplayer.impl

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.LinearProgressIndicator
import io.element.android.libraries.designsystem.theme.components.Slider
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.media.MediaSource
import io.element.android.libraries.musicplayer.api.MusicMiniPlayer
import io.element.android.libraries.musicplayer.api.MusicPlayer
import io.element.android.libraries.musicplayer.api.MusicPlayerState
import io.element.android.libraries.musicplayer.api.MusicRepeatMode
import io.element.android.libraries.musicplayer.api.MusicTrack
import io.element.android.libraries.musicplayer.api.MusicTrackTags
import io.element.android.libraries.musicplayer.api.displayArtist
import io.element.android.libraries.musicplayer.api.displayTitle
import kotlinx.collections.immutable.persistentListOf

@ContributesBinding(AppScope::class)
class DefaultMusicMiniPlayer(
    private val musicPlayer: MusicPlayer,
) : MusicMiniPlayer {
    @Composable
    override fun Render(modifier: Modifier) {
        val state by musicPlayer.state.collectAsState()
        val tags by musicPlayer.tags.collectAsState()
        AnimatedVisibility(
            visible = state.isActive,
            enter = expandVertically(),
            exit = shrinkVertically(),
            modifier = modifier,
        ) {
            MusicMiniPlayerView(
                state = state,
                tags = state.currentTrack?.let { tags[it.mediaKey] },
                onAction = { action ->
                    when (action) {
                        MiniPlayerAction.PlayPause -> musicPlayer.playPause()
                        MiniPlayerAction.Next -> musicPlayer.skipToNext()
                        MiniPlayerAction.Previous -> musicPlayer.skipToPrevious()
                        MiniPlayerAction.Shuffle -> musicPlayer.toggleShuffle()
                        MiniPlayerAction.Repeat -> musicPlayer.cycleRepeatMode()
                        MiniPlayerAction.Close -> musicPlayer.stop()
                        is MiniPlayerAction.Seek -> musicPlayer.seekTo(action.positionMs)
                    }
                },
            )
        }
    }
}

internal sealed interface MiniPlayerAction {
    data object PlayPause : MiniPlayerAction
    data object Next : MiniPlayerAction
    data object Previous : MiniPlayerAction
    data object Shuffle : MiniPlayerAction
    data object Repeat : MiniPlayerAction
    data object Close : MiniPlayerAction
    data class Seek(val positionMs: Long) : MiniPlayerAction
}

/** Полоса Telegram: play/pause, «Название – Артист», следующий, закрыть; тап — развернуть. */
@Composable
internal fun MusicMiniPlayerView(
    state: MusicPlayerState,
    tags: MusicTrackTags?,
    onAction: (MiniPlayerAction) -> Unit,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
) {
    val track = state.currentTrack ?: return
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val accent = ElementTheme.colors.iconAccentPrimary
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ElementTheme.colors.bgCanvasDefault),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { onAction(MiniPlayerAction.PlayPause) }) {
                Icon(
                    imageVector = if (state.isPlaying || state.isBuffering) CompoundIcons.PauseSolid() else CompoundIcons.PlaySolid(),
                    contentDescription = stringResource(if (state.isPlaying) R.string.larpgram_music_pause else R.string.larpgram_music_play),
                    tint = accent,
                )
            }
            val artist = track.displayArtist(tags)
            val secondary = ElementTheme.colors.textSecondary
            Text(
                text = buildAnnotatedString {
                    append(track.displayTitle(tags))
                    if (artist != null) {
                        withStyle(SpanStyle(color = secondary)) { append(" – $artist") }
                    }
                },
                style = ElementTheme.typography.fontBodyMdMedium,
                color = ElementTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clickable { expanded = !expanded }
                    .padding(vertical = 12.dp),
            )
            IconButton(onClick = { onAction(MiniPlayerAction.Next) }, enabled = state.hasNext) {
                Icon(imageVector = Icons.Filled.SkipNext, contentDescription = stringResource(R.string.larpgram_music_next))
            }
            IconButton(onClick = { onAction(MiniPlayerAction.Close) }) {
                Icon(
                    imageVector = CompoundIcons.Close(),
                    contentDescription = stringResource(R.string.larpgram_music_close),
                    tint = ElementTheme.colors.iconSecondary,
                )
            }
        }
        if (expanded) {
            ExpandedControls(state = state, onAction = onAction)
        } else {
            val progress = if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
            )
        }
        HorizontalDivider()
    }
}

@Composable
private fun ExpandedControls(
    state: MusicPlayerState,
    onAction: (MiniPlayerAction) -> Unit,
) {
    var seekingTo by remember { mutableFloatStateOf(-1f) }
    val progress = if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
    Column(modifier = Modifier.padding(horizontal = 12.dp)) {
        Slider(
            value = if (seekingTo >= 0) seekingTo else progress,
            onValueChange = { seekingTo = it },
            onValueChangeFinish = {
                if (seekingTo >= 0 && state.durationMs > 0) onAction(MiniPlayerAction.Seek((seekingTo * state.durationMs).toLong()))
                seekingTo = -1f
            },
            enabled = state.durationMs > 0,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${formatTime(if (seekingTo >= 0) (seekingTo * state.durationMs).toLong() else state.positionMs)} / ${formatTime(state.durationMs)}",
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textSecondary,
            )
            Text(
                text = stringResource(R.string.larpgram_music_track_position, state.currentIndex + 1, state.queue.size),
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textSecondary,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val active = ElementTheme.colors.iconAccentPrimary
            val inactive = ElementTheme.colors.iconSecondary
            IconButton(onClick = { onAction(MiniPlayerAction.Shuffle) }) {
                Icon(
                    imageVector = Icons.Filled.Shuffle,
                    contentDescription = stringResource(R.string.larpgram_music_shuffle),
                    tint = if (state.shuffle) active else inactive,
                )
            }
            IconButton(onClick = { onAction(MiniPlayerAction.Previous) }) {
                Icon(imageVector = Icons.Filled.SkipPrevious, contentDescription = stringResource(R.string.larpgram_music_previous))
            }
            IconButton(onClick = { onAction(MiniPlayerAction.PlayPause) }) {
                Icon(
                    imageVector = if (state.isPlaying || state.isBuffering) CompoundIcons.PauseSolid() else CompoundIcons.PlaySolid(),
                    contentDescription = stringResource(if (state.isPlaying) R.string.larpgram_music_pause else R.string.larpgram_music_play),
                    tint = active,
                    modifier = Modifier.size(32.dp),
                )
            }
            IconButton(onClick = { onAction(MiniPlayerAction.Next) }, enabled = state.hasNext) {
                Icon(imageVector = Icons.Filled.SkipNext, contentDescription = stringResource(R.string.larpgram_music_next))
            }
            IconButton(onClick = { onAction(MiniPlayerAction.Repeat) }) {
                Icon(
                    imageVector = if (state.repeatMode == MusicRepeatMode.One) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    contentDescription = stringResource(R.string.larpgram_music_repeat),
                    tint = if (state.repeatMode == MusicRepeatMode.Off) inactive else active,
                )
            }
        }
    }
}

internal fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

@PreviewsDayNight
@Composable
internal fun MusicMiniPlayerViewPreview() = ElementPreview {
    val track = MusicTrack(
        id = "\$event",
        sessionId = SessionId("@alice:server.org"),
        roomId = RoomId("!room:server.org"),
        eventId = null,
        mediaSource = MediaSource("mxc://server.org/track"),
        filename = "01 - Daft Punk - Veridis Quo.flac",
        mimeType = "audio/flac",
    )
    Column {
        MusicMiniPlayerView(
            state = MusicPlayerState(queue = persistentListOf(track), currentIndex = 0, isPlaying = true, positionMs = 60_000, durationMs = 345_000),
            tags = null,
            onAction = {},
        )
        MusicMiniPlayerView(
            state = MusicPlayerState(queue = persistentListOf(track), currentIndex = 0, positionMs = 60_000, durationMs = 345_000, shuffle = true),
            tags = MusicTrackTags(title = "Veridis Quo", artist = "Daft Punk"),
            onAction = {},
            initiallyExpanded = true,
        )
    }
}
