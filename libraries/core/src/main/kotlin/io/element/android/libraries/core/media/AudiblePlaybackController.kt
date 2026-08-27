/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.core.media

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Larpgram: «в один момент звучит только одно» на весь процесс.
 *
 * Голосовые сообщения и кружочки играют звук независимо (голос — через общий MediaPlayer,
 * кружок — через свой ExoPlayer на каждый), поэтому без единой точки несколько могли звучать
 * разом. Тот, кто запускает звук, забирает фокус ([requestFocus]); остальные наблюдают [current]
 * и умолкают, как только перестают им владеть.
 *
 * Токен — любой стабильный ключ звучащего элемента (eventId голосового, mediaSource кружочка).
 * Сравнение по equals, поэтому токены разных типов никогда не совпадут.
 */
object AudiblePlaybackController {
    private val _current = MutableStateFlow<Any?>(null)
    val current: StateFlow<Any?> = _current.asStateFlow()

    /** Забрать аудио-фокус за [token]; любой другой звучащий плеер должен умолкнуть. */
    fun requestFocus(token: Any) {
        _current.value = token
    }

    /** Отдать фокус, если [token] всё ещё им владеет (звук закончился/элемент ушёл с экрана). */
    fun release(token: Any) {
        if (_current.value == token) {
            _current.value = null
        }
    }
}
