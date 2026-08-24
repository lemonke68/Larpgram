/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.model

import io.element.android.libraries.matrix.api.room.RoomInfo

/**
 * Larpgram-level split of Matrix rooms into the three chat kinds the UI actually shows.
 * Everything is a "room" in Matrix, but the client presents these as distinct types with
 * their own screens, actions and creation flows (like Telegram over its own primitives).
 *
 * - [Dm]: a 1:1 direct chat.
 * - [Channel]: a broadcast room where regular members cannot post (only admins), i.e. the
 *   power level required to send a message is above a default member's power.
 * - [Group]: everything else — a normal multi-person room where members can post.
 *
 * Spaces are not chats and are handled separately (`isSpace`).
 */
enum class ChatType {
    Dm,
    Group,
    Channel,
}

/**
 * Classifies a room for the chat list. Cheap: works off the [RoomInfo] the list projection
 * already carries (including power levels), so no extra fetch is needed per row.
 */
fun RoomInfo.chatType(): ChatType = when {
    isDm -> ChatType.Dm
    isBroadcastChannel() -> ChatType.Channel
    else -> ChatType.Group
}

/**
 * True when a default member cannot send messages — the room is configured for broadcast
 * (announcement/channel). Independent of the current user's own role.
 */
private fun RoomInfo.isBroadcastChannel(): Boolean {
    val powerLevels = roomPowerLevels ?: return false
    // `users_default` is 0 in practice; if sending a message needs more than that, only
    // elevated users can post → treat it as a channel.
    return powerLevels.values.eventsDefault > 0
}
