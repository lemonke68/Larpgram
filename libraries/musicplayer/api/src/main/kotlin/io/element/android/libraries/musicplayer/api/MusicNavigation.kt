/*
 * Copyright (c) 2026 Larpgram.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */
package io.element.android.libraries.musicplayer.api

import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.coroutines.flow.Flow

/** Session-local navigation from the player; never routes a different account's track. */
interface MusicNavigation {
    val requests: Flow<Request>
    fun showInChat(roomId: RoomId, eventId: EventId)
    data class Request(val roomId: RoomId, val eventId: EventId)
}
