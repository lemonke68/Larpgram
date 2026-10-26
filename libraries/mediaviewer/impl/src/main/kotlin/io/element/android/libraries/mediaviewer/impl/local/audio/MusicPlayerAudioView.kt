/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.mediaviewer.impl.local.audio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.text.toDp
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.mediaviewer.api.MediaInfo
import io.element.android.libraries.mediaviewer.api.helper.formatFileExtensionAndSize
import io.element.android.libraries.mediaviewer.impl.local.LocalMediaViewState
import io.element.android.libraries.mediaviewer.impl.local.PlayableState
import io.element.android.libraries.mediaviewer.impl.local.player.MediaPlayerControllerState
import io.element.android.libraries.mediaviewer.impl.local.player.MediaPlayerControllerView
import io.element.android.libraries.mediaviewer.impl.music.MusicPagePlayback
import io.element.android.libraries.musicplayer.api.MusicRepeatMode
import io.element.android.libraries.musicplayer.api.MusicTrack
import io.element.android.libraries.musicplayer.api.displayArtist
import io.element.android.libraries.musicplayer.api.displayTitle
import java.io.File

/**
 * Трек в просмотрщике: управляет общим плеером музыки. Свой плеер не заводит, поэтому звук не
 * обрывается ни при уходе с экрана, ни при сворачивании приложения.
 */
@Composable
internal fun MusicPlayerAudioView(
    playback: MusicPagePlayback,
    localMediaViewState: LocalMediaViewState,
    bottomPaddingInPixels: Int,
    info: MediaInfo?,
    modifier: Modifier = Modifier,
) {
    val player = playback.player
    val state by player.state.collectAsState()
    val allTags by player.tags.collectAsState()
    val isCurrent = state.currentTrack?.id == playback.trackId
    val track: MusicTrack? = state.queue.firstOrNull { it.id == playback.trackId }
    val tags = track?.let { allTags[it.mediaKey] }
    var seekingTo by remember { mutableStateOf<Long?>(null) }

    localMediaViewState.isReady = true
    localMediaViewState.playableState = PlayableState.Playable(isShowingControls = true)

    LaunchedEffect(playback.trackId) {
        if (playback.autoPlay) {
            if (!isCurrent) playback.onPlay()
            playback.onAutoPlayHandled()
        }
    }
    LaunchedEffect(state.positionMs, seekingTo) {
        val target = seekingTo
        if (target != null && (!isCurrent || state.positionMs >= target)) seekingTo = null
    }

    val controllerState = MediaPlayerControllerState(
        isVisible = true,
        isPlaying = isCurrent && (state.isPlaying || state.isBuffering),
        isReady = true,
        progressInMillis = if (isCurrent) state.positionMs else 0,
        durationInMillis = if (isCurrent) state.durationMs else 0,
        canMute = false,
        isMuted = false,
        seekingToMillis = seekingTo,
        playbackSpeed = 1f,
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ElementTheme.colors.bgSubtlePrimary),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Artwork(path = tags?.artworkPath)
            Spacer(Modifier.height(24.dp))
            val filename = info?.filename.orEmpty()
            val title = track?.displayTitle(tags) ?: filename
            val artist = track?.displayArtist(tags)
            Text(
                text = title,
                style = ElementTheme.typography.fontHeadingSmMedium,
                color = ElementTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = listOfNotNull(artist, tags?.album).joinToString(" — ")
            if (subtitle.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = ElementTheme.typography.fontBodyLgRegular,
                    color = ElementTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (info != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = formatFileExtensionAndSize(info.fileExtension, info.formattedFileSize),
                    style = ElementTheme.typography.fontBodySmRegular,
                    color = ElementTheme.colors.textSecondary,
                )
            }
            Spacer(Modifier.height(16.dp))
            QueueControls(
                isEnabled = isCurrent,
                shuffle = state.shuffle,
                repeatMode = state.repeatMode,
                hasNext = state.hasNext,
                onShuffle = player::toggleShuffle,
                onPrevious = player::skipToPrevious,
                onNext = player::skipToNext,
                onRepeat = player::cycleRepeatMode,
            )
        }
        MediaPlayerControllerView(
            state = controllerState,
            onTogglePlay = {
                if (isCurrent) player.playPause() else playback.onPlay()
            },
            onSeekChange = {
                if (isCurrent) {
                    seekingTo = it.toLong()
                    player.seekTo(it.toLong())
                }
            },
            onToggleMute = {},
            // Фокус ведёт сам общий плеер.
            audioFocus = null,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomPaddingInPixels.toDp()),
        )
    }
}

@Composable
private fun Artwork(path: String?) {
    val shape = RoundedCornerShape(12.dp)
    if (path != null) {
        AsyncImage(
            model = File(path),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(240.dp)
                .clip(shape),
        )
    } else {
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(shape)
                .background(ElementTheme.colors.bgSubtleSecondary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = CompoundIcons.Audio(),
                contentDescription = null,
                tint = ElementTheme.colors.iconSecondary,
                modifier = Modifier.size(64.dp),
            )
        }
    }
}

@Composable
private fun QueueControls(
    isEnabled: Boolean,
    shuffle: Boolean,
    repeatMode: MusicRepeatMode,
    hasNext: Boolean,
    onShuffle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRepeat: () -> Unit,
) {
    val active = ElementTheme.colors.iconAccentPrimary
    val inactive = ElementTheme.colors.iconSecondary
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onShuffle, enabled = isEnabled) {
            Icon(imageVector = Icons.Filled.Shuffle, contentDescription = null, tint = if (shuffle) active else inactive)
        }
        IconButton(onClick = onPrevious, enabled = isEnabled) {
            Icon(imageVector = Icons.Filled.SkipPrevious, contentDescription = null)
        }
        IconButton(onClick = onNext, enabled = isEnabled && hasNext) {
            Icon(imageVector = Icons.Filled.SkipNext, contentDescription = null)
        }
        IconButton(onClick = onRepeat, enabled = isEnabled) {
            Icon(
                imageVector = if (repeatMode == MusicRepeatMode.One) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                contentDescription = null,
                tint = if (repeatMode == MusicRepeatMode.Off) inactive else active,
            )
        }
    }
}
