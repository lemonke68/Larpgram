/*
 * Copyright (c) 2026 Larpgram.
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.room

import io.element.android.libraries.core.coroutine.parallelMap
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.forward.ForwardOptions
import io.element.android.libraries.matrix.api.forward.ForwardedMessage
import io.element.android.libraries.matrix.api.room.ForwardEventException
import io.element.android.libraries.matrix.api.room.RoomInfo
import io.element.android.libraries.matrix.impl.roomlist.roomOrNull
import kotlinx.coroutines.withTimeout
import org.matrix.rustcomponents.sdk.MessageContent
import org.matrix.rustcomponents.sdk.MessageType
import org.matrix.rustcomponents.sdk.MsgLikeKind
import org.matrix.rustcomponents.sdk.ProfileDetails
import org.matrix.rustcomponents.sdk.RoomListService
import org.matrix.rustcomponents.sdk.Timeline
import org.matrix.rustcomponents.sdk.TimelineItemContent
import org.matrix.rustcomponents.sdk.contentWithoutRelationFromMessage
import kotlin.time.Duration.Companion.milliseconds

/** Copies normal Matrix messages through the SDK send queue, with optional Larpgram attribution. */
class RoomContentForwarder(
    private val roomListService: RoomListService,
    private val sourceInfo: suspend (RoomId) -> RoomInfo = { error("No source information provider") },
    private val profileLinkAllowed: suspend (UserId) -> Boolean = { false },
    private val copyProtected: suspend (RoomId) -> Boolean = { error("No content protection provider") },
) {
    suspend fun forward(
        fromTimeline: Timeline,
        fromRoomId: RoomId,
        eventIds: List<EventId>,
        toRoomIds: List<RoomId>,
        options: ForwardOptions = ForwardOptions(),
        timeoutMs: Long = 5000L,
    ) {
        if (eventIds.isEmpty() || toRoomIds.isEmpty()) return
        // A hidden UI action is not sufficient: every export checks the fresh source policy.
        check(!copyProtected(fromRoomId)) { "Copying from this channel is restricted" }
        val source = sourceInfo(fromRoomId)
        val channel = (source.roomPowerLevels?.values?.eventsDefault ?: 0L) > 0L
        val profileLinks = mutableMapOf<String, Boolean>()
        val contents = eventIds.map { eventId ->
            val event = fromTimeline.getEventTimelineItemByEventId(eventId.value)
            val content = event.content as? TimelineItemContent.MsgLike ?: throw ForwardEventException(toRoomIds)
            val message = content.content.kind as? MsgLikeKind.Message ?: throw ForwardEventException(toRoomIds)
            val attribution = if (options.hideAuthor || options.mediaOnly) {
                null
            } else {
                val previous = ForwardedMessage.fromEventJson(event.lazyProvider.latestJson())
                val profile = event.senderProfile as? ProfileDetails.Ready
                val original = previous ?: if (channel) {
                    ForwardedMessage(
                        name = source.name,
                        roomId = fromRoomId.value,
                        eventId = eventId.value,
                        avatarUrl = source.avatarUrl,
                        isChannel = true,
                    )
                } else {
                    ForwardedMessage(
                        name = profile?.displayName,
                        userId = event.sender,
                        roomId = fromRoomId.value,
                        eventId = eventId.value,
                        avatarUrl = profile?.avatarUrl,
                    )
                }
                val userId = original.userId
                if (userId != null && !profileLinks.getOrPut(userId) { profileLinkAllowed(UserId(userId)) }) {
                    original.withoutProfileLink()
                } else {
                    original
                }
            }
            val copiedMessage = if (options.mediaOnly || options.caption != null) {
                message.content.withMediaCaption(options.caption?.takeIf(String::isNotBlank))
            } else {
                message.content.copy(mentions = null)
            }
            contentWithoutRelationFromMessage(copiedMessage) to attribution?.let(ForwardedMessage::extraContent)
        }
        // Return failures from each child instead of concurrently mutating a shared set. Missing
        // target rooms are failures too; silently dropping them would report a successful export.
        val failed = toRoomIds.distinct().parallelMap { roomId ->
            val room = roomListService.roomOrNull(roomId) ?: return@parallelMap roomId
            room.use { target ->
                runCatchingExceptions {
                    target.timeline().use { timeline ->
                        contents.forEach { (message, extra) ->
                            withTimeout(timeoutMs.milliseconds) { timeline.sendWithExtraContent(message, extra) }
                        }
                    }
                }
            }.exceptionOrNull()?.let { roomId }
        }.filterNotNull()
        if (failed.isNotEmpty()) throw ForwardEventException(failed)
    }
}

/** Preserve encrypted media sources and technical metadata, replacing only the media caption. */
internal fun MessageContent.withMediaCaption(caption: String?): MessageContent {
    val type = when (val type = msgType) {
        is MessageType.Image -> MessageType.Image(type.content.copy(caption = caption, formattedCaption = null))
        is MessageType.Video -> MessageType.Video(type.content.copy(caption = caption, formattedCaption = null))
        is MessageType.Audio -> MessageType.Audio(type.content.copy(caption = caption, formattedCaption = null))
        is MessageType.File -> MessageType.File(type.content.copy(caption = caption, formattedCaption = null))
        else -> error("This event is not a media file")
    }
    return copy(msgType = type, mentions = null)
}
