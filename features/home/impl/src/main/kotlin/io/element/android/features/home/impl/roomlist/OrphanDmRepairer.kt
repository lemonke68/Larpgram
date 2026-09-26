/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.roomlist

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.room.CurrentUserMembership
import io.element.android.libraries.matrix.api.room.RoomInfo
import io.element.android.libraries.matrix.api.roomlist.RoomList
import io.element.android.libraries.matrix.api.roomlist.RoomListService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

/**
 * Правка форка (роумлесс): личка, которую клиент «забыл» отметить личкой.
 *
 * ЛС в Matrix — обычная комната плюс запись в account data `m.direct`. Если запись пропала
 * (у toluze её затёр другой клиент, когда он принимал ЛС с третьим человеком), Larpgram
 * показывает личку группой «2 участника» с аватаркой-буквой. Так не должно быть ни у кого
 * (решение юзера 2026-09-25: комнат «на двоих, но не ЛС» нет).
 *
 * Комната на двоих без своего названия, адреса и признаков канала — по сути личка, её
 * дописываем в свой `m.direct`. Запись — чтение-слияние-запись, чужие записи не трогаем;
 * если своего `m.direct` в сторе ещё нет, а лички уже есть, ничего не пишем, иначе затёрли
 * бы их точно так же. Стартует вместе со списком чатов ([RoomListDataSource]).
 */
@Inject
@SingleIn(SessionScope::class)
class OrphanDmRepairer(
    private val client: MatrixClient,
    private val roomListService: RoomListService,
    @SessionCoroutineScope
    private val sessionCoroutineScope: CoroutineScope,
) {
    private val mutex = Mutex()
    private val repaired = mutableSetOf<RoomId>()
    private var started = false

    fun start() {
        if (started) return
        started = true
        combine(
            roomListService.allRooms.summaries,
            roomListService.allRooms.loadingState,
        ) { summaries, loadingState -> summaries.takeIf { loadingState is RoomList.LoadingState.Loaded } }
            .debounce(3.seconds)
            .filter { it != null }
            .onEach { summaries ->
                val infos = summaries.orEmpty().map { it.info }
                val orphans = findOrphanDms(infos, client.sessionId).filterKeys { it !in repaired }
                if (orphans.isNotEmpty()) repair(orphans, hasKnownDms = infos.any { it.isDirect })
            }
            .launchIn(sessionCoroutineScope)
    }

    private suspend fun repair(orphans: Map<RoomId, UserId>, hasKnownDms: Boolean) = mutex.withLock {
        val current = client.getAccountData(M_DIRECT).getOrElse {
            Timber.w(it, "m.direct не прочитался, лички не чиним")
            return@withLock
        }
        if (current == null && hasKnownDms) {
            Timber.w("m.direct ещё не в сторе, хотя лички есть — не пишем, чтобы не затереть")
            return@withLock
        }
        val merged = mergeIntoDirect(current, orphans) ?: return@withLock
        client.setAccountData(M_DIRECT, merged)
            .onSuccess {
                repaired += orphans.keys
                Timber.i("Отмечено личками: ${orphans.size}")
            }
            .onFailure { Timber.w(it, "Не удалось записать m.direct") }
    }

    companion object {
        const val M_DIRECT = "m.direct"
    }
}

/** Комнаты-«сироты»: по сути лички, но без записи в `m.direct`. Значение — собеседник. */
internal fun findOrphanDms(rooms: List<RoomInfo>, me: UserId): Map<RoomId, UserId> =
    rooms.mapNotNull { info ->
        val other = info.heroes.singleOrNull { it.userId != me }?.userId
        val isOrphanDm = other != null &&
            info.currentUserMembership == CurrentUserMembership.JOINED &&
            !info.isDirect &&
            !info.isSpace &&
            info.isPublic != true &&
            info.rawName.isNullOrBlank() &&
            info.canonicalAlias == null &&
            info.activeMembersCount == 2L &&
            (info.roomPowerLevels?.values?.eventsDefault ?: 0L) <= 0L
        if (isOrphanDm) info.id to other else null
    }.toMap()

/**
 * Добавляет [orphans] в содержимое `m.direct` ([current] — сырой JSON или null, если его нет).
 * Возвращает новый JSON или null, если добавлять нечего или текущий JSON не разобрался.
 */
internal fun mergeIntoDirect(current: String?, orphans: Map<RoomId, UserId>): String? {
    val direct = if (current == null) {
        JSONObject()
    } else {
        runCatchingExceptions { JSONObject(current) }.getOrNull() ?: return null
    }
    var changed = false
    orphans.forEach { (roomId, userId) ->
        val rooms = direct.optJSONArray(userId.value) ?: JSONArray().also { direct.put(userId.value, it) }
        val present = (0 until rooms.length()).any { rooms.optString(it) == roomId.value }
        if (!present) {
            rooms.put(roomId.value)
            changed = true
        }
    }
    return direct.toString().takeIf { changed }
}
