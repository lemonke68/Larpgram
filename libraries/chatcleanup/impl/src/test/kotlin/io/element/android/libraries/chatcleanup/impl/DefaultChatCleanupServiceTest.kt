/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatcleanup.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.chatcleanup.api.HISTORY_CLEARED_STATE_TYPE
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarDispatcher
import io.element.android.libraries.keyescrow.test.FakeKeyEscrowService
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.A_ROOM_ID_2
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultChatCleanupServiceTest {
    @Test
    fun `a second delete for both does not cancel the first one`() = runTest {
        val deleted = mutableListOf<RoomId>()
        val service = createService(deleted = deleted)
        service.deleteChatForBoth(A_ROOM_ID)
        advanceTimeBy(1_000)
        service.deleteChatForBoth(A_ROOM_ID_2)
        advanceTimeBy(DELETE_FOR_BOTH_UNDO_MS)
        runCurrent()
        assertThat(deleted).containsExactly(A_ROOM_ID, A_ROOM_ID_2)
    }

    @Test
    fun `cancel stops only that room`() = runTest {
        val deleted = mutableListOf<RoomId>()
        val service = createService(deleted = deleted)
        service.deleteChatForBoth(A_ROOM_ID)
        service.deleteChatForBoth(A_ROOM_ID_2)
        service.cancelDeleteForBoth(A_ROOM_ID)
        advanceTimeBy(DELETE_FOR_BOTH_UNDO_MS + 1)
        runCurrent()
        assertThat(deleted).containsExactly(A_ROOM_ID_2)
    }

    @Test
    fun `deleting the same room twice deletes it once`() = runTest {
        val deleted = mutableListOf<RoomId>()
        val service = createService(deleted = deleted)
        service.deleteChatForBoth(A_ROOM_ID)
        service.deleteChatForBoth(A_ROOM_ID)
        advanceTimeBy(DELETE_FOR_BOTH_UNDO_MS + 1)
        runCurrent()
        assertThat(deleted).containsExactly(A_ROOM_ID)
    }

    @Test
    fun `stored marks are loaded and merged with new ones`() = runTest {
        val saved = mutableListOf<String>()
        val client = FakeMatrixClient(
            getAccountDataLambda = { Result.success("""{"rooms":{"${A_ROOM_ID.value}":100,"not a room id":5}}""") },
            setAccountDataLambda = { _, content ->
                saved += content
                Result.success(Unit)
            },
        )
        val service = createService(client = client)
        runCurrent()
        assertThat(service.clearedHistory.value).containsExactly(A_ROOM_ID, 100L)

        service.clearHistory(A_ROOM_ID_2, upToTs = 200, forBoth = false)
        runCurrent()

        assertThat(service.clearedHistory.value).containsExactly(A_ROOM_ID, 100L, A_ROOM_ID_2, 200L)
        assertThat(saved.last()).isEqualTo("""{"rooms":{"${A_ROOM_ID.value}":100,"${A_ROOM_ID_2.value}":200}}""")
    }

    @Test
    fun `an older mark never moves the cutoff back`() = runTest {
        val service = createService()
        service.clearHistory(A_ROOM_ID, upToTs = 300, forBoth = false)
        service.onClearMarkerSeen(A_ROOM_ID, markerTs = 200)
        runCurrent()
        assertThat(service.clearedHistory.value[A_ROOM_ID]).isEqualTo(300L)
    }

    @Test
    fun `clearing for both sends the room marker`() = runTest {
        val sent = mutableListOf<Pair<String, String>>()
        val client = FakeMatrixClient()
        client.givenGetRoomResult(
            A_ROOM_ID,
            FakeJoinedRoom(
                sendRawStateEventResult = { type, stateKey, _ ->
                    sent += type to stateKey
                    Result.success(Unit)
                },
            ),
        )
        val service = createService(client = client)

        val result = service.clearHistory(A_ROOM_ID, upToTs = 42, forBoth = true)

        assertThat(result.isSuccess).isTrue()
        assertThat(sent).containsExactly(HISTORY_CLEARED_STATE_TYPE to "")
        assertThat(service.clearedHistory.value[A_ROOM_ID]).isEqualTo(42L)
    }

    @Test
    fun `a failed marker keeps the history`() = runTest {
        val client = FakeMatrixClient()
        client.givenGetRoomResult(
            A_ROOM_ID,
            FakeJoinedRoom(sendRawStateEventResult = { _, _, _ -> Result.failure(IllegalStateException("403")) }),
        )
        val service = createService(client = client)

        val result = service.clearHistory(A_ROOM_ID, upToTs = 42, forBoth = true)

        assertThat(result.isFailure).isTrue()
        assertThat(service.clearedHistory.value).isEmpty()
    }

    @Test
    fun `the banner counts down and its undo cancels the delete`() = runTest {
        val deleted = mutableListOf<RoomId>()
        val dispatcher = SnackbarDispatcher()
        val service = createService(deleted = deleted, snackbarDispatcher = dispatcher)
        val before = System.currentTimeMillis()
        service.deleteChatForBoth(A_ROOM_ID)

        val banner = dispatcher.snackbarMessage.first()!!
        assertThat(banner.actionResId).isEqualTo(R.string.larpgram_undo)
        assertThat(banner.countdownTotalMillis).isEqualTo(DELETE_FOR_BOTH_UNDO_MS)
        assertThat(banner.countdownEndsAtMillis!! - before).isAtLeast(DELETE_FOR_BOTH_UNDO_MS)
        banner.action()
        advanceTimeBy(DELETE_FOR_BOTH_UNDO_MS + 1)
        runCurrent()
        assertThat(deleted).isEmpty()
    }

    private fun TestScope.createService(
        client: FakeMatrixClient = FakeMatrixClient(),
        deleted: MutableList<RoomId> = mutableListOf(),
        snackbarDispatcher: SnackbarDispatcher = SnackbarDispatcher(),
    ) = DefaultChatCleanupService(
        client = client,
        keyEscrowService = FakeKeyEscrowService(
            deleteDmForBothLambda = { roomId ->
                deleted += roomId
                true
            },
        ),
        snackbarDispatcher = snackbarDispatcher,
        sessionCoroutineScope = backgroundScope,
    )
}
