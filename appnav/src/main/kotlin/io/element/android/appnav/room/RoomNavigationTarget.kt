/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.appnav.room

import android.os.Parcelable
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.ThreadId
import io.element.android.libraries.matrix.api.room.JoinedRoom
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

sealed interface RoomNavigationTarget : Parcelable {
    @Parcelize
    data class Root(
        val eventId: EventId? = null,
        @IgnoredOnParcel val joinedRoom: JoinedRoom? = null,
    ) : RoomNavigationTarget

    /** Larpgram: open the room directly in a thread (channel comments), so Back leaves the room in one step. */
    @Parcelize
    data class Thread(
        val threadRootId: ThreadId,
        val focusedEventId: EventId? = null,
    ) : RoomNavigationTarget

    @Parcelize
    data object Details : RoomNavigationTarget

    @Parcelize
    data object NotificationSettings : RoomNavigationTarget
}
