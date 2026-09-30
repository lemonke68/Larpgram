/*
 * Copyright (c) 2026 Larpgram.
 * Правка форка: состояние экрана чата Telegram сверх элементовского. Вынесено из
 * `MessagesPresenter` (аудит C-009).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.circles.impl.CircleRecorderState
import io.element.android.features.gifs.impl.GifPickerState
import io.element.android.features.messages.impl.chatcleanup.ChatCleanupState
import io.element.android.features.stickers.impl.StickerPickerState
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.di.RoomScope
import io.element.android.libraries.imagepacks.api.ImagePackSource
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.api.room.RoomNotificationMode
import io.element.android.libraries.matrix.ui.saved.SavedMessages
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

@ContributesBinding(RoomScope::class)
class DefaultTgChatPresenter(
    private val room: JoinedRoom,
    private val matrixClient: MatrixClient,
    private val savedMessages: SavedMessages,
    private val imagePackSource: ImagePackSource,
    private val stickerPickerPresenter: Presenter<StickerPickerState>,
    private val gifPickerPresenter: Presenter<GifPickerState>,
    private val circleRecorderPresenter: Presenter<CircleRecorderState>,
    private val chatCleanupPresenter: Presenter<ChatCleanupState>,
) : Presenter<TgChatState> {
    @Composable
    override fun present(): TgChatState {
        val coroutineScope = rememberCoroutineScope()
        val roomInfo by room.roomInfoFlow.collectAsState()
        val savedMessagesRoomId by savedMessages.roomId.collectAsState()
        // Канал Telegram: писать могут только админы. Подписчик вместо поля ввода видит полосу звука,
        // комментарии — под каждым постом.
        val isChannel = (roomInfo.roomPowerLevels?.values?.eventsDefault ?: 0L) > 0L
        // Звук нужен не только каналу: пункт «Выключить уведомления» в ⋮ Telegram есть у любого чата.
        val isMuted by produceState(initialValue = false) {
            matrixClient.notificationSettingsService.notificationSettingsChangeFlow
                .onStart { emit(Unit) }
                .collect {
                    val info = room.info()
                    val settings = matrixClient.notificationSettingsService.getRoomNotificationSettings(
                        roomId = room.roomId,
                        isEncrypted = info.isEncrypted == true,
                        isOneToOne = info.isDm,
                    ).getOrNull()
                    value = settings?.mode == RoomNotificationMode.MUTE
                }
        }
        // Блок (роумлесс, ф4): собеседник ЛС в ignoredUsers — своя сторона стены. Поле ввода гасим,
        // показываем полосу «Разблокировать». Реактивно на ignoredUsersFlow.
        val dmPeerUserId = if (roomInfo.isDm) roomInfo.heroes.firstOrNull()?.userId else null
        val ignoredUsers by matrixClient.ignoredUsersFlow.collectAsState()

        fun handleEvent(event: TgChatEvent) {
            when (event) {
                TgChatEvent.ToggleMute -> coroutineScope.launch {
                    val service = matrixClient.notificationSettingsService
                    if (isMuted) {
                        val info = room.info()
                        service.unmuteRoom(roomId = room.roomId, isEncrypted = info.isEncrypted == true, isOneToOne = info.isDm)
                    } else {
                        service.muteRoom(room.roomId)
                    }
                }
                TgChatEvent.UnblockUser -> dmPeerUserId?.let { peer ->
                    coroutineScope.launch { matrixClient.unignoreUser(peer) }
                }
            }
        }

        return TgChatState(
            isChannel = isChannel,
            isMuted = isMuted,
            channelSubscriberCount = if (isChannel) roomInfo.joinedMembersCount else null,
            isUserBlocked = dmPeerUserId?.let { it in ignoredUsers } == true,
            dmUserId = dmPeerUserId,
            // В «Избранном» подзаголовка «1 участник» нет, как в TG.
            memberCount = roomInfo.joinedMembersCount.takeUnless { room.roomId == savedMessagesRoomId },
            stickerPickerState = stickerPickerPresenter.present(),
            gifPickerState = gifPickerPresenter.present(),
            circleRecorderState = circleRecorderPresenter.present(),
            chatCleanupState = chatCleanupPresenter.present(),
            circleMediaLoader = matrixClient.matrixMediaLoader,
            imagePackSource = imagePackSource,
            eventSink = ::handleEvent,
        )
    }
}
