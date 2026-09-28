/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.channelcomments

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChannelDiscussionTest {
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
