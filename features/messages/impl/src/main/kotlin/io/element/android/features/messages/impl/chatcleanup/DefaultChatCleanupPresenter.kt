/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.chatcleanup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.messages.impl.R
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.chatcleanup.api.ChatCleanupService
import io.element.android.libraries.chatcleanup.api.HISTORY_CLEARED_STATE_TYPE
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarDispatcher
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarMessage
import io.element.android.libraries.di.RoomScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.api.room.StateEventType
import io.element.android.libraries.matrix.api.room.powerlevels.permissionsAsState
import io.element.android.libraries.matrix.ui.saved.SavedMessages
import kotlinx.coroutines.launch

@ContributesBinding(RoomScope::class)
class DefaultChatCleanupPresenter(
    private val room: JoinedRoom,
    private val matrixClient: MatrixClient,
    private val chatCleanupService: ChatCleanupService,
    private val savedMessages: SavedMessages,
    private val snackbarDispatcher: SnackbarDispatcher,
) : Presenter<ChatCleanupState> {
    @Composable
    override fun present(): ChatCleanupState {
        val coroutineScope = rememberCoroutineScope()
        val roomInfo by room.roomInfoFlow.collectAsState()
        val savedMessagesRoomId by savedMessages.roomId.collectAsState()
        val canSendClearMarker by room.permissionsAsState(false) { perms ->
            perms.canOwnUserSendState(StateEventType.Custom(HISTORY_CLEARED_STATE_TYPE))
        }
        val peer = if (roomInfo.isDm) roomInfo.heroes.firstOrNull() else null
        val chatKind = when {
            room.roomId == savedMessagesRoomId -> ChatKind.SavedMessages
            // Канал Telegram — комната, где писать могут только админы (как в MessagesPresenter).
            (roomInfo.roomPowerLevels?.values?.eventsDefault ?: 0L) > 0L -> ChatKind.Channel
            roomInfo.isDm -> ChatKind.Dm
            else -> ChatKind.Group
        }

        fun handleEvent(event: ChatCleanupEvent) {
            when (event) {
                is ChatCleanupEvent.ClearHistory -> coroutineScope.launch {
                    chatCleanupService.clearHistory(room.roomId, upToTs = event.upToTs, forBoth = event.forBoth)
                        .onFailure {
                            snackbarDispatcher.post(SnackbarMessage(messageResId = R.string.larpgram_clear_history_failed))
                        }
                }
                is ChatCleanupEvent.DeleteChat -> if (event.forBoth) {
                    chatCleanupService.deleteChatForBoth(room.roomId)
                } else {
                    chatCleanupService.deleteChat(room.roomId)
                }
            }
        }

        return ChatCleanupState(
            chatKind = chatKind,
            chatName = peer?.displayName ?: roomInfo.name ?: room.roomId.value,
            canClearForBoth = chatKind == ChatKind.Dm && canSendClearMarker,
            canDeleteForBoth = chatKind == ChatKind.Dm && peer?.userId?.domainName == matrixClient.sessionId.domainName,
            eventSink = ::handleEvent,
        )
    }
}
