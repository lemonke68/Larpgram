/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.musicplayer.test

import io.element.android.libraries.musicplayer.api.MusicOfflineStatus
import io.element.android.libraries.musicplayer.api.MusicOrder
import io.element.android.libraries.musicplayer.api.MusicPlayer
import io.element.android.libraries.musicplayer.api.MusicPlayerState
import io.element.android.libraries.musicplayer.api.MusicRepeatMode
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

    data class PlayCall(val queueId: String, val tracks: List<MusicTrack>, val startTrackId: String, val title: String? = null)

    val playCalls = mutableListOf<PlayCall>()
    val syncCalls = mutableListOf<Pair<String, List<MusicTrack>>>()
    var playPauseCount = 0
    var stopCount = 0
    val seeks = mutableListOf<Long>()

    override fun play(queueId: String, tracks: List<MusicTrack>, startTrackId: String, title: String?) {
        playCalls += PlayCall(queueId, tracks, startTrackId, title)
        state.value = MusicPlayerState.Idle.copy(
            queueId = queueId,
            queueTitle = title,
            queue = tracks.toImmutableList(),
            currentIndex = tracks.indexOfFirst { it.id == startTrackId },
            isPlaying = true,
        )
    }

    override fun syncQueue(queueId: String, tracks: List<MusicTrack>) {
        syncCalls += queueId to tracks
    }

    val removeCalls = mutableListOf<Pair<String, String>>()

    override fun removeFromQueue(queueId: String, trackId: String) {
        removeCalls += queueId to trackId
    }

    override fun playPause() {
        playPauseCount++
        state.value = state.value.copy(isPlaying = !state.value.isPlaying)
    }

    override fun pause() {
        state.value = state.value.copy(isPlaying = false)
    }

    var nextCount = 0
    var previousCount = 0

    override fun skipToNext() {
        nextCount++
    }

    override fun skipToPrevious() {
        previousCount++
    }

    override fun seekTo(positionMs: Long) {
        seeks += positionMs
    }

    override fun cycleOrder() {
        val next = MusicOrder.entries[(state.value.order.ordinal + 1) % MusicOrder.entries.size]
        state.value = state.value.copy(order = next)
    }

    override fun cycleRepeatMode() {
        val next = MusicRepeatMode.entries[(state.value.repeatMode.ordinal + 1) % MusicRepeatMode.entries.size]
        state.value = state.value.copy(repeatMode = next)
    }

    override fun cycleSpeed() {
        state.value = state.value.copy(speed = if (state.value.speed >= 2f) 1f else state.value.speed + 0.5f)
    }

    override fun stop() {
        stopCount++
        state.value = MusicPlayerState.Idle
    }

    override val offline = MutableStateFlow<ImmutableMap<String, MusicOfflineStatus>>(persistentMapOf())
    val savedOnDeviceCalls = mutableListOf<Pair<List<MusicTrack>, Boolean>>()

    override fun setSavedOnDevice(tracks: List<MusicTrack>, save: Boolean) {
        savedOnDeviceCalls += tracks to save
    }
}
