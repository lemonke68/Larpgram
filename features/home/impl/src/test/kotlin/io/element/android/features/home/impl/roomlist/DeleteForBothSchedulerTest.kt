/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.roomlist

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarDispatcher
import io.element.android.libraries.keyescrow.test.FakeKeyEscrowService
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.A_ROOM_ID_2
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DeleteForBothSchedulerTest {
    @Test
    fun `a second delete does not cancel the first one`() = runTest {
        val deleted = mutableListOf<RoomId>()
        val scheduler = createScheduler(deleted)
        scheduler.schedule(A_ROOM_ID)
        advanceTimeBy(1_000)
        scheduler.schedule(A_ROOM_ID_2)
        advanceTimeBy(DELETE_FOR_BOTH_UNDO_MS)
        runCurrent()
        assertThat(deleted).containsExactly(A_ROOM_ID, A_ROOM_ID_2)
    }

    @Test
    fun `cancel stops only that room`() = runTest {
        val deleted = mutableListOf<RoomId>()
        val scheduler = createScheduler(deleted)
        scheduler.schedule(A_ROOM_ID)
        scheduler.schedule(A_ROOM_ID_2)
        scheduler.cancel(A_ROOM_ID)
        advanceTimeBy(DELETE_FOR_BOTH_UNDO_MS + 1)
        runCurrent()
        assertThat(deleted).containsExactly(A_ROOM_ID_2)
    }

    @Test
    fun `scheduling the same room twice deletes it once`() = runTest {
        val deleted = mutableListOf<RoomId>()
        val scheduler = createScheduler(deleted)
        scheduler.schedule(A_ROOM_ID)
        scheduler.schedule(A_ROOM_ID)
        advanceTimeBy(DELETE_FOR_BOTH_UNDO_MS + 1)
        runCurrent()
        assertThat(deleted).containsExactly(A_ROOM_ID)
    }

    private fun TestScope.createScheduler(deleted: MutableList<RoomId>) = DeleteForBothScheduler(
        keyEscrowService = FakeKeyEscrowService(
            deleteDmForBothLambda = { roomId ->
                deleted += roomId
                true
            },
        ),
        snackbarDispatcher = SnackbarDispatcher(),
        sessionCoroutineScope = backgroundScope,
    )
}
