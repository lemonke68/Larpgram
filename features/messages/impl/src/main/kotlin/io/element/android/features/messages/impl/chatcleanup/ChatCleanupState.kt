/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.chatcleanup

/** Пункты «Очистить историю» и «Удалить чат» / «Выйти» в меню ⋮ шапки чата, как в Telegram. */
data class ChatCleanupState(
    val chatKind: ChatKind,
    /** Название чата или имя собеседника для текста подтверждения. */
    val chatName: String,
    /** Галочка «Также очистить для …»: личка, где можно отправить отметку в комнату. */
    val canClearForBoth: Boolean,
    /** Галочка «Также удалить для …»: личка с человеком с нашего сервера (серверный purge). */
    val canDeleteForBoth: Boolean,
    val eventSink: (ChatCleanupEvent) -> Unit,
) {
    val canClearHistory: Boolean = chatKind != ChatKind.Channel
    val canDeleteChat: Boolean = chatKind != ChatKind.SavedMessages
}

enum class ChatKind {
    Dm,
    Group,
    Channel,
    SavedMessages,
}

sealed interface ChatCleanupEvent {
    /** [upToTs] — время сервера самого свежего сообщения в ленте. */
    data class ClearHistory(val upToTs: Long, val forBoth: Boolean) : ChatCleanupEvent

    /** Удалить личку или выйти из группы/канала. */
    data class DeleteChat(val forBoth: Boolean) : ChatCleanupEvent
}
