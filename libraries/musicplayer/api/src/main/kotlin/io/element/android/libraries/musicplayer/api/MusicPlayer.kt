/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.musicplayer.api

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.StateFlow

/**
 * Один общий плеер музыки на всё приложение: играет в фоне (MediaSession + уведомление),
 * очередь — аудио комнаты. Все методы — с главного потока.
 */
interface MusicPlayer {
    val state: StateFlow<MusicPlayerState>

    /** Известные теги по [MusicTrack.mediaKey]. */
    val tags: StateFlow<ImmutableMap<String, MusicTrackTags>>

    /**
     * Играть [tracks] (в порядке ленты) с трека [startTrackId]. Если эта очередь уже играет и
     * трек в ней есть — просто переходит на него, очередь не пересобирается.
     */
    fun play(queueId: String, tracks: List<MusicTrack>, startTrackId: String)

    /** Догрузились ещё треки той же очереди: добавить недостающие, не прерывая звук. */
    fun syncQueue(queueId: String, tracks: List<MusicTrack>)

    fun playPause()
    fun pause()
    fun skipToNext()
    fun skipToPrevious()
    fun seekTo(positionMs: Long)
    fun toggleShuffle()
    fun cycleRepeatMode()

    /** Закрыть плеер: очередь очищается, уведомление уходит. */
    fun stop()
}

enum class MusicRepeatMode { Off, All, One }

@Immutable
data class MusicPlayerState(
    val queueId: String? = null,
    val queue: ImmutableList<MusicTrack> = persistentListOf(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    /** Игрок хочет играть, но ждёт файл / буфер. */
    val isBuffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: MusicRepeatMode = MusicRepeatMode.Off,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val hasError: Boolean = false,
) {
    val currentTrack: MusicTrack? get() = queue.getOrNull(currentIndex)
    val isActive: Boolean get() = currentTrack != null
}
