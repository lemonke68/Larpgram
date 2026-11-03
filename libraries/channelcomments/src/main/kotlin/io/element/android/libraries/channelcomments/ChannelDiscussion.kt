/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.channelcomments

import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.api.timeline.MatrixTimelineItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Channel-to-discussion links live in shared room state, fetched through authenticated CS API.
 * Account data caches the link for offline use; legacy post references remain a fallback.
 */
object ChannelDiscussion {
    const val DISCUSSION_STATE_TYPE = "ru.mangokokos.larpgram.discussion"
    private val httpClient by lazy { OkHttpClient() }

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
        // Дешёвая проверка до разбора: у почти всех событий поля нет.
        if (!originalJson.contains("\"$COMMENT_REF_FIELD\"")) return@runCatching null
        val comment = json.parseToJsonElement(originalJson).jsonObject["content"]?.jsonObject
            ?.get(COMMENT_REF_FIELD)?.jsonObject ?: return@runCatching null
        val room = comment["room"]?.jsonPrimitive?.content ?: return@runCatching null
        val id = comment["id"]?.jsonPrimitive?.content ?: return@runCatching null
        room to id
    }.getOrNull()

    /** The commentId a mirror message in the discussion group carries, or null. */
    fun commentIdFromMirror(originalJson: String): String? = runCatching {
        if (!originalJson.contains("\"$COMMENT_ID_FIELD\"")) return@runCatching null
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
        sharedDiscussionRoomId(room.roomId, matrixClient)?.let {
            cacheDiscussion(matrixClient, room.roomId, it)
            return it
        }
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

    /** Shared room state is visible to every channel member, including before the first post. */
    private suspend fun sharedDiscussionRoomId(channelId: RoomId, client: MatrixClient): RoomId? = withContext(Dispatchers.IO) {
        runCatchingExceptions {
            val token = client.getAccessToken().getOrThrow()
            val url = client.homeserverUrl.toHttpUrl().newBuilder()
                .addPathSegments("_matrix/client/v3/rooms")
                .addPathSegment(channelId.value)
                .addPathSegment("state")
                .addPathSegment(DISCUSSION_STATE_TYPE)
                .addPathSegment("")
                .build()
            val request = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val content = json.parseToJsonElement(response.body.string()).jsonObject
                content["room"]?.jsonPrimitive?.content?.let(::RoomId)
            }
        }.getOrNull()
    }

    private suspend fun cacheDiscussion(matrixClient: MatrixClient, channelId: RoomId, discussionId: RoomId) {
        val raw = matrixClient.getAccountData(ACCOUNT_DATA_TYPE).getOrElse { return }
        val current = linkMap(raw)
        matrixClient.setAccountData(ACCOUNT_DATA_TYPE, encodeLinkMap(current + (channelId.value to discussionId.value)))
    }
}
