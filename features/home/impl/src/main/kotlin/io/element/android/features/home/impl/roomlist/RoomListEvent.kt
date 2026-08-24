/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.roomlist

import io.element.android.features.home.impl.model.RoomListRoomSummary
import io.element.android.libraries.matrix.api.core.RoomId

sealed interface RoomListEvent {
    data class UpdateVisibleRange(val range: IntRange) : RoomListEvent
    data object DismissRequestVerificationPrompt : RoomListEvent
    data object DismissBanner : RoomListEvent
    data object DismissNewNotificationSoundBanner : RoomListEvent

    /** Правка форка: «потом» на баннере про почту, молчим две недели. */
    data object DismissConnectEmailBanner : RoomListEvent

    /** Правка форка: скрыть баннер про очистку старых сессий. */
    data object DismissCleanUpSessionsBanner : RoomListEvent

    /** Правка форка: «потом» на баннере обновления, молчим до следующей версии. */
    data object DismissUpdateBanner : RoomListEvent
    data object ToggleSearchResults : RoomListEvent
    data class ShowContextMenu(val roomSummary: RoomListRoomSummary) : RoomListEvent

    data class AcceptInvite(val roomSummary: RoomListRoomSummary) : RoomListEvent
    data class DeclineInvite(val roomSummary: RoomListRoomSummary, val blockUser: Boolean) : RoomListEvent
    data class ShowDeclineInviteMenu(val roomSummary: RoomListRoomSummary) : RoomListEvent
    data object HideDeclineInviteMenu : RoomListEvent

    sealed interface ContextMenuEvent : RoomListEvent
    data object HideContextMenu : ContextMenuEvent
    data class LeaveRoom(val roomId: RoomId, val needsConfirmation: Boolean) : ContextMenuEvent
    data class MarkAsRead(val roomId: RoomId) : ContextMenuEvent
    data class MarkAsUnread(val roomId: RoomId) : ContextMenuEvent
    data class SetRoomIsFavorite(val roomId: RoomId, val isFavorite: Boolean) : ContextMenuEvent

    // Telegram-style swipe action: mute/unmute the room's notifications.
    data class SetRoomIsMuted(val roomId: RoomId, val isMuted: Boolean) : ContextMenuEvent
}
