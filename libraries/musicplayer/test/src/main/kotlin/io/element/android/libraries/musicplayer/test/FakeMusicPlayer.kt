/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.musicplayer.test

import io.element.android.libraries.musicplayer.api.MusicPlayer
import io.element.android.libraries.musicplayer.api.MusicPlayerState
import io.element.android.libraries.musicplayer.api.MusicTrack
import io.element.android.libraries.musicplayer.api.MusicTrackTags
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow

/** Запоминает вызовы; play сразу делает очередь текущей. */
class FakeMusicPlayer(
    initialState: MusicPlayerState = MusicPlayerState.Idle,
) : MusicPlayer {
    override val state = MutableStateFlow(initialState)
    override val tags = MutableStateFlow<ImmutableMap<String, MusicTrackTags>>(persistentMapOf())

    data class PlayCall(val queueId: String, val tracks: List<MusicTrack>, val startTrackId: String)

    val playCalls = mutableListOf<PlayCall>()
    val syncCalls = mutableListOf<Pair<String, List<MusicTrack>>>()
    var playPauseCount = 0
    var stopCount = 0
    val seeks = mutableListOf<Long>()

    override fun play(queueId: String, tracks: List<MusicTrack>, startTrackId: String) {
        playCalls += PlayCall(queueId, tracks, startTrackId)
        state.value = MusicPlayerState.Idle.copy(
            queueId = queueId,
            queue = tracks.toImmutableList(),
            currentIndex = tracks.indexOfFirst { it.id == startTrackId },
            isPlaying = true,
        )
    }

    override fun syncQueue(queueId: String, tracks: List<MusicTrack>) {
        syncCalls += queueId to tracks
    }

    override fun playPause() {
        playPauseCount++
        state.value = state.value.copy(isPlaying = !state.value.isPlaying)
    }

    override fun pause() {
        state.value = state.value.copy(isPlaying = false)
    }

    override fun skipToNext() = Unit
    override fun skipToPrevious() = Unit

    override fun seekTo(positionMs: Long) {
        seeks += positionMs
    }

    override fun toggleShuffle() {
        state.value = state.value.copy(shuffle = !state.value.shuffle)
    }

    override fun cycleRepeatMode() = Unit

    override fun stop() {
        stopCount++
        state.value = MusicPlayerState.Idle
    }
}
