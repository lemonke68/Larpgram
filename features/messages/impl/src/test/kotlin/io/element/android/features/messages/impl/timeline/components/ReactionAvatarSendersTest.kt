/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components

import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.timeline.model.anAggregatedReaction
import org.junit.Test

class ReactionAvatarSendersTest {
    @Test
    fun `in a DM the senders are always shown as avatars`() {
        val reaction = anAggregatedReaction(count = 2)
        val others = listOf(reaction, anAggregatedReaction(key = "🔥", count = 2), anAggregatedReaction(key = "😂", count = 2))

        assertThat(reactionAvatarSenders(reaction, others, isDm = true, isChannel = false)).isEqualTo(reaction.senders)
    }

    @Test
    fun `in a group avatars are shown while the message has at most three reactions`() {
        val thumbs = anAggregatedReaction(count = 2)
        val fire = anAggregatedReaction(key = "🔥", count = 1)

        assertThat(reactionAvatarSenders(thumbs, listOf(thumbs, fire), isDm = false, isChannel = false)).isEqualTo(thumbs.senders)
    }

    @Test
    fun `in a group a fourth reaction anywhere on the message switches to counts`() {
        val thumbs = anAggregatedReaction(count = 2)
        val fire = anAggregatedReaction(key = "🔥", count = 2)

        assertThat(reactionAvatarSenders(thumbs, listOf(thumbs, fire), isDm = false, isChannel = false)).isEmpty()
    }

    @Test
    fun `a channel always shows counts`() {
        val reaction = anAggregatedReaction(count = 1)

        assertThat(reactionAvatarSenders(reaction, listOf(reaction), isDm = false, isChannel = true)).isEmpty()
    }
}
