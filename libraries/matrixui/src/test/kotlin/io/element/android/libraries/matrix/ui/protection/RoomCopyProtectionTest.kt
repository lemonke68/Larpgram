/*
 * Copyright (c) 2026 Larpgram.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */
package io.element.android.libraries.matrix.ui.protection

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.FakeMatrixClient
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RoomCopyProtectionTest {
    @Test
    fun `successful setting writes update visible screens immediately and failed writes retain the previous policy`() = runTest {
        val client = FakeMatrixClient()
        val protection = RoomCopyProtection(client)
        val room = io.element.android.libraries.matrix.test.room.FakeJoinedRoom()
        protection.read(A_ROOM_ID)
        assertThat(protection.allowed.value[A_ROOM_ID]).isTrue()
        protection.set(room, true).getOrThrow()
        assertThat(protection.allowed.value[A_ROOM_ID]).isFalse()
        val failingRoom = io.element.android.libraries.matrix.test.room.FakeJoinedRoom(
            sendRawStateEventResult = { _, _, _ -> Result.failure(IllegalStateException("offline")) },
        )
        assertThat(protection.set(failingRoom, false).isFailure).isTrue()
        assertThat(protection.allowed.value[A_ROOM_ID]).isFalse()
        protection.set(room, false).getOrThrow()
        assertThat(protection.allowed.value[A_ROOM_ID]).isTrue()
    }

    @Test
    fun `policy changes are checked again and errors fail closed`() = runTest {
        var result = Result.success(false)
        val client = FakeMatrixClient().apply { getRoomCopyProtectionLambda = { result } }
        val protection = RoomCopyProtection(client)
        protection.requireAllowed(A_ROOM_ID)
        assertThat(protection.isAllowed(A_ROOM_ID)).isTrue()
        result = Result.success(true)
        assertThat(protection.isAllowed(A_ROOM_ID)).isFalse()
        assertThat(runCatching { protection.requireAllowed(A_ROOM_ID) }.isFailure).isTrue()
        result = Result.failure(IllegalStateException("offline"))
        assertThat(protection.isAllowed(A_ROOM_ID)).isFalse()
        assertThat(runCatching { protection.requireAllowed(A_ROOM_ID) }.isFailure).isTrue()
    }
}
