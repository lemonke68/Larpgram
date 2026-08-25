/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.roomlist

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.CurrentUserMembership
import io.element.android.libraries.matrix.api.roomlist.RoomListService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Правка форка (роумлесс, ф4 блок): автоотклонение будущих инвайтов от заблокированных.
 *
 * Matrix ignoreUser скрывает сообщения, но не мешает игнорируемому слать инвайты. Чтобы блок
 * был TG-стеной, наблюдаем инвайты и молча отклоняем (leave+forget) те, чей пригласивший в
 * ignoredUsers. Наблюдение живёт на session scope и стартует вместе со списком чатов
 * ([RoomListDataSource]), пока приложение в сессии.
 */
@Inject
@SingleIn(SessionScope::class)
class BlockedInviteAutoDecliner(
    private val client: MatrixClient,
    private val roomListService: RoomListService,
    @SessionCoroutineScope
    private val sessionCoroutineScope: CoroutineScope,
) {
    // Чтобы не слать leave повторно, пока первый не убрал комнату из списка инвайтов.
    private val inFlight = ConcurrentHashMap.newKeySet<RoomId>()
    private var started = false

    fun start() {
        if (started) return
        started = true
        combine(
            roomListService.allRooms.summaries,
            client.ignoredUsersFlow,
        ) { summaries, ignored -> summaries to ignored.toSet() }
            .onEach { (summaries, ignored) ->
                if (ignored.isEmpty()) return@onEach
                summaries.forEach { summary ->
                    val info = summary.info
                    val inviter = info.inviter?.userId
                    if (info.currentUserMembership == CurrentUserMembership.INVITED &&
                        inviter != null &&
                        inviter in ignored &&
                        inFlight.add(summary.roomId)
                    ) {
                        sessionCoroutineScope.launch {
                            try {
                                client.getRoom(summary.roomId)?.use { room ->
                                    room.leave().onSuccess { room.forget() }
                                }
                            } finally {
                                inFlight.remove(summary.roomId)
                            }
                        }
                    }
                }
            }
            .launchIn(sessionCoroutineScope)
    }
}
