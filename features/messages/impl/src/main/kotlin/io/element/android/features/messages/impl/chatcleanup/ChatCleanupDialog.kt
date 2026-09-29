/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.chatcleanup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.messages.impl.R
import io.element.android.libraries.designsystem.components.dialogs.ListDialog
import io.element.android.libraries.designsystem.components.list.CheckboxListItem
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Text

enum class ChatCleanupDialogType {
    ClearHistory,
    DeleteChat,
}

/**
 * Подтверждение Telegram: «Очистить историю» и «Удалить чат» / «Выйти из группы» / «Покинуть канал»,
 * в личке — с галочкой «Также … для <имя>». После удаления [onChatDeleted] уводит из чата.
 */
@Composable
internal fun ChatCleanupDialog(
    type: ChatCleanupDialogType,
    state: ChatCleanupState,
    latestEventTs: Long,
    onDismiss: () -> Unit,
    onChatDeleted: () -> Unit,
) {
    var forBoth by rememberSaveable { mutableStateOf(false) }
    val canActForBoth = when (type) {
        ChatCleanupDialogType.ClearHistory -> state.canClearForBoth
        ChatCleanupDialogType.DeleteChat -> state.canDeleteForBoth
    }
    val title = stringResource(type.titleRes(state.chatKind))
    ListDialog(
        title = title,
        submitText = title,
        destructiveSubmit = true,
        onDismissRequest = onDismiss,
        onSubmit = {
            onDismiss()
            when (type) {
                ChatCleanupDialogType.ClearHistory -> {
                    state.eventSink(ChatCleanupEvent.ClearHistory(upToTs = latestEventTs, forBoth = forBoth && canActForBoth))
                }
                ChatCleanupDialogType.DeleteChat -> {
                    state.eventSink(ChatCleanupEvent.DeleteChat(forBoth = forBoth && canActForBoth))
                    onChatDeleted()
                }
            }
        },
    ) {
        item {
            Text(
                text = stringResource(type.messageRes(state.chatKind), state.chatName),
                style = ElementTheme.typography.fontBodyMdRegular,
                color = ElementTheme.colors.textSecondary,
            )
        }
        if (canActForBoth) {
            item {
                CheckboxListItem(
                    headline = stringResource(
                        if (type == ChatCleanupDialogType.ClearHistory) R.string.larpgram_clear_history_also else R.string.larpgram_delete_chat_also,
                        state.chatName,
                    ),
                    checked = forBoth,
                    onChange = { forBoth = it },
                    compactLayout = true,
                )
            }
        }
    }
}

/** Текст пункта меню и заголовка диалога. */
internal fun ChatCleanupDialogType.titleRes(chatKind: ChatKind): Int = when (this) {
    ChatCleanupDialogType.ClearHistory -> R.string.larpgram_clear_history
    ChatCleanupDialogType.DeleteChat -> when (chatKind) {
        ChatKind.Group -> R.string.larpgram_leave_group
        ChatKind.Channel -> R.string.larpgram_leave_channel
        ChatKind.Dm, ChatKind.SavedMessages -> R.string.larpgram_delete_chat
    }
}

private fun ChatCleanupDialogType.messageRes(chatKind: ChatKind): Int = when (this) {
    ChatCleanupDialogType.ClearHistory -> when (chatKind) {
        ChatKind.Dm -> R.string.larpgram_clear_history_dm_message
        ChatKind.SavedMessages -> R.string.larpgram_clear_history_saved_message
        ChatKind.Group, ChatKind.Channel -> R.string.larpgram_clear_history_group_message
    }
    ChatCleanupDialogType.DeleteChat -> when (chatKind) {
        ChatKind.Group -> R.string.larpgram_leave_group_message
        ChatKind.Channel -> R.string.larpgram_leave_channel_message
        ChatKind.Dm, ChatKind.SavedMessages -> R.string.larpgram_delete_chat_message
    }
}

@PreviewsDayNight
@Composable
internal fun ChatCleanupDialogPreview(@PreviewParameter(ChatCleanupStatePreviewParam::class) state: ChatCleanupState) = ElementPreview {
    ChatCleanupDialog(
        type = if (state.chatKind == ChatKind.Dm) ChatCleanupDialogType.DeleteChat else ChatCleanupDialogType.ClearHistory,
        state = state,
        latestEventTs = 0L,
        onDismiss = {},
        onChatDeleted = {},
    )
}
