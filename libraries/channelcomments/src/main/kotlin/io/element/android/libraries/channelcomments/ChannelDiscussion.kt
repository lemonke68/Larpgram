/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.channelcomments

import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.api.timeline.MatrixTimelineItem
import kotlinx.coroutines.flow.first
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Shared bits of the Telegram-style channel↔comments link. A channel post is sent with a
 * [COMMENT_REF_FIELD] object in its content pointing at the discussion group; the channel→group
 * mapping is cached per-user in account data ([ACCOUNT_DATA_TYPE]). Custom room state is not
 * readable via the SDK, so the discussion is discovered from account data or, failing that,
 * backfilled from an existing post's raw content.
 */
object ChannelDiscussion {
    const val ACCOUNT_DATA_TYPE = "ru.mangokokos.larpgram.channel_discussions"
    const val COMMENT_REF_FIELD = "ru.mangokokos.larpgram.comment"
    const val COMMENT_ID_FIELD = "ru.mangokokos.larpgram.comment_id"

    val json = Json { ignoreUnknownKeys = true }

    /** channelId -> discussionId map decoded from raw account data content. */
    fun linkMap(rawAccountData: String?): Map<String, String> = rawAccountData
        ?.let { runCatching { json.decodeFromString<Map<String, String>>(it) }.getOrNull() }
        .orEmpty()

    fun encodeLinkMap(map: Map<String, String>): String = json.encodeToString(map)

    /** Discussion room id embedded in a channel post's raw event JSON, or null. */
    fun discussionRoomFromPost(originalJson: String): String? = commentRefFromPost(originalJson)?.first

    /** (discussionRoomId, commentId) embedded in a channel post's raw event JSON, or null. */
    fun commentRefFromPost(originalJson: String): Pair<String, String>? = runCatching {
        val comment = json.parseToJsonElement(originalJson).jsonObject["content"]?.jsonObject
            ?.get(COMMENT_REF_FIELD)?.jsonObject ?: return@runCatching null
        val room = comment["room"]?.jsonPrimitive?.content ?: return@runCatching null
        val id = comment["id"]?.jsonPrimitive?.content ?: return@runCatching null
        room to id
    }.getOrNull()

    /** The commentId a mirror message in the discussion group carries, or null. */
    fun commentIdFromMirror(originalJson: String): String? = runCatching {
        json.parseToJsonElement(originalJson).jsonObject["content"]?.jsonObject
            ?.get(COMMENT_ID_FIELD)?.jsonPrimitive?.content
    }.getOrNull()

    /**
     * The linked discussion group of [room] if it is a channel that has one, else null. Looked up in
     * per-user account data first, then backfilled from an existing channel post's content (custom
     * room state is not SDK-readable) and cached back to account data. Shared by the composer,
     * timeline and attachment-preview presenters.
     */
    suspend fun resolveDiscussionRoomId(room: JoinedRoom, matrixClient: MatrixClient): RoomId? {
        linkMap(matrixClient.getAccountData(ACCOUNT_DATA_TYPE).getOrNull())[room.roomId.value]
            ?.let { return RoomId(it) }
        val items = room.liveTimeline.timelineItems.first()
        for (item in items) {
            if (item !is MatrixTimelineItem.Event) continue
            val originalJson = item.event.timelineItemDebugInfoProvider().originalJson ?: continue
            val discussion = discussionRoomFromPost(originalJson)?.takeIf { it.isNotEmpty() } ?: continue
            cacheDiscussion(matrixClient, room.roomId, RoomId(discussion))
            return RoomId(discussion)
        }
        return null
    }

    private suspend fun cacheDiscussion(matrixClient: MatrixClient, channelId: RoomId, discussionId: RoomId) {
        val current = linkMap(matrixClient.getAccountData(ACCOUNT_DATA_TYPE).getOrNull())
        matrixClient.setAccountData(ACCOUNT_DATA_TYPE, encodeLinkMap(current + (channelId.value to discussionId.value)))
    }
}
