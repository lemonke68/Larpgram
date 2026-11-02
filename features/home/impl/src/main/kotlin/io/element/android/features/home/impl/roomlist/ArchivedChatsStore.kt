/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.roomlist

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.features.home.impl.R
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarDispatcher
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarMessage
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Правка форка: архив — видимость чата, не выход из комнаты и не выключение уведомлений.
 * Новые сообщения остаются в архиве. Список хранится в account data отдельно для каждого аккаунта;
 * другое устройство получает его при следующем запуске сессии.
 */
@Inject
@SingleIn(SessionScope::class)
class ArchivedChatsStore(
    private val client: MatrixClient,
    @SessionCoroutineScope private val scope: CoroutineScope,
    private val snackbarDispatcher: SnackbarDispatcher,
) {
    private val mutex = Mutex()
    private var loaded = false
    private val json = Json { ignoreUnknownKeys = true }
    @Serializable
    private data class Content(val rooms: List<String> = emptyList())
    private val state = MutableStateFlow<Set<RoomId>>(emptySet())
    val archived = state.asStateFlow()

    suspend fun load() = mutex.withLock { loadLocked() }

    private suspend fun loadLocked() {
        if (loaded) return
        val raw = client.getAccountData(EVENT_TYPE).getOrThrow()
        state.value = raw?.let { json.decodeFromString<Content>(it).rooms.map(::RoomId).toSet() }.orEmpty()
        loaded = true
    }

    fun setArchived(roomId: RoomId, isArchived: Boolean) {
        scope.launch {
            runCatchingExceptions {
                mutex.withLock {
                    loadLocked()
                    val previous = state.value
                    val next = if (isArchived) previous + roomId else previous - roomId
                    if (next == previous) return@withLock
                    state.value = next
                    val result = client.setAccountData(
                        EVENT_TYPE,
                        json.encodeToString(Content(next.map { it.value })),
                    )
                    if (result.isFailure) state.value = previous
                    result.getOrThrow()
                }
            }.onFailure {
                Timber.w(it, "Не удалось изменить архив чатов")
                snackbarDispatcher.post(SnackbarMessage(R.string.larpgram_archive_error))
            }
        }
    }

    companion object {
        const val EVENT_TYPE = "ru.mangokokos.larpgram.archived_chats"
    }
}
