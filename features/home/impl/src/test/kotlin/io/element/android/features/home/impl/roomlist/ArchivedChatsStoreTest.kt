/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.roomlist

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarDispatcher
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.A_ROOM_ID_2
import io.element.android.libraries.matrix.test.FakeMatrixClient
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ArchivedChatsStoreTest {
    @Test
    fun `archive and restore persist across store recreation`() = runTest {
        var stored: String? = null
        val client = FakeMatrixClient(
            getAccountDataLambda = { Result.success(stored) },
            setAccountDataLambda = { type, content ->
                assertThat(type).isEqualTo(ArchivedChatsStore.EVENT_TYPE)
                stored = content
                Result.success(Unit)
            },
        )
        val store = ArchivedChatsStore(client, backgroundScope, SnackbarDispatcher())
        store.setArchived(A_ROOM_ID, true)
        store.setArchived(A_ROOM_ID_2, true)
        runCurrent()
        val restarted = ArchivedChatsStore(client, backgroundScope, SnackbarDispatcher())
        restarted.load()
        assertThat(restarted.archived.value).containsExactly(A_ROOM_ID, A_ROOM_ID_2)
        restarted.setArchived(A_ROOM_ID, false)
        runCurrent()
        val again = ArchivedChatsStore(client, backgroundScope, SnackbarDispatcher())
        again.load()
        assertThat(again.archived.value).containsExactly(A_ROOM_ID_2)
    }

    @Test
    fun `failed write restores previous archive state`() = runTest {
        val client = FakeMatrixClient(setAccountDataLambda = { _, _ -> Result.failure(IllegalStateException("offline")) })
        val store = ArchivedChatsStore(client, backgroundScope, SnackbarDispatcher())
        store.setArchived(A_ROOM_ID, true)
        runCurrent()
        assertThat(store.archived.value).isEmpty()
    }

    @Test
    fun `failed initial read never overwrites remote archive`() = runTest {
        var writes = 0
        val client = FakeMatrixClient(
            getAccountDataLambda = { Result.failure(IllegalStateException("offline")) },
            setAccountDataLambda = { _, _ ->
                writes++
                Result.success(Unit)
            },
        )
        val store = ArchivedChatsStore(client, backgroundScope, SnackbarDispatcher())
        store.setArchived(A_ROOM_ID, true)
        runCurrent()
        assertThat(writes).isEqualTo(0)
        assertThat(store.archived.value).isEmpty()
    }
}
