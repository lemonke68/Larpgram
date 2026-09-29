/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatcleanup.test

import io.element.android.libraries.chatcleanup.api.ChatCleanupService
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeChatCleanupService(
    initialClearedHistory: Map<RoomId, Long> = emptyMap(),
    private val clearHistoryResult: (RoomId, Long, Boolean) -> Result<Unit> = { _, _, _ -> Result.success(Unit) },
) : ChatCleanupService {
    private val clearedHistoryFlow = MutableStateFlow(initialClearedHistory)
    override val clearedHistory: StateFlow<Map<RoomId, Long>> = clearedHistoryFlow

    val deletedChats = mutableListOf<RoomId>()
    val deletedForBoth = mutableListOf<RoomId>()
    val cancelledDeletes = mutableListOf<RoomId>()

    override fun deleteChat(roomId: RoomId) {
        deletedChats += roomId
    }

    override fun deleteChatForBoth(roomId: RoomId) {
        deletedForBoth += roomId
    }

    override fun cancelDeleteForBoth(roomId: RoomId) {
        cancelledDeletes += roomId
    }

    override suspend fun clearHistory(roomId: RoomId, upToTs: Long, forBoth: Boolean): Result<Unit> =
        clearHistoryResult(roomId, upToTs, forBoth).onSuccess { remember(roomId, upToTs) }

    override fun onClearMarkerSeen(roomId: RoomId, markerTs: Long) = remember(roomId, markerTs)

    private fun remember(roomId: RoomId, ts: Long) {
        val current = clearedHistoryFlow.value
        if ((current[roomId] ?: Long.MIN_VALUE) < ts) clearedHistoryFlow.value = current + (roomId to ts)
    }
}
