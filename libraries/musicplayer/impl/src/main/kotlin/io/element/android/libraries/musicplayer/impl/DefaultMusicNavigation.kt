/*
 * Copyright (c) 2026 Larpgram.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */
package io.element.android.libraries.musicplayer.impl

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.musicplayer.api.MusicNavigation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@ContributesBinding(SessionScope::class)
@SingleIn(SessionScope::class)
class DefaultMusicNavigation : MusicNavigation {
    private val events = MutableSharedFlow<MusicNavigation.Request>(extraBufferCapacity = 1)
    override val requests = events.asSharedFlow()
    override fun showInChat(roomId: RoomId, eventId: EventId) {
        events.tryEmit(MusicNavigation.Request(roomId, eventId))
    }
}
