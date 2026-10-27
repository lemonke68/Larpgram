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
 * очередь — аудио комнаты или музыка профиля. Все методы — с главного потока.
 *
 * Очередь хранится в порядке показа, как в Telegram: от новых к старым. «Следующий» по умолчанию
 * — трек ниже в списке (старее), [MusicOrder.Reversed] играет вверх, к новым.
 */
interface MusicPlayer {
    val state: StateFlow<MusicPlayerState>

    /** Известные теги по [MusicTrack.mediaKey]. */
    val tags: StateFlow<ImmutableMap<String, MusicTrackTags>>

    /**
     * Играть [tracks] (от новых к старым) с трека [startTrackId]. Если эта очередь уже играет и
     * трек в ней есть — просто переходит на него, очередь не пересобирается.
     *
     * @param title заголовок шторки плеера для этой очереди (плейлист профиля), null — без него.
     */
    fun play(queueId: String, tracks: List<MusicTrack>, startTrackId: String, title: String? = null)

    /** Догрузились ещё треки той же очереди: добавить недостающие, не прерывая звук. */
    fun syncQueue(queueId: String, tracks: List<MusicTrack>)

    /** Убрать трек из очереди [queueId] (его убрали из своего профиля). Текущий не трогаем. */
    fun removeFromQueue(queueId: String, trackId: String)

    fun playPause()
    fun pause()
    fun skipToNext()
    fun skipToPrevious()
    fun seekTo(positionMs: Long)

    /** Порядок как кнопка ↑↓ Telegram: обычный → обратный → перемешать → обычный. */
    fun cycleOrder()
    fun cycleRepeatMode()

    /** Скорость 1X → 1.5X → 2X → 1X, как у кнопки скорости Telegram. Запоминается. */
    fun cycleSpeed()

    /** Закрыть плеер: очередь очищается, уведомление уходит. */
    fun stop()

    /** Треки, сохранённые на устройстве (или сохраняемые сейчас), по [MusicTrack.mediaKey]. */
    val offline: StateFlow<ImmutableMap<String, MusicOfflineStatus>>

    /**
     * Сохранить треки на устройстве для прослушивания без сети или убрать их. Это отдельное
     * хранилище, не общий кэш медиа: система его не чистит, удаляет только пользователь.
     */
    fun setSavedOnDevice(tracks: List<MusicTrack>, save: Boolean)
}

enum class MusicOfflineStatus { Saving, Saved }

enum class MusicRepeatMode { Off, All, One }

enum class MusicOrder {
    /** Вниз по списку: от новых к старым. */
    Default,

    /** Вверх по списку: от старых к новым. */
    Reversed,
    Shuffle,
}

@Immutable
data class MusicPlayerState(
    val queueId: String?,
    /** Заголовок очереди (плейлист профиля) или null. */
    val queueTitle: String?,
    val queue: ImmutableList<MusicTrack>,
    val currentIndex: Int,
    val isPlaying: Boolean,
    /** Игрок хочет играть, но ждёт файл / буфер. */
    val isBuffering: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val order: MusicOrder,
    val repeatMode: MusicRepeatMode,
    val speed: Float,
    val hasNext: Boolean,
    val hasPrevious: Boolean,
    val hasError: Boolean,
) {
    val currentTrack: MusicTrack? get() = queue.getOrNull(currentIndex)
    val isActive: Boolean get() = currentTrack != null

    companion object {
        /** Ничего не играет, очереди нет. */
        val Idle = MusicPlayerState(
            queueId = null,
            queueTitle = null,
            queue = persistentListOf(),
            currentIndex = -1,
            isPlaying = false,
            isBuffering = false,
            positionMs = 0,
            durationMs = 0,
            order = MusicOrder.Default,
            repeatMode = MusicRepeatMode.Off,
            speed = 1f,
            hasNext = false,
            hasPrevious = false,
            hasError = false,
        )
    }
}
