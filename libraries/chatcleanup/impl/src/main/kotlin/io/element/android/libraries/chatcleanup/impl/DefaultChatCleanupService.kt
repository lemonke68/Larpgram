/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatcleanup.impl

import androidx.compose.material3.SnackbarDuration
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.chatcleanup.api.ChatCleanupService
import io.element.android.libraries.chatcleanup.api.HISTORY_CLEARED_STATE_TYPE
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarDispatcher
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarMessage
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.keyescrow.api.KeyEscrowService
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/** Окно отмены «удалить у обоих» перед серверным purge. */
internal const val DELETE_FOR_BOTH_UNDO_MS = 5_000L

/**
 * Отложенное «удалить у обоих» живёт на session scope, а не в экране: иначе задача умирала, если
 * за окно отмены открыть чат или сменить вкладку. У каждой комнаты своя задача, отменяет её только
 * «Отменить» на плашке.
 *
 * Отметки очистки истории хранятся в account data [ACCOUNT_DATA_TYPE] одной картой на аккаунт, как
 * закреплённые чаты. Перед записью карта перечитывается и сливается по максимуму, чтобы два
 * устройства не затирали отметки друг друга.
 */
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
class DefaultChatCleanupService(
    private val client: MatrixClient,
    private val keyEscrowService: KeyEscrowService,
    private val snackbarDispatcher: SnackbarDispatcher,
    @SessionCoroutineScope
    private val sessionCoroutineScope: CoroutineScope,
) : ChatCleanupService {
    private val json = Json { ignoreUnknownKeys = true }
    private val pendingDeletes = ConcurrentHashMap<RoomId, Job>()
    private val saveMutex = Mutex()
    private val clearedHistoryFlow = MutableStateFlow<Map<RoomId, Long>>(emptyMap())
    override val clearedHistory: StateFlow<Map<RoomId, Long>> = clearedHistoryFlow.asStateFlow()

    init {
        sessionCoroutineScope.launch {
            val stored = decode(client.getAccountData(ACCOUNT_DATA_TYPE).getOrNull())
            clearedHistoryFlow.update { current -> stored.mergeMax(current) }
        }
    }

    override fun deleteChat(roomId: RoomId) {
        sessionCoroutineScope.launch {
            client.getRoom(roomId)?.use { room ->
                room.leave().onSuccess { room.forget() }
            }
        }
    }

    override fun deleteChatForBoth(roomId: RoomId) {
        if (pendingDeletes.containsKey(roomId)) return
        val job = sessionCoroutineScope.launch {
            delay(DELETE_FOR_BOTH_UNDO_MS)
            if (!keyEscrowService.deleteDmForBoth(roomId)) {
                snackbarDispatcher.post(SnackbarMessage(messageResId = R.string.larpgram_delete_both_failed))
            }
        }
        pendingDeletes[roomId] = job
        job.invokeOnCompletion { pendingDeletes.remove(roomId, job) }
        snackbarDispatcher.post(
            SnackbarMessage(
                messageResId = R.string.larpgram_delete_both_pending,
                actionResId = CommonStrings.action_cancel,
                duration = SnackbarDuration.Long,
                action = { cancelDeleteForBoth(roomId) },
            )
        )
    }

    override fun cancelDeleteForBoth(roomId: RoomId) {
        pendingDeletes.remove(roomId)?.cancel()
    }

    override suspend fun clearHistory(roomId: RoomId, upToTs: Long, forBoth: Boolean): Result<Unit> {
        if (forBoth) {
            val room = client.getJoinedRoom(roomId) ?: return Result.failure(IllegalStateException("room $roomId is not joined"))
            val content = buildJsonObject { put("cleared_by", client.sessionId.value) }.toString()
            room.use { it.sendRawStateEvent(HISTORY_CLEARED_STATE_TYPE, "", content) }
                .onFailure { return Result.failure(it) }
        }
        remember(roomId, upToTs)
        return Result.success(Unit)
    }

    override fun onClearMarkerSeen(roomId: RoomId, markerTs: Long) {
        remember(roomId, markerTs)
    }

    private fun remember(roomId: RoomId, ts: Long) {
        if ((clearedHistoryFlow.value[roomId] ?: Long.MIN_VALUE) >= ts) return
        clearedHistoryFlow.update { it.mergeMax(mapOf(roomId to ts)) }
        sessionCoroutineScope.launch {
            saveMutex.withLock {
                val stored = decode(client.getAccountData(ACCOUNT_DATA_TYPE).getOrNull())
                val merged = stored.mergeMax(clearedHistoryFlow.value)
                if (merged == stored) return@withLock
                client.setAccountData(ACCOUNT_DATA_TYPE, encode(merged))
                    .onFailure { Timber.w(it, "не удалось сохранить отметки очистки истории") }
            }
        }
    }

    /** Контент account data обязан быть JSON-объектом, поэтому карта лежит в поле [rooms]. */
    @Serializable
    private data class Content(val rooms: Map<String, Long> = emptyMap())

    private fun decode(raw: String?): Map<RoomId, Long> = raw
        ?.let { runCatching { json.decodeFromString<Content>(it).rooms }.getOrNull() }
        .orEmpty()
        .mapNotNull { (id, ts) -> runCatching { RoomId(id) }.getOrNull()?.let { it to ts } }
        .toMap()

    private fun encode(map: Map<RoomId, Long>): String = json.encodeToString(Content(map.mapKeys { it.key.value }))

    companion object {
        const val ACCOUNT_DATA_TYPE = "ru.mangokokos.larpgram.cleared_history"
    }
}

private fun Map<RoomId, Long>.mergeMax(other: Map<RoomId, Long>): Map<RoomId, Long> =
    (keys + other.keys).associateWith { maxOf(this[it] ?: Long.MIN_VALUE, other[it] ?: Long.MIN_VALUE) }
