/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.zacsweers.metro.Inject
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.MatrixClientProvider
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.totalUnreadCountFlow
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.matrix.api.user.toMatrixUser
import io.element.android.libraries.sessionstorage.api.SessionData
import io.element.android.libraries.sessionstorage.api.SessionStore
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn

/** One row of the account switcher popover. */
@Immutable
data class TgAccount(
    val user: MatrixUser,
    val unreadCount: Int,
    val isCurrent: Boolean,
)

/**
 * All signed-in accounts for the long-press account switcher on the Профиль tab, in the order the
 * user added them, with the unread count of each (the current account included, though the
 * popover only shows it for the other ones).
 */
@Inject
class TgAccountsPresenter(
    private val matrixClient: MatrixClient,
    private val sessionStore: SessionStore,
    private val matrixClientProvider: MatrixClientProvider,
    private val coroutineDispatchers: CoroutineDispatchers,
) : Presenter<ImmutableList<TgAccount>> {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Composable
    override fun present(): ImmutableList<TgAccount> {
        val accounts by remember {
            sessionStore.sessionsFlow()
                .flatMapLatest { sessions ->
                    if (sessions.isEmpty()) {
                        flowOf(persistentListOf())
                    } else {
                        combine(sessions.sortedBy { it.position }.map(::accountFlow)) { it.toImmutableList() }
                    }
                }
        }.collectAsState(initial = persistentListOf())
        return accounts
    }

    private fun accountFlow(sessionData: SessionData): Flow<TgAccount> = flow {
        val user = sessionData.toMatrixUser()
        val isCurrent = sessionData.userId == matrixClient.sessionId.value
        val client = if (isCurrent) matrixClient else matrixClientProvider.getOrNull(SessionId(sessionData.userId))
        if (client == null) {
            emit(TgAccount(user = user, unreadCount = 0, isCurrent = false))
        } else {
            emitAll(
                combine(client.userProfile, client.totalUnreadCountFlow().flowOn(coroutineDispatchers.io)) { profile, unread ->
                    TgAccount(
                        // The current profile is the freshest; for the others keep the stored avatar thumbnail.
                        user = if (isCurrent) profile else user,
                        unreadCount = unread,
                        isCurrent = isCurrent,
                    )
                }
            )
        }
    }
}
