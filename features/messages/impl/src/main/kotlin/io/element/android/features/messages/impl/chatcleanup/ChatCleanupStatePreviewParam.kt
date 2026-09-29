/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.chatcleanup

import androidx.compose.ui.tooling.preview.PreviewParameterProvider

open class ChatCleanupStatePreviewParam : PreviewParameterProvider<ChatCleanupState> {
    override val values: Sequence<ChatCleanupState>
        get() = sequenceOf(
            aChatCleanupState(chatKind = ChatKind.Dm, chatName = "Alice", canClearForBoth = true, canDeleteForBoth = true),
            aChatCleanupState(chatKind = ChatKind.Group, chatName = "Game masters"),
        )
}

internal fun aChatCleanupState(
    chatKind: ChatKind = ChatKind.Dm,
    chatName: String = "Alice",
    canClearForBoth: Boolean = false,
    canDeleteForBoth: Boolean = false,
    eventSink: (ChatCleanupEvent) -> Unit = {},
) = ChatCleanupState(
    chatKind = chatKind,
    chatName = chatName,
    canClearForBoth = canClearForBoth,
    canDeleteForBoth = canDeleteForBoth,
    eventSink = eventSink,
)
