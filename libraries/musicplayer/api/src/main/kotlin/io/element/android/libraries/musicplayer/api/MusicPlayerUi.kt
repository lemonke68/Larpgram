/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.musicplayer.api

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.element.android.libraries.matrix.api.core.UserId

/**
 * Экраны плеера музыки сессии (как `FragmentContextView` и `AudioPlayerAlert` Telegram).
 */
interface MusicPlayerUi {
    /**
     * Стеклянная пилюля «сейчас играет» под шапкой; тап открывает шторку плеера. Пустая, пока
     * музыка не играет.
     */
    @Composable
    fun NowPlayingBar(modifier: Modifier)

    /** Шторка плеера с текущей очередью. */
    @Composable
    fun PlayerSheet(onDismiss: () -> Unit)

    /**
     * Строка «♫ Трек – Исполнитель ›» в профиле [userId] (как в TG): верхний трек его музыки.
     * Тап запускает плейлист профиля и открывает шторку. Пустая, если музыки в профиле нет.
     */
    @Composable
    fun ProfileMusicRow(userId: UserId, ownerName: String?, modifier: Modifier)

    /** Играть «музыку профиля» [userId] с первого трека; заголовок очереди — «<имя> — плейлист». */
    fun playProfile(userId: UserId, ownerName: String?, tracks: List<ProfileMusicTrack>)
}
