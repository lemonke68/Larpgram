/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.musicplayer.api

import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.media.MediaSource

internal fun aMusicTrack(
    id: String = "\$track",
    filename: String = "track.mp3",
) = MusicTrack(
    id = id,
    sessionId = SessionId("@alice:server.org"),
    roomId = RoomId("!room:server.org"),
    eventId = null,
    mediaSource = MediaSource("mxc://server.org/$id"),
    filename = filename,
    mimeType = "audio/mpeg",
)
