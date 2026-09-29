/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.createroom.impl.selectmembers

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.room.recent.getRecentDirectRooms
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.matrix.ui.model.tgHandle
import io.element.android.libraries.usersearch.api.UserRepository
import io.element.android.libraries.usersearch.api.UserSearchResult
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList

private const val MAX_RECENT_USERS = 50

@Inject
class SelectMembersPresenter(
    private val matrixClient: MatrixClient,
    private val userRepository: UserRepository,
) : Presenter<SelectMembersState> {
    @Composable
    override fun present(): SelectMembersState {
        val query = rememberTextFieldState()
        var selectedUsers by remember { mutableStateOf(persistentListOf<MatrixUser>()) }
        val recentUsers by produceState(emptyList<MatrixUser>()) {
            value = matrixClient.getRecentDirectRooms()
                .map { it.matrixUser }
                .take(MAX_RECENT_USERS)
                .toList()
        }
        var searchResults by remember { mutableStateOf<List<UserSearchResult>>(emptyList()) }
        var isSearching by remember { mutableStateOf(false) }
        val queryText = query.text.toString().trim()
        LaunchedEffect(queryText) {
            searchResults = emptyList()
            isSearching = false
            if (queryText.isEmpty()) return@LaunchedEffect
            userRepository.search(queryText).collect { state ->
                isSearching = state.isSearching
                searchResults = state.results
            }
        }

        val listed = if (queryText.isEmpty()) {
            recentUsers.map { UserSearchResult(it) }
        } else {
            searchResults
        }
        val ownServer = remember { matrixClient.userIdServerName() }
        val users = listed.map { result ->
            SelectableUser(
                user = result.matrixUser,
                handle = result.matrixUser.userId.tgHandle(ownServer),
                isSelected = selectedUsers.any { it.userId == result.matrixUser.userId },
                isUnresolved = result.isUnresolved,
            )
        }.toImmutableList()

        fun handleEvent(event: SelectMembersEvent) {
            when (event) {
                is SelectMembersEvent.ToggleUser -> {
                    val existing = selectedUsers.firstOrNull { it.userId == event.user.userId }
                    selectedUsers = if (existing != null) selectedUsers.remove(existing) else selectedUsers.add(event.user)
                }
            }
        }

        return SelectMembersState(
            query = query,
            users = users,
            selectedUsers = selectedUsers,
            isSearching = isSearching,
            eventSink = ::handleEvent,
        )
    }
}
