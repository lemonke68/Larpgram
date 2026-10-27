/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.musicplayer.impl.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.musicplayer.api.MusicPlayer
import io.element.android.libraries.musicplayer.api.MusicTrack
import io.element.android.libraries.musicplayer.api.MusicTrackTags
import io.element.android.libraries.musicplayer.api.ProfileMusic
import io.element.android.libraries.musicplayer.api.displayArtist
import io.element.android.libraries.musicplayer.api.displayTitle
import io.element.android.libraries.musicplayer.api.profileMusicOwner
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

@Inject
class MusicSheetPresenter(
    private val sessionId: SessionId,
    private val musicPlayer: MusicPlayer,
    private val profileMusic: ProfileMusic,
    private val sharer: MusicTrackSharer,
) : Presenter<MusicSheetState> {
    @Composable
    override fun present(): MusicSheetState {
        val scope = rememberCoroutineScope()
        val player by musicPlayer.state.collectAsState()
        val tags by musicPlayer.tags.collectAsState()
        val ownTracks by profileMusic.ownTracks.collectAsState()
        var profileAction by remember { mutableStateOf<AsyncAction<ProfileButton>>(AsyncAction.Uninitialized) }

        LaunchedEffect(Unit) {
            if (profileMusic.ownTracks.value == null) profileMusic.refreshOwn()
        }

        val isOwnPlaylist = profileMusicOwner(player.queueId) == sessionId
        val currentTrack = player.currentTrack
        val tracks = remember(player.queue, player.currentIndex, tags) {
            player.queue.mapIndexed { index, track -> track.toSheetTrack(tags[track.mediaKey], isCurrent = index == player.currentIndex) }
                .toImmutableList()
        }
        val profileButton = remember(currentTrack, ownTracks) {
            when {
                currentTrack == null -> ProfileButton.Hidden
                profileMusic.isInOwnProfile(currentTrack) -> ProfileButton.Remove
                else -> ProfileButton.Add
            }
        }

        fun changeProfile(track: MusicTrack, add: Boolean, dropFromQueue: Boolean = false) {
            if (profileAction is AsyncAction.Loading) return
            profileAction = AsyncAction.Loading
            scope.launch {
                val trackTags = tags[track.mediaKey]
                val result = if (add) {
                    profileMusic.add(track, title = track.displayTitle(trackTags), artist = track.displayArtist(trackTags))
                } else {
                    profileMusic.remove(track)
                }
                if (result.isSuccess && dropFromQueue) player.queueId?.let { musicPlayer.removeFromQueue(it, track.id) }
                profileAction = result.fold(
                    onSuccess = { AsyncAction.Success(if (add) ProfileButton.Remove else ProfileButton.Add) },
                    onFailure = { AsyncAction.Failure(it) },
                )
            }
        }

        fun handleEvent(event: MusicSheetEvent) {
            when (event) {
                is MusicSheetEvent.Play -> {
                    if (event.trackId == currentTrack?.id) {
                        musicPlayer.playPause()
                    } else {
                        player.queueId?.let { queueId -> musicPlayer.play(queueId, player.queue, event.trackId, player.queueTitle) }
                    }
                }
                MusicSheetEvent.PlayPause -> musicPlayer.playPause()
                MusicSheetEvent.Next -> musicPlayer.skipToNext()
                MusicSheetEvent.Previous -> musicPlayer.skipToPrevious()
                is MusicSheetEvent.Seek -> musicPlayer.seekTo(event.positionMs)
                MusicSheetEvent.CycleSpeed -> musicPlayer.cycleSpeed()
                MusicSheetEvent.CycleOrder -> musicPlayer.cycleOrder()
                MusicSheetEvent.CycleRepeat -> musicPlayer.cycleRepeatMode()
                MusicSheetEvent.ToggleProfile -> currentTrack?.let { changeProfile(it, add = profileButton == ProfileButton.Add) }
                is MusicSheetEvent.RemoveFromProfile -> player.queue.firstOrNull { it.id == event.trackId }?.let {
                    changeProfile(it, add = false, dropFromQueue = isOwnPlaylist)
                }
                MusicSheetEvent.Share -> currentTrack?.let { track -> scope.launch { sharer.share(track) } }
                MusicSheetEvent.ClearProfileAction -> profileAction = AsyncAction.Uninitialized
            }
        }

        return MusicSheetState(
            title = player.queueTitle,
            tracks = tracks,
            current = tracks.getOrNull(player.currentIndex),
            isPlaying = player.isPlaying || player.isBuffering,
            positionMs = player.positionMs,
            durationMs = player.durationMs,
            speed = player.speed,
            order = player.order,
            repeatMode = player.repeatMode,
            hasNext = player.hasNext,
            hasPrevious = player.hasPrevious,
            profileButton = profileButton,
            profileAction = profileAction,
            canRemoveTracks = isOwnPlaylist,
            eventSink = ::handleEvent,
        )
    }
}

internal fun MusicTrack.toSheetTrack(tags: MusicTrackTags?, isCurrent: Boolean) = MusicSheetTrack(
    id = id,
    title = displayTitle(tags),
    artist = displayArtist(tags),
    durationMs = durationMs,
    fileSize = fileSize,
    artworkPath = tags?.artworkPath,
    isCurrent = isCurrent,
)
