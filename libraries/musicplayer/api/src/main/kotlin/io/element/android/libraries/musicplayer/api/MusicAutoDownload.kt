/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.musicplayer.api

import kotlinx.coroutines.flow.StateFlow

/**
 * Автозагрузка треков из чата, как «Автозагрузка медиа» в Telegram: файл качается заранее, чтобы
 * показать теги и обложку в пузыре и играть без ожидания. Трек, который включили сами, качается всегда.
 */
data class MusicAutoDownload(
    val network: Network,
    /** Не качать заранее файлы больше этого размера; null — без ограничения. */
    val maxSizeBytes: Long?,
) {
    enum class Network { Never, WifiOnly, Always }

    companion object {
        val Default = MusicAutoDownload(Network.WifiOnly, maxSizeBytes = 50L * 1024 * 1024)

        /** Варианты ограничения в настройках. */
        val sizeOptions: List<Long?> = listOf(10L, 50L, 100L, 300L).map { it * 1024 * 1024 } + null
    }
}

interface MusicAutoDownloadStore {
    val settings: StateFlow<MusicAutoDownload>
    fun update(settings: MusicAutoDownload)
}
