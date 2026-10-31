/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.roomlist

/** Preserve measured rows through the SDK's transient empty filter batch, never a final empty result. */
internal fun roomListContentDuringFolderSwitch(
    live: RoomListContentState,
    cached: RoomListContentState?,
    isContentPending: Boolean,
): RoomListContentState = if (
    isContentPending && live is RoomListContentState.Rooms && live.summaries.isEmpty() && cached is RoomListContentState.Rooms
) {
    cached
} else {
    live
}
