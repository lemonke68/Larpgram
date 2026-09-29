/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.chatsearch

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.matrix.api.core.EventId
import kotlinx.collections.immutable.toImmutableList

open class ChatSearchStatePreviewParam : PreviewParameterProvider<ChatSearchState> {
    override val values: Sequence<ChatSearchState>
        get() = sequenceOf(
            aChatSearchState(query = "banana", results = listOf(aResult("Alice", "Zebra banana search"), aResult("Bob", "Banana bread recipe"))),
            aChatSearchState(query = "zzz"),
            aChatSearchState(isAvailable = false),
        )

    private fun aResult(name: String, body: String) = ChatSearchResult(
        eventId = EventId("\$$name"),
        senderName = name,
        senderAvatar = AvatarData(id = "@$name:server", name = name, url = null, size = AvatarSize.RoomListItem),
        body = body,
        formattedTimestamp = "12:43",
    )

    private fun aChatSearchState(
        query: String = "",
        results: List<ChatSearchResult> = emptyList(),
        isAvailable: Boolean = true,
    ) = ChatSearchState(
        query = TextFieldState(query),
        results = results.toImmutableList(),
        isLoading = false,
        isAvailable = isAvailable,
        eventSink = {},
    )
}
