/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.channelcomments

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChannelPostMirrorTest {
    private fun message(msgtype: String, extra: String = "") =
        """{"type":"m.room.message","content":{"msgtype":"$msgtype","body":"x"$extra}}"""

    private fun withFilename(name: String) = ",\"filename\":\"$name\""

    @Test
    fun `text post kinds are text messages`() {
        assertThat(ChannelPostMirror.isTextMessage(message("m.text"))).isTrue()
        assertThat(ChannelPostMirror.isTextMessage(message("m.notice"))).isTrue()
        assertThat(ChannelPostMirror.isTextMessage(message("m.emote"))).isTrue()
    }

    @Test
    fun `media, stickers and garbage are not text messages`() {
        assertThat(ChannelPostMirror.isTextMessage(message("m.image"))).isFalse()
        assertThat(ChannelPostMirror.isTextMessage("""{"type":"m.sticker","content":{"body":"x"}}""")).isFalse()
        assertThat(ChannelPostMirror.isTextMessage("not json")).isFalse()
        assertThat(ChannelPostMirror.isTextMessage(null)).isFalse()
    }

    @Test
    fun `media msgtypes are mirrorable, text is not`() {
        assertThat(ChannelPostMirror.isMirrorableMessage(message("m.image"))).isTrue()
        assertThat(ChannelPostMirror.isMirrorableMessage(message("m.file"))).isTrue()
        assertThat(ChannelPostMirror.isMirrorableMessage(message("m.text"))).isFalse()
    }

    @Test
    fun `circle is detected by the filename marker`() {
        assertThat(ChannelPostMirror.isCircleEvent(message("m.video", withFilename("larpgram-circle-1.mp4")))).isTrue()
        assertThat(ChannelPostMirror.isCircleEvent(message("m.video", withFilename("clip.mp4")))).isFalse()
        assertThat(ChannelPostMirror.isCircleEvent(message("m.image", withFilename("larpgram-circle-1.mp4")))).isFalse()
    }

    @Test
    fun `sticker is detected by the event type`() {
        assertThat(ChannelPostMirror.isStickerEvent("""{"type":"m.sticker","content":{}}""")).isTrue()
        assertThat(ChannelPostMirror.isStickerEvent(message("m.image"))).isFalse()
    }

    @Test
    fun `gallery is detected by msgtype or itemtypes`() {
        assertThat(ChannelPostMirror.isGalleryEvent(message("m.gallery"))).isTrue()
        assertThat(ChannelPostMirror.isGalleryEvent(message("m.image", ""","itemtypes":[]"""))).isTrue()
        assertThat(ChannelPostMirror.isGalleryEvent(message("m.image"))).isFalse()
        assertThat(ChannelPostMirror.isGalleryEvent(null)).isFalse()
    }

    @Test
    fun `only the first part of a Larpgram album is mirrored`() {
        assertThat(ChannelPostMirror.isAlbumTail(message("m.image", withFilename("larpgram-album-0a1b2c3d-1-3.jpg")))).isFalse()
        assertThat(ChannelPostMirror.isAlbumTail(message("m.image", withFilename("larpgram-album-0a1b2c3d-2-3.jpg")))).isTrue()
        assertThat(ChannelPostMirror.isAlbumTail(message("m.image", withFilename("photo.jpg")))).isFalse()
    }

    @Test
    fun `mirror content keeps the post, drops the reply relation and adds the comment id`() {
        val post = """{"content":{"msgtype":"m.text","body":"hi","m.relates_to":{"m.in_reply_to":{"event_id":"${'$'}x"}}}}"""
        val mirror = ChannelPostMirror.buildMirrorContent(post, "${'$'}post")!!
        assertThat(mirror).contains(""""body":"hi"""")
        assertThat(mirror).doesNotContain("m.relates_to")
        assertThat(ChannelDiscussion.commentIdFromMirror("""{"content":$mirror}""")).isEqualTo("${'$'}post")
        assertThat(ChannelPostMirror.buildMirrorContent("not json", "id")).isNull()
    }
}
