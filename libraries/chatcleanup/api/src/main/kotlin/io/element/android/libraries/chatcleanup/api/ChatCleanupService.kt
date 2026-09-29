/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatcleanup.api

import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.coroutines.flow.StateFlow

/**
 * Удаление чата и очистка истории, как в Telegram. Общий для списка чатов (свайп) и меню ⋮ в чате.
 *
 * Очистка истории в Matrix не удаляет события: Larpgram прячет всё, что было до отметки. Отметка
 * «у себя» лежит в account data, «у обоих» — ещё и состоянием комнаты [HISTORY_CLEARED_STATE_TYPE],
 * его видит клиент собеседника. Обычные клиенты Matrix показывают историю целиком.
 */
interface ChatCleanupService {
    /**
     * Отметки очистки: комната → время сервера (мс) последнего скрытого события, включительно.
     * Сюда же попадают отметки «у обоих», которые клиент увидел в ленте.
     */
    val clearedHistory: StateFlow<Map<RoomId, Long>>

    /** Удалить чат у себя: выйти и забыть комнату, она уходит из списка. */
    fun deleteChat(roomId: RoomId)

    /**
     * «Удалить у обоих» для лички с человеком с нашего сервера: серверный purge через key-escrow
     * после окна отмены. Плашка с «Отменить» — в общем снэкбаре сессии.
     */
    fun deleteChatForBoth(roomId: RoomId)

    /** Отменить «удалить у обоих», пока не истекло окно отмены. */
    fun cancelDeleteForBoth(roomId: RoomId)

    /**
     * Очистить историю: спрятать всё до [upToTs] включительно. [forBoth] — ещё и отправить в комнату
     * отметку, по которой историю спрячет клиент собеседника.
     */
    suspend fun clearHistory(roomId: RoomId, upToTs: Long, forBoth: Boolean): Result<Unit>

    /** В ленте встретилась отметка «очищено у обоих» от [markerTs] — запомнить её у себя. */
    fun onClearMarkerSeen(roomId: RoomId, markerTs: Long)
}

/** Тип состояния комнаты с отметкой «история очищена у обоих». Время отметки — время события. */
const val HISTORY_CLEARED_STATE_TYPE = "ru.mangokokos.larpgram.history_cleared"
