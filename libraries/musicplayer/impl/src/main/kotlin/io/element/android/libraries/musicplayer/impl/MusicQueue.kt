/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.musicplayer.impl

import io.element.android.libraries.musicplayer.api.MusicOrder
import io.element.android.libraries.musicplayer.api.MusicRepeatMode
import io.element.android.libraries.musicplayer.api.MusicTrack

/** Чистая логика очереди, без плеера. */
internal object MusicQueue {
    data class Built(val tracks: List<MusicTrack>, val startIndex: Int)

    /** Вставка [tracks] перед позицией [index] текущего плейлиста. */
    data class Insert(val index: Int, val tracks: List<MusicTrack>)

    /** Без дублей по id (первое вхождение), старт — с [startTrackId] или с начала. */
    fun build(tracks: List<MusicTrack>, startTrackId: String): Built {
        val unique = tracks.distinctBy { it.id }
        val start = unique.indexOfFirst { it.id == startTrackId }.coerceAtLeast(0)
        return Built(unique, start)
    }

    /**
     * Какие треки из [desired] вставить в [current] (ids плейлиста) и куда, чтобы порядок
     * совпал с [desired]. Уже стоящие треки не трогаем: текущий не прерывается.
     */
    fun inserts(current: List<String>, desired: List<MusicTrack>): List<Insert> {
        val working = current.toMutableList()
        val result = mutableListOf<Insert>()
        var position = 0
        for (track in desired.distinctBy { it.id }) {
            val existing = working.indexOf(track.id)
            if (existing >= 0) {
                position = existing + 1
                continue
            }
            working.add(position, track.id)
            val last = result.lastOrNull()
            if (last != null && last.index + last.tracks.size == position) {
                result[result.lastIndex] = last.copy(tracks = last.tracks + track)
            } else {
                result.add(Insert(position, listOf(track)))
            }
            position++
        }
        return result
    }

    /** Порядок кнопки повтора как в Telegram: выкл → вся очередь → один трек. */
    fun nextRepeatMode(mode: MusicRepeatMode): MusicRepeatMode = when (mode) {
        MusicRepeatMode.Off -> MusicRepeatMode.All
        MusicRepeatMode.All -> MusicRepeatMode.One
        MusicRepeatMode.One -> MusicRepeatMode.Off
    }

    /** Кнопка ↑↓: обычный → обратный → перемешать → обычный. */
    fun nextOrder(order: MusicOrder): MusicOrder = when (order) {
        MusicOrder.Default -> MusicOrder.Reversed
        MusicOrder.Reversed -> MusicOrder.Shuffle
        MusicOrder.Shuffle -> MusicOrder.Default
    }

    /** Скорости кнопки в полосе Telegram (`FragmentContextView.toggleSpeeds`). */
    val speeds = listOf(1f, 1.5f, 2f)

    /** Следующая скорость по кругу, как у Telegram: нестандартная — к следующей большей. */
    fun nextSpeed(speed: Float): Float {
        val index = speeds.indexOfFirst { speed - SPEED_EPSILON <= it }
        return speeds.getOrElse(index + 1) { speeds.first() }
    }

    private const val SPEED_EPSILON = 0.1f
}
