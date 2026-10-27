/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.mediaviewer.test

import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.media.MediaSource
import io.element.android.libraries.mediaviewer.api.RoomMusicPlayback

/** Запоминает, какие треки из ленты попросили сыграть. */
class FakeRoomMusicPlayback : RoomMusicPlayback {
    val played = mutableListOf<EventId?>()

    override fun playFromTimeline(
        eventId: EventId?,
        mediaSource: MediaSource,
        filename: String,
        mimeType: String?,
        fileSize: Long?,
        durationMs: Long?,
    ) {
        played += eventId
    }
}
