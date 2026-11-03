/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.channelcomments

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UniqueId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.room.powerlevels.RoomPowerLevels
import io.element.android.libraries.matrix.api.room.powerlevels.RoomPowerLevelsValues
import io.element.android.libraries.matrix.api.timeline.MatrixTimelineItem
import io.element.android.libraries.matrix.api.timeline.item.event.TimelineItemDebugInfoProvider
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.room.FakeBaseRoom
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.libraries.matrix.test.room.aRoomInfo
import io.element.android.libraries.matrix.test.timeline.FakeTimeline
import io.element.android.libraries.matrix.test.timeline.aTimelineItemDebugInfo
import io.element.android.libraries.matrix.test.timeline.anEventTimelineItem
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test

class ChannelDiscussionTest {
    @Test
    fun `new reader discovers discussion from shared channel state before any posts`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{"room":"!discussion:example.org"}"""))
        server.start()
        try {
            val client = FakeMatrixClient(
                homeserverUrl = server.url("/").toString(),
                getAccountDataLambda = { Result.success(null) },
                getAccessTokenResult = { Result.success("test-token") },
            )
            val room = FakeJoinedRoom()
            assertThat(ChannelDiscussion.resolveDiscussionRoomId(room, client)?.value).isEqualTo("!discussion:example.org")
            val request = server.takeRequest()
            assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-token")
            assertThat(request.path).contains("/state/${ChannelDiscussion.DISCUSSION_STATE_TYPE}/")
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `concurrent mirrors cannot claim the same post twice and failed claim can retry`() {
        val key = "test-session/channel/post"
        assertThat(ChannelPostMirror.claimPost(key)).isTrue()
        assertThat(ChannelPostMirror.claimPost(key)).isFalse()
        ChannelPostMirror.releasePost(key)
        assertThat(ChannelPostMirror.claimPost(key)).isTrue()
        ChannelPostMirror.releasePost(key)
    }

    @Test
    fun `two overlapping sends mirror both distinct posts rather than the same latest post`() = runTest {
        val session = A_SESSION_ID
        val values = RoomPowerLevelsValues(0, 0, 0, 100, 0, 0, 0, 0, 0, 0, 0, 0)
        val info = aRoomInfo(
            roomPowerLevels = RoomPowerLevels(values, persistentMapOf()),
        )
        val events = (1..2).map { index ->
            val raw = """{"type":"m.room.message","content":{"msgtype":"m.image","body":"image$index"}}"""
            MatrixTimelineItem.Event(
                UniqueId("mirror-concurrent-$index"),
                anEventTimelineItem(
                    eventId = EventId("\$mirror-concurrent-$index"),
                    sender = UserId(session.value),
                    debugInfoProvider = TimelineItemDebugInfoProvider {
                        aTimelineItemDebugInfo(originalJson = raw)
                    },
                ),
            )
        }
        val channel = FakeJoinedRoom(
            baseRoom = FakeBaseRoom(initialRoomInfo = info),
            liveTimeline = FakeTimeline(
                timelineItems = MutableStateFlow(events),
            ),
        )
        val mirrored = mutableListOf<String>()
        val discussion = FakeJoinedRoom(
            sendRawEventResult = { _, content ->
                mirrored += content
                Result.success(Unit)
            },
        )
        val discussionId = RoomId("!concurrent-discussion:example.org")
        val client = FakeMatrixClient(
            getAccountDataLambda = { Result.success(ChannelDiscussion.encodeLinkMap(mapOf(channel.roomId.value to discussionId.value))) },
        ).apply { givenGetRoomResult(discussionId, discussion) }
        coroutineScope {
            val one = async { ChannelPostMirror.mirrorLastPost(channel, client, emptySet(), matches = ChannelPostMirror::isMirrorableMessage) }
            val two = async { ChannelPostMirror.mirrorLastPost(channel, client, emptySet(), matches = ChannelPostMirror::isMirrorableMessage) }
            one.await()
            two.await()
        }
        assertThat(mirrored.map { ChannelDiscussion.commentIdFromMirror("{\"content\":$it}") })
            .containsExactly("\$mirror-concurrent-1", "\$mirror-concurrent-2")
    }

    @Test
    fun `link map survives a round trip and garbage reads as empty`() {
        val map = mapOf("!channel:domain" to "!discussion:domain")
        assertThat(ChannelDiscussion.linkMap(ChannelDiscussion.encodeLinkMap(map))).isEqualTo(map)
        assertThat(ChannelDiscussion.linkMap(null)).isEmpty()
        assertThat(ChannelDiscussion.linkMap("not json")).isEmpty()
        assertThat(ChannelDiscussion.linkMap("""{"a":1}""")).isEmpty()
    }

    @Test
    fun `comment ref is read from the post content`() {
        val post = """{"content":{"body":"x","ru.mangokokos.larpgram.comment":{"room":"!d:domain","id":"c1"}}}"""
        assertThat(ChannelDiscussion.commentRefFromPost(post)).isEqualTo("!d:domain" to "c1")
        assertThat(ChannelDiscussion.discussionRoomFromPost(post)).isEqualTo("!d:domain")
    }

    @Test
    fun `incomplete or missing comment ref is null`() {
        assertThat(ChannelDiscussion.commentRefFromPost("""{"content":{"ru.mangokokos.larpgram.comment":{"room":"!d:domain"}}}""")).isNull()
        assertThat(ChannelDiscussion.commentRefFromPost("""{"content":{"body":"x"}}""")).isNull()
        assertThat(ChannelDiscussion.commentRefFromPost("""{"type":"m.room.message"}""")).isNull()
        assertThat(ChannelDiscussion.commentRefFromPost("not json")).isNull()
    }

    @Test
    fun `comment id is read from a mirror message`() {
        assertThat(ChannelDiscussion.commentIdFromMirror("""{"content":{"ru.mangokokos.larpgram.comment_id":"${'$'}e"}}""")).isEqualTo("${'$'}e")
        assertThat(ChannelDiscussion.commentIdFromMirror("""{"content":{}}""")).isNull()
        assertThat(ChannelDiscussion.commentIdFromMirror("[]")).isNull()
    }
}
