/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.mediaviewer.api

import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.media.MediaSource

/**
 * Тап по аудио в ленте, как в Telegram: просмотрщик не открывается, общий плеер играет музыку
 * комнаты с этого трека (очередь догружается из истории), под шапкой появляется полоса. Тап по
 * играющему треку — пауза / продолжить.
 */
interface RoomMusicPlayback {
    fun playFromTimeline(
        eventId: EventId?,
        mediaSource: MediaSource,
        filename: String,
        mimeType: String?,
        fileSize: Long?,
        durationMs: Long?,
    )
}
