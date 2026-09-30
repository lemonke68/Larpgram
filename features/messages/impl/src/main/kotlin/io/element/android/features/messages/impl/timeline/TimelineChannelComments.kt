/*
 * Copyright (c) 2026 Larpgram.
 * Правка форка: комментарии к постам канала в ленте, как в Telegram. Вынесено из
 * `TimelinePresenter` (аудит C-009).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import dev.zacsweers.metro.Inject
import io.element.android.features.messages.impl.MessagesNavigator
import io.element.android.features.messages.impl.timeline.factories.dropEmptyDaySeparators
import io.element.android.features.messages.impl.timeline.groups.canBeGrouped
import io.element.android.features.messages.impl.timeline.model.TimelineItem
import io.element.android.libraries.channelcomments.ChannelDiscussion
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.ThreadId
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.api.timeline.MatrixTimelineItem
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import timber.log.Timber

@Inject
class TimelineChannelComments(
    private val room: JoinedRoom,
    private val matrixClient: MatrixClient,
) {
    data class State(
        /** Группа обсуждения канала: по ней на постах с картинками появляется кнопка «Комментарии». */
        val discussionRoomId: RoomId?,
        /** comment_id поста → число комментариев для чипа «N комментариев». */
        val commentCounts: ImmutableMap<String, Long>,
    )

    @Composable
    fun present(isChannel: Boolean): State {
        val discussionRoomId by produceState<RoomId?>(null, isChannel) {
            value = if (isChannel) resolveDiscussionRoomId() else null
        }
        // Комментарии — это реплаи-тред на зеркало поста в дискуссии, поэтому число = numberOfReplies
        // треда. Ключа `comment_id` в ThreadListItem нет, поэтому root-eventId сопоставляем с
        // comment_id по сырому JSON зеркала в таймлайне дискуссии. Мягкий фолбэк: пусто → чип без числа.
        val commentCounts by produceState<ImmutableMap<String, Long>>(persistentMapOf(), isChannel, discussionRoomId) {
            val discussionId = discussionRoomId
            if (!isChannel || discussionId == null) {
                value = persistentMapOf()
                return@produceState
            }
            val discussion = matrixClient.getJoinedRoom(discussionId) ?: run {
                value = persistentMapOf()
                return@produceState
            }
            discussion.use { d ->
                val threadsService = d.threadsListService
                try {
                    combine(
                        threadsService.subscribeToItemUpdates().onStart { threadsService.paginate() },
                        d.liveTimeline.timelineItems,
                    ) { threadItems, timelineItems ->
                        val commentIdByEvent = timelineItems.asSequence()
                            .filterIsInstance<MatrixTimelineItem.Event>()
                            .mapNotNull { item ->
                                val raw = item.event.timelineItemDebugInfoProvider().originalJson ?: return@mapNotNull null
                                val commentId = ChannelDiscussion.commentIdFromMirror(raw) ?: return@mapNotNull null
                                val eventId = item.event.eventId?.value ?: return@mapNotNull null
                                eventId to commentId
                            }
                            .toMap()
                        threadItems.mapNotNull { threadItem ->
                            val commentId = commentIdByEvent[threadItem.rootEvent.eventId.value] ?: return@mapNotNull null
                            commentId to threadItem.numberOfReplies
                        }.toMap().toImmutableMap()
                    }.collect { value = it }
                } finally {
                    threadsService.destroy()
                }
            }
        }
        return State(discussionRoomId = discussionRoomId, commentCounts = commentCounts)
    }

    /**
     * Канал прячет служебные строки («вошёл», «пригласил», «сменил название»), как в Telegram;
     * «Избранное» — шум создания комнаты. Плашку дня без сообщений после фильтра тоже убираем.
     */
    suspend fun filterServiceItems(items: List<TimelineItem>, isSavedMessages: Boolean): List<TimelineItem> {
        val isChannelRoom = (room.info().roomPowerLevels?.values?.eventsDefault ?: 0L) > 0L
        if (!isChannelRoom && !isSavedMessages) return items
        return items.filterNot(::isServiceItem).dropEmptyDaySeparators()
    }

    /**
     * Открыть комментарии поста: тред его зеркала в группе обсуждения — пост сверху, комментарии
     * ниже. Текстовые посты несут группу и id связки в своём контенте; медиапосты (отправка SDK)
     * связаны по своему eventId. Если зеркало не найдено, открываем саму группу.
     */
    suspend fun openPostComments(post: TimelineItem.Event, navigator: MessagesNavigator) {
        val textRef = post.debugInfo.originalJson?.let { ChannelDiscussion.commentRefFromPost(it) }
        val discussionId: RoomId
        val commentId: String
        if (textRef != null) {
            discussionId = RoomId(textRef.first)
            commentId = textRef.second
        } else {
            discussionId = resolveDiscussionRoomId() ?: run {
                Timber.w("No discussion group for channel post ${post.eventId}")
                return
            }
            commentId = post.eventId?.value ?: return
        }
        val mirrorEventId = findMirrorEventId(discussionId, commentId = commentId)
        if (mirrorEventId != null) {
            navigator.navigateToRoomThread(discussionId, threadRootId = ThreadId(mirrorEventId.value))
        } else {
            navigator.navigateToRoom(discussionId, eventId = null, serverNames = emptyList())
        }
    }

    private suspend fun resolveDiscussionRoomId(): RoomId? = ChannelDiscussion.resolveDiscussionRoomId(room, matrixClient)

    private fun isServiceItem(item: TimelineItem): Boolean = when (item) {
        is TimelineItem.GroupedEvents -> true
        is TimelineItem.Event -> item.canBeGrouped()
        else -> false
    }

    private suspend fun findMirrorEventId(discussionId: RoomId, commentId: String): EventId? {
        val discussion = matrixClient.getJoinedRoom(discussionId) ?: return null
        return discussion.use { room ->
            room.liveTimeline.timelineItems.first()
                .asSequence()
                .filterIsInstance<MatrixTimelineItem.Event>()
                .firstOrNull { item ->
                    val originalJson = item.event.timelineItemDebugInfoProvider().originalJson
                    originalJson != null && ChannelDiscussion.commentIdFromMirror(originalJson) == commentId
                }
                ?.event
                ?.eventId
        }
    }
}
