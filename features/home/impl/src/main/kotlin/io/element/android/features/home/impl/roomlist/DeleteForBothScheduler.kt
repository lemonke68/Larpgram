/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.roomlist

import androidx.compose.material3.SnackbarDuration
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.features.home.impl.R
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarDispatcher
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarMessage
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.keyescrow.api.KeyEscrowService
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/** Окно отмены «удалить у обоих» перед серверным purge. */
internal const val DELETE_FOR_BOTH_UNDO_MS = 5_000L

/**
 * Правка форка: отложенное «удалить у обоих» для ЛС (серверный Synapse purge через key-escrow).
 *
 * Живёт на session scope, а не в композиции списка чатов: раньше задача умирала, если за 5 с
 * окна отмены открыть чат или сменить вкладку, а второе удаление отменяло первое. Теперь у каждой
 * комнаты своя задача, и отменяет её только «Отменить» на плашке.
 */
@Inject
@SingleIn(SessionScope::class)
class DeleteForBothScheduler(
    private val keyEscrowService: KeyEscrowService,
    private val snackbarDispatcher: SnackbarDispatcher,
    @SessionCoroutineScope
    private val sessionCoroutineScope: CoroutineScope,
) {
    private val pending = ConcurrentHashMap<RoomId, Job>()

    fun schedule(roomId: RoomId) {
        if (pending.containsKey(roomId)) return
        val job = sessionCoroutineScope.launch {
            delay(DELETE_FOR_BOTH_UNDO_MS)
            if (!keyEscrowService.deleteDmForBoth(roomId)) {
                snackbarDispatcher.post(SnackbarMessage(messageResId = R.string.screen_roomlist_delete_both_failed))
            }
        }
        pending[roomId] = job
        job.invokeOnCompletion { pending.remove(roomId, job) }
        snackbarDispatcher.post(
            SnackbarMessage(
                messageResId = R.string.screen_roomlist_delete_both_pending,
                actionResId = CommonStrings.action_cancel,
                duration = SnackbarDuration.Long,
                action = { cancel(roomId) },
            )
        )
    }

    fun cancel(roomId: RoomId) {
        pending.remove(roomId)?.cancel()
    }
}
