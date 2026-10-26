/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.musicplayer.impl

import android.content.Context
import androidx.core.content.edit
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.di.annotations.ApplicationContext

/** Где остановились в треке, по [io.element.android.libraries.musicplayer.api.MusicTrack.mediaKey]. */
interface TrackPositionStore {
    fun get(key: String): Long?
    fun set(key: String, positionMs: Long)
    fun clear(key: String)
}

/**
 * Как в Telegram: позицию помним только у длинных записей (миксы, подкасты, аудиокниги) —
 * обычную песню после перехода назад логичнее слушать сначала.
 */
internal object PositionPolicy {
    const val MIN_DURATION_MS = 10 * 60 * 1000L
    const val MIN_POSITION_MS = 5_000L
    const val END_MARGIN_MS = 15_000L

    /** null — сохранять нечего (короткий трек, самое начало или почти конец): стереть. */
    fun positionToSave(positionMs: Long, durationMs: Long): Long? {
        if (durationMs < MIN_DURATION_MS) return null
        if (positionMs < MIN_POSITION_MS || durationMs - positionMs < END_MARGIN_MS) return null
        return positionMs
    }

    /** С какой позиции начать трек; 0 — с начала. [durationMs] может быть ещё неизвестен. */
    fun positionToRestore(savedMs: Long?, durationMs: Long?): Long {
        if (savedMs == null || savedMs < MIN_POSITION_MS) return 0
        if (durationMs != null && durationMs > 0 && durationMs - savedMs < END_MARGIN_MS) return 0
        return savedMs
    }
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class SharedPrefsTrackPositionStore(
    @ApplicationContext context: Context,
) : TrackPositionStore {
    private val prefs = context.getSharedPreferences("larpgram_music_positions", Context.MODE_PRIVATE)

    override fun get(key: String): Long? = prefs.getLong(key, -1).takeIf { it >= 0 }

    override fun set(key: String, positionMs: Long) {
        prefs.edit {
            putLong(key, positionMs)
            // Чтобы файл не рос бесконечно: позиции нужны для последних длинных записей.
            if (prefs.all.size > MAX_ENTRIES) {
                prefs.all.keys.filter { it != key }.take(prefs.all.size - MAX_ENTRIES).forEach { remove(it) }
            }
        }
    }

    override fun clear(key: String) {
        if (prefs.contains(key)) prefs.edit { remove(key) }
    }

    private companion object {
        const val MAX_ENTRIES = 200
    }
}
