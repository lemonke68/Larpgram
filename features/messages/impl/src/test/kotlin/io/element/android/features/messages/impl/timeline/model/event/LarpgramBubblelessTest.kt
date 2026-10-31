/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.model.event

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LarpgramBubblelessTest {
    @Test
    fun `text content copies recalculate emoji classification`() {
        val emoji = aTimelineItemTextContent(body = "😀", formattedBody = "😀")
        assertTrue(emoji.isEmojiOnly)
        assertTrue(emoji.isBubbleless)
        assertFalse(emoji.copy(body = "пинг", formattedBody = "пинг").isEmojiOnly)
        assertFalse(emoji.copy(formattedBody = "other text").isEmojiOnly)
    }

    @Test
    fun `notice and emote classify the same text like ordinary messages`() {
        assertTrue(aTimelineItemNoticeContent(body = "ℹ️", formattedBody = "ℹ️").isEmojiOnly)
        assertTrue(aTimelineItemEmoteContent(body = "1️⃣", formattedBody = "1️⃣").isEmojiOnly)
        assertFalse(aTimelineItemNoticeContent(body = "notice", formattedBody = "notice").isEmojiOnly)
        assertFalse(aTimelineItemEmoteContent(body = "* user 😀", formattedBody = "* user 😀").isEmojiOnly)
    }

    @Test
    fun `space separated emoji and ordinary text preserve bubble rules`() {
        assertTrue(aTimelineItemTextContent(body = "😀 🙂", formattedBody = "😀 🙂").isBubbleless)
        assertFalse(aTimelineItemTextContent(body = "😀 text", formattedBody = "😀 text").isBubbleless)
    }
}
