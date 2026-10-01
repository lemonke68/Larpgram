/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.features.messages.api.timeline.HtmlConverterProvider
import io.element.android.features.messages.impl.timeline.TimelineChannelComments
import io.element.android.features.messages.impl.timeline.TimelineRoomInfo
import io.element.android.features.messages.impl.timeline.applyClearedHistory
import io.element.android.features.messages.impl.timeline.factories.TimelineItemsFactory
import io.element.android.features.messages.impl.timeline.factories.TimelineItemsFactoryConfig
import io.element.android.features.messages.impl.timeline.model.TimelineItem
import io.element.android.features.messages.impl.timeline.protection.TimelineProtectionState
import io.element.android.features.messages.impl.typing.TypingNotificationState
import io.element.android.features.roomcall.api.aStandByCallState
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.chatcleanup.api.ChatCleanupService
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.api.room.roomMembers
import io.element.android.libraries.matrix.api.timeline.Timeline
import io.element.android.libraries.matrix.ui.saved.SavedMessages
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class ChatPreviewState(
    val timelineRoomInfo: TimelineRoomInfo,
    val timelineProtectionState: TimelineProtectionState,
    val timelineItems: ImmutableList<TimelineItem>,
    val loadMore: () -> Unit,
)

/**
 * Лента для превью чата из списка. Нарочно без `TimelinePresenter` и `MessagesPresenter`: именно
 * они шлют отметки о прочтении, снимают флаг «не прочитано» и ставят fully-read. Здесь лента только
 * читается (живая лента комнаты и подгрузка назад), наружу не уходит ничего.
 */
@Inject
class ChatPreviewPresenter(
    private val room: JoinedRoom,
    timelineItemsFactoryCreator: TimelineItemsFactory.Creator,
    private val timelineProtectionPresenter: Presenter<TimelineProtectionState>,
    private val timelineChannelComments: TimelineChannelComments,
    private val chatCleanupService: ChatCleanupService,
    private val savedMessages: SavedMessages,
    private val htmlConverterProvider: HtmlConverterProvider,
    private val dispatchers: CoroutineDispatchers,
) : Presenter<ChatPreviewState> {
    private val timelineItemsFactory: TimelineItemsFactory = timelineItemsFactoryCreator.create(
        config = TimelineItemsFactoryConfig(
            computeReadReceipts = false,
            computeReactions = true,
        )
    )

    @Composable
    override fun present(): ChatPreviewState {
        htmlConverterProvider.Update()
        val roomInfo by room.roomInfoFlow.collectAsState()
        val savedMessagesRoomId by savedMessages.roomId.collectAsState()
        val timelineRoomInfo by remember {
            derivedStateOf {
                TimelineRoomInfo(
                    isDm = roomInfo.isDm,
                    isChannel = (roomInfo.roomPowerLevels?.values?.eventsDefault ?: 0L) > 0L,
                    name = roomInfo.name,
                    // В превью писать и реагировать нельзя: так строки не включают ответ свайпом.
                    userHasPermissionToSendMessage = false,
                    userHasPermissionToSendReaction = false,
                    roomCallState = aStandByCallState(),
                    pinnedEventIds = roomInfo.pinnedEventIds,
                    typingNotificationState = TypingNotificationState(
                        renderTypingNotifications = false,
                        typingMembers = persistentListOf(),
                        reserveSpace = false,
                    ),
                    predecessorRoom = room.predecessorRoom(),
                    isSavedMessages = room.roomId == savedMessagesRoomId,
                )
            }
        }
        val timelineProtectionState = timelineProtectionPresenter.present()
        var timelineItems by remember { mutableStateOf<ImmutableList<TimelineItem>>(persistentListOf()) }

        LaunchedEffect(Unit) {
            combine(room.liveTimeline.timelineItems, room.membersStateFlow) { items, membersState ->
                timelineItemsFactory.replaceWith(
                    timelineItems = items,
                    roomMembers = membersState.roomMembers().orEmpty(),
                    renderReadReceipts = false,
                )
            }
                .flowOn(dispatchers.computation)
                .launchIn(this)

            // Отметка очистки истории действует и здесь: превью не должно показывать то, что в чате скрыто.
            val clearedUpToFlow = chatCleanupService.clearedHistory.map { it[room.roomId] }.distinctUntilChanged()
            timelineItemsFactory.timelineItems
                .combine(clearedUpToFlow) { items, clearedUpTo -> items.applyClearedHistory(clearedUpTo).items }
                .onEach { items ->
                    timelineItems = timelineChannelComments.filterServiceItems(
                        items = items,
                        isSavedMessages = room.roomId == savedMessages.roomId.value,
                    ).toImmutableList()
                }
                .launchIn(this)
        }

        val scope = rememberCoroutineScope()
        return ChatPreviewState(
            timelineRoomInfo = timelineRoomInfo,
            timelineProtectionState = timelineProtectionState,
            timelineItems = timelineItems,
            loadMore = {
                scope.launch { room.liveTimeline.paginate(Timeline.PaginationDirection.BACKWARDS) }
            },
        )
    }
}
