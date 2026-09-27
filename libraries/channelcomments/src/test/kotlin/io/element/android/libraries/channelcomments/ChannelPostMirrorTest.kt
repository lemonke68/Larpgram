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
        assertThat(ChannelPostMirror.isCircleEvent(message("m.video", ""","filename":"larpgram-circle-1.mp4""""))).isTrue()
        assertThat(ChannelPostMirror.isCircleEvent(message("m.video", ""","filename":"clip.mp4""""))).isFalse()
        assertThat(ChannelPostMirror.isCircleEvent(message("m.image", ""","filename":"larpgram-circle-1.mp4""""))).isFalse()
    }

    @Test
    fun `sticker is detected by the event type`() {
        assertThat(ChannelPostMirror.isStickerEvent("""{"type":"m.sticker","content":{}}""")).isTrue()
        assertThat(ChannelPostMirror.isStickerEvent(message("m.image"))).isFalse()
    }
}
