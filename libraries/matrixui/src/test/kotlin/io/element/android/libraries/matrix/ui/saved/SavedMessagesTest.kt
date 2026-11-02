/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.ui.saved

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SavedMessagesTest {
    @Test
    fun `stored room not loaded locally does not create a duplicate`() = runTest {
        var creations = 0
        val client = FakeMatrixClient(getAccountDataLambda = { Result.success("""{"room_id":"${A_ROOM_ID.value}"}""") })
        client.createRoomResult = {
            creations++
            Result.success(A_ROOM_ID)
        }
        val saved = DefaultSavedMessages(client, testCoroutineDispatchers(), backgroundScope)
        assertThat(saved.getOrCreate().getOrThrow()).isEqualTo(A_ROOM_ID)
        assertThat(creations).isEqualTo(0)
    }

    @Test
    fun `failed account data read does not create a new room`() = runTest {
        var creations = 0
        val client = FakeMatrixClient(getAccountDataLambda = { Result.failure(IllegalStateException("offline")) })
        client.createRoomResult = {
            creations++
            Result.success(A_ROOM_ID)
        }
        val saved = DefaultSavedMessages(client, testCoroutineDispatchers(), backgroundScope)
        assertThat(saved.getOrCreate().isFailure).isTrue()
        assertThat(creations).isEqualTo(0)
    }

    @Test
    fun `retry after failed account data write reuses the created room`() = runTest {
        var creations = 0
        var writes = 0
        val client = FakeMatrixClient(setAccountDataLambda = { _, _ ->
            writes++
            if (writes == 1) Result.failure(IllegalStateException("offline")) else Result.success(Unit)
        })
        client.createRoomResult = {
            creations++
            Result.success(A_ROOM_ID)
        }
        val saved = DefaultSavedMessages(client, testCoroutineDispatchers(), backgroundScope)
        assertThat(saved.getOrCreate().isFailure).isTrue()
        assertThat(saved.getOrCreate().getOrThrow()).isEqualTo(A_ROOM_ID)
        assertThat(creations).isEqualTo(1)
    }

    @Test
    fun `concurrent callers create only one saved room`() = runTest {
        var stored: String? = null
        var creations = 0
        val client = FakeMatrixClient(
            getAccountDataLambda = { Result.success(stored) },
            setAccountDataLambda = { _, content ->
                stored = content
                Result.success(Unit)
            },
        )
        client.createRoomResult = {
            creations++
            Result.success(A_ROOM_ID)
        }
        val saved = DefaultSavedMessages(client, testCoroutineDispatchers(), backgroundScope)
        List(3) { async { saved.getOrCreate().getOrThrow() } }.awaitAll()
        assertThat(creations).isEqualTo(1)
        assertThat(saved.roomId.value).isEqualTo(A_ROOM_ID)
    }
}
