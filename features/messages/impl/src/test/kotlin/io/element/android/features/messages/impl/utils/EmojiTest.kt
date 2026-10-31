/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.utils

import com.sigpwned.emoji4j.core.data.GraphemeEntry
import com.sigpwned.emoji4j.core.util.Graphemes
import org.junit.Assert
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiTest {
    @Test
    fun `prefix check accepts every registered emoji sequence`() {
        Graphemes.getGraphemeData().graphemes
            .filter { it.type == GraphemeEntry.EMOJI_TYPE }
            .forEach { entry ->
                val sequences = listOf(entry.canonicalCodePointSequence) + entry.alternativeCodePointSequences.orEmpty()
                sequences.forEach { points ->
                    val text = String(points, 0, points.size)
                    assertTrue("${entry.name}: $text", text.containsOnlyEmojis())
                }
            }
    }

    @Test
    fun `ordinary text is not emoji only`() {
        listOf("пинг", "Hello", "Γεια", "你好", "こんにちは", "한글", "é", "1", "#", "*", "\n", "x😀", "😀x").forEach {
            Assert.assertFalse(it, it.containsOnlyEmojis())
        }
    }

    @Test
    fun validEmojis() {
        // Simple single/multiple single-codepoint emojis per string
        assertTrue("👍".containsOnlyEmojis())
        assertTrue("😀".containsOnlyEmojis())
        assertTrue("🙂🙁".containsOnlyEmojis())
        assertTrue("👁❤️🍝".containsOnlyEmojis()) // 👁 is a pictographic
        assertTrue("👨‍👩‍👦1️⃣🚀👳🏾‍♂️🪩".containsOnlyEmojis())
        assertTrue("🌍🌎🌏".containsOnlyEmojis())

        // Awkward multi-codepoint graphemes
        assertTrue("🧑‍🧑‍🧒‍🧒".containsOnlyEmojis())
        assertTrue("🏴‍☠".containsOnlyEmojis())
        assertTrue("👩🏿‍🔧".containsOnlyEmojis())

        Assert.assertFalse("".containsOnlyEmojis())
        Assert.assertFalse(" ".containsOnlyEmojis())
        Assert.assertFalse("🙂 🙁".containsOnlyEmojis())
        Assert.assertFalse(" 🙂 🙁 ".containsOnlyEmojis())
        Assert.assertFalse("Hello".containsOnlyEmojis())
        Assert.assertFalse("Hello 👋".containsOnlyEmojis())
    }
}
