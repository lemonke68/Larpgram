/*
 * Правка форка: «печатает…» в строке списка чатов, как в Telegram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.home.impl.roomlist

import dev.zacsweers.metro.Inject
import io.element.android.features.home.impl.model.TypingPreview
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Подписывается на уведомления о наборе только для чатов, видимых на экране: подписка на каждую
 * комнату аккаунта была бы лишней работой. Имена печатающих берёт из участников комнаты и кэширует.
 */
@Inject
class RoomListTypingTracker(
    private val client: MatrixClient,
) {
    private val _typing = MutableStateFlow<Map<RoomId, TypingPreview>>(emptyMap())
    val typing: StateFlow<Map<RoomId, TypingPreview>> = _typing.asStateFlow()

    private val jobs = mutableMapOf<RoomId, Job>()
    private val names = mutableMapOf<UserId, String>()

    /** [rooms] — видимые чаты и признак ЛС (в ЛС имени не пишем). */
    fun track(scope: CoroutineScope, rooms: Map<RoomId, Boolean>) {
        (jobs.keys - rooms.keys).forEach { roomId ->
            jobs.remove(roomId)?.cancel()
            _typing.update { it - roomId }
        }
        rooms.forEach { (roomId, isDm) ->
            // Экран списка могли покинуть: старая подписка тогда отменена вместе с его scope.
            if (jobs[roomId]?.isActive == true) return@forEach
            jobs[roomId] = scope.launch {
                client.roomTypingMembersFlow(roomId).distinctUntilChanged().collect { userIds ->
                    val preview = when {
                        userIds.isEmpty() -> null
                        isDm -> TypingPreview(names = emptyList<String>().toImmutableList())
                        else -> TypingPreview(names = userIds.map { displayName(roomId, it) }.toImmutableList())
                    }
                    _typing.update { current -> if (preview == null) current - roomId else current + (roomId to preview) }
                }
            }
        }
    }

    private suspend fun displayName(roomId: RoomId, userId: UserId): String = names.getOrPut(userId) {
        val name = client.getRoom(roomId)?.use { room -> room.userDisplayName(userId).getOrNull() }
        // Как в TG: в строке списка — только имя, без фамилии.
        (name ?: userId.extractedDisplayName).substringBefore(' ')
    }
}
