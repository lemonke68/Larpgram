/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.channel

import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.api.timeline.MatrixTimelineItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Telegram-style channel comments rely on each channel post being echoed into the linked discussion
 * group. A post is sent normally (the SDK encrypts it and we can't inject a comment reference into
 * that send), so afterwards we identify the just-sent event and re-post its content into the
 * discussion group carrying the post's event id ([ChannelDiscussion.COMMENT_ID_FIELD]); the
 * "Comments" chip correlates by that id.
 *
 * This is shared by every send path that produces a channel post (attachments preview, voice
 * messages, …). Each path supplies a [matches] predicate identifying its own event kind, since the
 * different senders never return an event id.
 */
internal object ChannelPostMirror {
    private const val MIRROR_TIMEOUT_MS = 20_000L
    const val ROOM_MESSAGE_EVENT_TYPE = "m.room.message"

    /** Message msgtypes we can mirror verbatim (mxc url + decryption keys travel inside the content). */
    private val MIRRORABLE_MSGTYPES = setOf("m.image", "m.video", "m.audio", "m.file")

    /** True for an `m.room.message` whose msgtype we know how to mirror (image/video/audio/file, incl. voice). */
    fun isMirrorableMessage(originalJson: String?): Boolean {
        originalJson ?: return false
        return runCatchingExceptions {
            val msgtype = ChannelDiscussion.json.parseToJsonElement(originalJson).jsonObject["content"]
                ?.jsonObject?.get("msgtype")?.jsonPrimitive?.content
            msgtype in MIRRORABLE_MSGTYPES
        }.getOrDefault(false)
    }

    /** A room is a channel (broadcast) when sending is admin-gated: eventsDefault power level > 0. */
    private suspend fun isChannel(room: JoinedRoom): Boolean =
        (room.info().roomPowerLevels?.values?.eventsDefault ?: 0L) > 0L

    /** Event ids of my posts already in the timeline that satisfy [matches] — used to skip older posts. */
    suspend fun myPostIds(room: JoinedRoom, matches: (String?) -> Boolean): Set<String> = runCatchingExceptions {
        room.liveTimeline.timelineItems.first()
            .asSequence()
            .filterIsInstance<MatrixTimelineItem.Event>()
            .filter { it.event.sender.value == room.sessionId.value }
            .filter { matches(it.event.timelineItemDebugInfoProvider().originalJson) }
            .mapNotNull { it.event.eventId?.value }
            .toSet()
    }.getOrDefault(emptySet())

    /**
     * Mirror the newest of my matching posts (excluding [preIds], the ones already present before the
     * send) into the channel's discussion group. No-op if [room] is not a channel, has no discussion,
     * or the post doesn't get a remote event id within [MIRROR_TIMEOUT_MS].
     */
    suspend fun mirrorLastPost(
        room: JoinedRoom,
        matrixClient: MatrixClient,
        preIds: Set<String>,
        eventType: String = ROOM_MESSAGE_EVENT_TYPE,
        matches: (String?) -> Boolean,
    ) {
        if (!isChannel(room)) return
        val discussionId = ChannelDiscussion.resolveDiscussionRoomId(room, matrixClient) ?: return
        val myId = room.sessionId.value
        // Wait for our just-sent post to appear with a remote event id (skip local echo and any
        // older matching posts captured in [preIds]).
        val sent = withTimeoutOrNull(MIRROR_TIMEOUT_MS) {
            room.liveTimeline.timelineItems
                .mapNotNull { items ->
                    items.asSequence()
                        .filterIsInstance<MatrixTimelineItem.Event>()
                        .lastOrNull { ev ->
                            val id = ev.event.eventId?.value
                            id != null && id !in preIds &&
                                ev.event.sender.value == myId &&
                                matches(ev.event.timelineItemDebugInfoProvider().originalJson)
                        }
                }
                .first()
        } ?: return
        val eventId = sent.event.eventId?.value ?: return
        val originalJson = sent.event.timelineItemDebugInfoProvider().originalJson ?: return
        val content = buildMirrorContent(originalJson, eventId) ?: return
        matrixClient.getJoinedRoom(discussionId)?.use { it.sendRawEvent(eventType, content) }
    }

    private fun buildMirrorContent(originalJson: String, commentId: String): String? = runCatchingExceptions {
        val content = ChannelDiscussion.json.parseToJsonElement(originalJson).jsonObject["content"]?.jsonObject
            ?: return@runCatchingExceptions null
        buildJsonObject {
            content.forEach { (key, value) -> put(key, value) }
            put(ChannelDiscussion.COMMENT_ID_FIELD, commentId)
        }.toString()
    }.getOrNull()
}
