/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.roomlist

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.features.home.impl.model.ChatType
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId

open class RoomListStateContextMenuShownProvider : PreviewParameterProvider<RoomListState.ContextMenu.Shown> {
    override val values: Sequence<RoomListState.ContextMenu.Shown>
        get() = sequenceOf(
            aContextMenuShown(hasNewContent = true),
            aContextMenuShown(isDm = true, isFavorite = true),
            aContextMenuShown(roomName = null),
            aContextMenuShown(chatType = ChatType.Channel),
            aContextMenuShown(isPinned = true),
        )
}

internal fun aContextMenuShown(
    roomName: String? = "aRoom",
    isDm: Boolean = false,
    chatType: ChatType = if (isDm) ChatType.Dm else ChatType.Group,
    isPinned: Boolean = false,
    hasNewContent: Boolean = false,
    isFavorite: Boolean = false,
) = RoomListState.ContextMenu.Shown(
    roomId = RoomId("!aRoom:aDomain"),
    roomName = roomName,
    isDm = isDm,
    chatType = chatType,
    isPinned = isPinned,
    dmUserId = if (isDm) UserId("@dm:aDomain") else null,
    hasNewContent = hasNewContent,
    isFavorite = isFavorite,
)
