/*
 * Copyright (c) 2026 Larpgram.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */
package io.element.android.libraries.musicplayer.impl

import dev.zacsweers.metro.Inject
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.MatrixClientProvider
import io.element.android.libraries.musicplayer.api.MusicTrack

/** The global player can still contain a track from another logged-in account. */
@Inject
class MusicCopyPolicy(private val clients: MatrixClientProvider) {
    suspend fun sourceClient(track: MusicTrack): MatrixClient = clients.getOrRestore(track.sessionId).getOrThrow()

    suspend fun requireAllowed(track: MusicTrack) {
        val roomId = track.roomId ?: return
        val sourceClient = sourceClient(track)
        check(!sourceClient.getRoomCopyProtection(roomId).getOrThrow()) { "Copying from this channel is restricted" }
    }
}
