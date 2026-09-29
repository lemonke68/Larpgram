/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.chatsearch

import androidx.compose.foundation.text.input.TextFieldState
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.matrix.api.core.EventId
import kotlinx.collections.immutable.ImmutableList

/**
 * Поиск по сообщениям одного чата (⋮ → «Поиск», как в TG). Ищет локальный индекс SDK, поэтому
 * находятся сообщения, пришедшие после включения поиска, — об этом говорит пустой результат.
 */
data class ChatSearchState(
    val query: TextFieldState,
    val results: ImmutableList<ChatSearchResult>,
    val isLoading: Boolean,
    val isAvailable: Boolean,
    val eventSink: (ChatSearchEvent) -> Unit,
) {
    val showNoResults: Boolean = query.text.isNotBlank() && !isLoading && results.isEmpty()
}

data class ChatSearchResult(
    val eventId: EventId,
    val senderName: String,
    val senderAvatar: AvatarData,
    val body: String,
    val formattedTimestamp: String,
)

sealed interface ChatSearchEvent {
    data class VisibleRangeChanged(val range: IntRange) : ChatSearchEvent
}
