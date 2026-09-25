/*
 * Правка форка: «Избранное» — чат с самим собой, как Saved Messages в Telegram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.matrix.ui.saved

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.createroom.CreateRoomParameters
import io.element.android.libraries.matrix.api.createroom.RoomPreset
import io.element.android.libraries.matrix.api.room.CurrentUserMembership
import io.element.android.libraries.matrix.api.room.history.RoomHistoryVisibility
import io.element.android.libraries.matrix.api.roomdirectory.RoomVisibility
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber

/**
 * В Matrix чата с собой нет, поэтому «Избранное» — обычная приватная комната, где мы одни.
 * Её id лежит в account data, чтобы все устройства аккаунта открывали одну и ту же. Имя и аватар
 * (закладка) ставятся самой комнате, так что она выглядит «Избранным» везде: в списке, поиске,
 * пересылке и уведомлениях — без особых случаев в коде.
 */
interface SavedMessages {
    /** Id «Избранного», если его уже создали; null — ещё нет. */
    val roomId: StateFlow<RoomId?>

    /** Открыть «Избранное»: вернуть существующее или создать. */
    suspend fun getOrCreate(): Result<RoomId>
}

class NoOpSavedMessages(roomId: RoomId? = null) : SavedMessages {
    override val roomId: StateFlow<RoomId?> = MutableStateFlow(roomId)
    override suspend fun getOrCreate(): Result<RoomId> =
        roomId.value?.let { Result.success(it) } ?: Result.failure(IllegalStateException("no saved messages"))
}

@ContributesBinding(SessionScope::class)
@SingleIn(SessionScope::class)
class DefaultSavedMessages(
    private val client: MatrixClient,
    private val dispatchers: CoroutineDispatchers,
    @SessionCoroutineScope sessionScope: CoroutineScope,
) : SavedMessages {
    private val _roomId = MutableStateFlow<RoomId?>(null)
    override val roomId: StateFlow<RoomId?> = _roomId.asStateFlow()

    private val mutex = Mutex()

    init {
        sessionScope.launch { _roomId.value = storedRoomId() }
    }

    override suspend fun getOrCreate(): Result<RoomId> = withContext(dispatchers.io) {
        mutex.withLock {
            runCatching {
                storedRoomId()?.let { return@runCatching it }
                val roomId = client.createRoom(
                    CreateRoomParameters(
                        name = NAME,
                        isEncrypted = true,
                        isDirect = false,
                        visibility = RoomVisibility.Private,
                        preset = RoomPreset.PRIVATE_CHAT,
                        historyVisibilityOverride = RoomHistoryVisibility.Shared,
                    )
                ).getOrThrow()
                client.setAccountData(EVENT_TYPE, JSONObject().put(KEY_ROOM_ID, roomId.value).toString()).getOrThrow()
                client.getJoinedRoom(roomId)?.use { room ->
                    room.updateAvatar("image/png", SavedMessagesAvatar.png())
                        .onFailure { Timber.w(it, "Не удалось поставить аватар «Избранного»") }
                }
                roomId
            }.onSuccess { _roomId.value = it }
                .onFailure { Timber.w(it, "«Избранное» не открылось") }
        }
    }

    /** Id из account data, если комната ещё наша (не вышли и не удалили). */
    private suspend fun storedRoomId(): RoomId? {
        val raw = client.getAccountData(EVENT_TYPE).getOrNull() ?: return null
        val roomId = runCatching { RoomId(JSONObject(raw).getString(KEY_ROOM_ID)) }.getOrNull() ?: return null
        val isJoined = client.getRoom(roomId)?.use { it.info().currentUserMembership == CurrentUserMembership.JOINED } == true
        return roomId.takeIf { isJoined }
    }

    companion object {
        const val EVENT_TYPE = "ru.mangokokos.larpgram.saved_messages"
        private const val KEY_ROOM_ID = "room_id"

        /** Имя комнаты на сервере — так её видят все места, что не знают про «Избранное». */
        const val NAME = "Избранное"
    }
}
