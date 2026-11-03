/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.roomlist

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.A_ROOM_ID_2
import io.element.android.libraries.matrix.test.FakeMatrixClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PinnedChatsStoreTest {
    @Test
    fun `pin during initial read merges with stored pins instead of being overwritten`() = runTest {
        val read = CompletableDeferred<Unit>()
        var stored = """{"pinned":["${A_ROOM_ID.value}"]}"""
        val fakeClient = FakeMatrixClient(
            setAccountDataLambda = { _, content ->
                stored = content
                Result.success(Unit)
            },
        )
        val client = object : io.element.android.libraries.matrix.api.MatrixClient by fakeClient {
            override suspend fun getAccountData(eventType: String): Result<String?> {
                read.await()
                return Result.success(stored)
            }
        }
        val store = PinnedChatsStore(client, backgroundScope)
        runCurrent()
        store.setPinned(A_ROOM_ID_2, true)
        runCurrent()
        read.complete(Unit)
        runCurrent()
        assertThat(store.pinnedFlow.value).containsExactly(A_ROOM_ID_2, A_ROOM_ID).inOrder()
        assertThat(stored).contains(A_ROOM_ID_2.value)
    }
}
