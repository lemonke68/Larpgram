/*
 * Copyright (c) 2026 Larpgram.
 * Правка форка: строки списка чатов Telegram — пины, «Избранное» сверху, черновик и «печатает…»,
 * очищенная история — и действия свайпа (пин, звук, удалить, блок). Вынесено из
 * `RoomListPresenter` (аудит C-009).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.roomlist

import dev.zacsweers.metro.Inject
import io.element.android.features.home.impl.model.ChatType
import io.element.android.features.home.impl.model.LatestEvent
import io.element.android.features.home.impl.model.RoomListRoomSummary
import io.element.android.features.home.impl.model.TypingPreview
import io.element.android.libraries.chatcleanup.api.ChatCleanupService
import io.element.android.libraries.dateformatter.api.DateFormatter
import io.element.android.libraries.dateformatter.api.DateFormatterMode
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.ui.drafts.DraftPreview
import io.element.android.libraries.matrix.ui.drafts.DraftPreviews
import io.element.android.libraries.matrix.ui.saved.SavedMessages
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

@Inject
class RoomListForkRows(
    private val client: MatrixClient,
    private val pinnedChatsStore: PinnedChatsStore,
    private val chatCleanupService: ChatCleanupService,
    private val draftPreviews: DraftPreviews,
    private val typingTracker: RoomListTypingTracker,
    private val dateFormatter: DateFormatter,
    private val savedMessages: SavedMessages,
) {
    /**
     * Готовый список SDK поверх наших данных. Сортировкой SDK и diff-кэшем не рулим —
     * переставляем уже готовый список.
     */
    fun decorate(summaries: Flow<ImmutableList<RoomListRoomSummary>>): Flow<ImmutableList<RoomListRoomSummary>> = combine(
        summaries,
        pinnedChatsStore.pinnedFlow.combine(chatCleanupService.clearedHistory, ::Pair),
        draftPreviews.drafts,
        typingTracker.typing,
        savedMessages.roomId,
    ) { rows, (pinnedIds, clearedHistory), drafts, typing, savedRoomId ->
        // «Избранное» всегда закреплено сверху (план, ф4.5), остальные пины — под ним.
        applyPins(rows, listOfNotNull(savedRoomId) + pinnedIds.filterNot { it == savedRoomId })
            .map { it.withDraftAndTyping(drafts[it.roomId], typing[it.roomId]) }
            // «Избранное»: «Вы присоединились к комнате» — шум создания, строку оставляем пустой.
            .map { if (it.roomId == savedRoomId && it.isLatestEventService) it.copy(latestEvent = LatestEvent.None) else it }
            .map { if (it.isLocalDm()) it.copy(canDeleteForBoth = true) else it }
            // История очищена (меню ⋮ в чате): последнее сообщение из-под отметки не показываем.
            .map { it.withoutClearedLatestEvent(clearedHistory[it.roomId]) }
            .toImmutableList()
    }

    /** «Печатает…» слушаем только у видимых строк. */
    suspend fun trackTypingOfVisibleRows(scope: CoroutineScope, visibleRange: Flow<IntRange>, rows: Flow<List<RoomListRoomSummary>>) {
        combine(visibleRange, rows) { range, summaries ->
            range.mapNotNull { summaries.getOrNull(it) }.associate { it.roomId to it.isDm }
        }
            .distinctUntilChangedBy { it.keys }
            .collect { rooms -> typingTracker.track(scope, rooms) }
    }

    /** Действия свайпа и меню строки, которых нет у Element. */
    fun handleEvent(event: RoomListEvent, scope: CoroutineScope) {
        when (event) {
            is RoomListEvent.SetRoomIsMuted -> scope.setRoomIsMuted(event.roomId, event.isMuted)
            is RoomListEvent.SetRoomIsPinned -> pinnedChatsStore.setPinned(event.roomId, event.isPinned)
            is RoomListEvent.DeleteRoom -> chatCleanupService.deleteChat(event.roomId)
            is RoomListEvent.DeleteRoomForBoth -> chatCleanupService.deleteChatForBoth(event.roomId)
            is RoomListEvent.BlockUser -> scope.blockUser(event.roomId, event.userId)
            else -> Unit
        }
    }

    /**
     * Черновик и «печатает…» поверх строки. Правило TG (`DialogCell`): черновик прячется, если после
     * него пришло непрочитанное сообщение; время у строки — время черновика, если он новее последнего
     * сообщения.
     */
    private fun RoomListRoomSummary.withDraftAndTyping(draft: DraftPreview?, typing: TypingPreview?): RoomListRoomSummary {
        val lastEventAt = latestEventTimestampMillis ?: 0L
        val shownDraft = draft?.takeUnless { hasNewContent && lastEventAt > it.savedAtMillis }
        if (shownDraft == null && typing == null) return this
        return copy(
            draft = shownDraft?.text,
            typing = typing,
            timestamp = if (shownDraft != null && shownDraft.savedAtMillis > lastEventAt) {
                dateFormatter.format(timestamp = shownDraft.savedAtMillis, mode = DateFormatterMode.TimeOrDate, useRelative = true)
            } else {
                timestamp
            },
        )
    }

    private fun RoomListRoomSummary.isLocalDm(): Boolean =
        chatType == ChatType.Dm && dmUserId != null && dmUserId.domainName == client.sessionId.domainName

    // Пин (роумлесс, ф2): закреплённые комнаты наверх, в порядке пина; помечаем isPinned для
    // строки/меню. Пин ставится только на ROOM, поэтому инвайты не трогаются.
    private fun applyPins(
        summaries: ImmutableList<RoomListRoomSummary>,
        pinnedIds: List<RoomId>,
    ): ImmutableList<RoomListRoomSummary> {
        if (pinnedIds.isEmpty()) return summaries
        val pinnedSet = pinnedIds.toSet()
        val byId = summaries.associateBy { it.roomId }
        val pinnedRooms = pinnedIds.mapNotNull { byId[it]?.copy(isPinned = true) }
        val rest = summaries.filterNot { it.roomId in pinnedSet }
        return (pinnedRooms + rest).toImmutableList()
    }

    // Блок (роумлесс, ф4, только ЛС): односторонняя TG-стена — ignoreUser + выйти/забыть личку.
    // Будущие инвайты автоотклоняет BlockedInviteAutoDecliner, композер гасит messages по
    // ignoredUsersFlow. Собеседник берётся из строки, иначе из участников.
    private fun CoroutineScope.blockUser(roomId: RoomId, userId: UserId?) = launch {
        client.getRoom(roomId)?.use { room ->
            val peer = userId ?: room.getMembers().getOrNull()
                ?.firstOrNull { it.userId != client.sessionId }?.userId
            if (peer != null) {
                client.ignoreUser(peer)
            }
            room.leave().onSuccess { room.forget() }
        }
    }

    private fun CoroutineScope.setRoomIsMuted(roomId: RoomId, isMuted: Boolean) = launch {
        val notificationSettings = client.notificationSettingsService
        if (isMuted) {
            notificationSettings.muteRoom(roomId)
        } else {
            client.getRoom(roomId)?.use { room ->
                val info = room.info()
                notificationSettings.unmuteRoom(
                    roomId = roomId,
                    isEncrypted = info.isEncrypted == true,
                    isOneToOne = info.isDm,
                )
            }
        }
    }
}
