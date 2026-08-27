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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Правка форка (роумлесс): свой пин чатов поверх списка.
 *
 * Нативного «закрепить чат в списке» в Matrix нет (RoomPinnedEvents — это закреплённые
 * *сообщения внутри* комнаты, другое). Поэтому держим свой список закреплённых roomId в
 * account data ([ACCOUNT_DATA_TYPE]) — как канал↔дискуссия и «потом» у баннеров. Пресентер
 * поднимает помеченные комнаты наверх списка в порядке этого списка (первый = самый верхний).
 *
 * Порядок: свежий пин встаёт наверх (prepend). Изменение применяется локально сразу
 * (оптимистично), затем пишется на сервер, чтобы список перестраивался без ожидания эха.
 * Кросс-девайс согласованность — на следующем старте: [load] перечитывает account data.
 */
@Inject
@SingleIn(SessionScope::class)
class PinnedChatsStore(
    private val client: MatrixClient,
    @SessionCoroutineScope
    private val sessionCoroutineScope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _pinnedFlow = MutableStateFlow<List<RoomId>>(emptyList())
    val pinnedFlow: StateFlow<List<RoomId>> = _pinnedFlow.asStateFlow()

    init {
        sessionCoroutineScope.launch { load() }
    }

    private suspend fun load() {
        _pinnedFlow.value = decode(client.getAccountData(ACCOUNT_DATA_TYPE).getOrNull())
    }

    fun setPinned(roomId: RoomId, isPinned: Boolean) {
        sessionCoroutineScope.launch {
            val current = _pinnedFlow.value
            val next = if (isPinned) {
                if (roomId in current) current else listOf(roomId) + current
            } else {
                current.filterNot { it == roomId }
            }
            if (next == current) return@launch
            _pinnedFlow.value = next
            client.setAccountData(ACCOUNT_DATA_TYPE, encode(next))
                .onFailure { Timber.w(it, "не удалось сохранить закреплённые чаты") }
        }
    }

    /**
     * Контент account data в Matrix обязан быть JSON-ОБЪЕКТОМ: голый массив сервер отклоняет
     * (PUT падает 400), и пины не сохранялись между сессиями. Поэтому список лежит в поле [pinned].
     */
    @Serializable
    private data class Content(val pinned: List<String> = emptyList())

    private fun decode(raw: String?): List<RoomId> = raw
        ?.let { runCatching { json.decodeFromString<Content>(it).pinned }.getOrNull() }
        .orEmpty()
        .map { RoomId(it) }

    private fun encode(list: List<RoomId>): String = json.encodeToString(Content(list.map { it.value }))

    companion object {
        const val ACCOUNT_DATA_TYPE = "ru.mangokokos.larpgram.pinned_chats"
    }
}
