/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.musicplayer.impl

/**
 * Музыка и «моно-звук» приложения (AudiblePlaybackController): голосовое или кружок со звуком
 * ставят музыку на паузу, после них музыка продолжает сама. Если музыку остановил пользователь,
 * сама она не включится.
 */
internal class MusicFocusArbiter(private val ownToken: Any) {
    enum class Action { None, Pause, Resume }

    var isPausedForOther: Boolean = false
        private set

    /** Владелец звука сменился; [isMusicPlaying] — играет ли музыка (или хочет играть). */
    fun onAudibleChanged(current: Any?, isMusicPlaying: Boolean): Action {
        val isOther = current != null && current != ownToken
        return when {
            isOther && isMusicPlaying -> {
                isPausedForOther = true
                Action.Pause
            }
            !isOther && isPausedForOther -> {
                isPausedForOther = false
                Action.Resume
            }
            else -> Action.None
        }
    }

    /** Пользователь сам нажал play/pause или закрыл плеер — автопродолжение отменяется. */
    fun onUserAction() {
        isPausedForOther = false
    }
}
