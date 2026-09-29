/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.createroom.impl.selectmembers

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.usersearch.api.UserSearchResult
import io.element.android.libraries.usersearch.api.UserSearchResultState
import io.element.android.libraries.usersearch.test.FakeUserRepository
import io.element.android.tests.testutils.test
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SelectMembersPresenterTest {
    private val local = MatrixUser(UserId("@alice:matrix.org"), displayName = "Alice")
    private val remote = MatrixUser(UserId("@bob:other.org"), displayName = "Bob")

    @Test
    fun `search results are listed with a short handle for our own server`() = runTest {
        val repository = FakeUserRepository()
        SelectMembersPresenter(FakeMatrixClient(userIdServerNameLambda = { "matrix.org" }), repository).test {
            val initial = awaitItem()
            assertThat(initial.users).isEmpty()
            initial.query.setTextAndPlaceCursorAtEnd("a")
            skipItems(1)
            repository.emitState(UserSearchResultState(results = listOf(UserSearchResult(local), UserSearchResult(remote)), isSearching = false))
            val results = expectMostRecentItemMatching { it.users.size == 2 }
            assertThat(results.users.map { it.handle }).containsExactly("@alice", "@bob:other.org").inOrder()
            assertThat(repository.providedQuery).isEqualTo("a")
        }
    }

    @Test
    fun `toggling a user selects and deselects them`() = runTest {
        SelectMembersPresenter(FakeMatrixClient(userIdServerNameLambda = { "matrix.org" }), FakeUserRepository()).test {
            val initial = awaitItem()
            initial.eventSink(SelectMembersEvent.ToggleUser(local))
            val selected = expectMostRecentItemMatching { it.selectedUsers.isNotEmpty() }
            assertThat(selected.selectedUsers).containsExactly(local)
            selected.eventSink(SelectMembersEvent.ToggleUser(local))
            assertThat(expectMostRecentItemMatching { it.selectedUsers.isEmpty() }.selectedUsers).isEmpty()
        }
    }

    private suspend fun app.cash.turbine.TurbineTestContext<SelectMembersState>.expectMostRecentItemMatching(
        predicate: (SelectMembersState) -> Boolean,
    ): SelectMembersState {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }
}
