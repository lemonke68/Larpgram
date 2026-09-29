/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.createroom.impl.selectmembers

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.user.MatrixUser
import kotlinx.collections.immutable.toImmutableList

open class SelectMembersStateProvider : PreviewParameterProvider<SelectMembersState> {
    private val alice = MatrixUser(UserId("@alice:mango-kokos.ru"), displayName = "Alice")
    private val bob = MatrixUser(UserId("@bob:matrix.org"), displayName = "Bob")

    override val values: Sequence<SelectMembersState>
        get() = sequenceOf(
            aSelectMembersState(),
            aSelectMembersState(selected = listOf(alice)),
            aSelectMembersState(query = "zzz", users = emptyList()),
        )

    private fun aSelectMembersState(
        query: String = "",
        users: List<MatrixUser> = listOf(alice, bob),
        selected: List<MatrixUser> = emptyList(),
    ) = SelectMembersState(
        query = TextFieldState(query),
        users = users.map { user ->
            SelectableUser(
                user = user,
                handle = user.userId.value,
                isSelected = selected.any { it.userId == user.userId },
                isUnresolved = false,
            )
        }.toImmutableList(),
        selectedUsers = selected.toImmutableList(),
        isSearching = false,
        eventSink = {},
    )
}
