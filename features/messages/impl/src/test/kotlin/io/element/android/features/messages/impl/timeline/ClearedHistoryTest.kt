/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline

import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.timeline.model.TimelineItem
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemHistoryClearedContent
import io.element.android.features.messages.impl.timeline.model.virtual.TimelineItemLoadingIndicatorModel
import io.element.android.libraries.matrix.api.core.UniqueId
import io.element.android.libraries.matrix.api.timeline.Timeline
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test

class ClearedHistoryTest {
    private val newMessage = aMessage(ts = 300)
    private val oldMessage = aMessage(ts = 100)
    private val daySeparator = aTimelineItemDaySeparator()
    private val loadingIndicator = TimelineItem.Virtual(
        id = UniqueId("loading"),
        model = TimelineItemLoadingIndicatorModel(Timeline.PaginationDirection.BACKWARDS, timestamp = 0),
    )

    @Test
    fun `without a mark the timeline is untouched`() {
        val items = listOf(newMessage, oldMessage, daySeparator, loadingIndicator)

        val result = items.applyClearedHistory(clearedUpTo = null)

        assertThat(result.items).isEqualTo(items)
        assertThat(result.reachedCutoff).isFalse()
    }

    @Test
    fun `messages up to the own mark are hidden and pagination stops there`() {
        val result = listOf(newMessage, oldMessage, daySeparator, loadingIndicator).applyClearedHistory(clearedUpTo = 100)

        assertThat(result.items).containsExactly(newMessage, daySeparator).inOrder()
        assertThat(result.reachedCutoff).isTrue()
        assertThat(result.markerTs).isNull()
    }

    @Test
    fun `a day whose messages are all hidden loses its separator`() {
        val result = listOf(oldMessage, daySeparator).applyClearedHistory(clearedUpTo = 100)

        assertThat(result.items).isEmpty()
    }

    @Test
    fun `before the loaded timeline reaches the mark it keeps paginating`() {
        val result = listOf(newMessage, loadingIndicator).applyClearedHistory(clearedUpTo = 100)

        assertThat(result.items).containsExactly(newMessage, loadingIndicator).inOrder()
        assertThat(result.reachedCutoff).isFalse()
    }

    @Test
    fun `the room mark from the other side hides everything older and itself`() {
        val marker = aMessage(ts = 200).copy(content = TimelineItemHistoryClearedContent)

        val result = listOf(newMessage, marker, oldMessage, loadingIndicator).applyClearedHistory(clearedUpTo = null)

        assertThat(result.items).containsExactly(newMessage).inOrder()
        assertThat(result.markerTs).isEqualTo(200L)
        assertThat(result.reachedCutoff).isTrue()
    }

    @Test
    fun `a message that is still sending is never hidden`() {
        val sending = aMessage(ts = 0).copy(eventId = null)

        val result = listOf(sending, oldMessage).applyClearedHistory(clearedUpTo = 100)

        assertThat(result.items).containsExactly(sending)
    }

    @Test
    fun `hidden events are removed from a group`() {
        val group = aGroupedEvents().copy(events = persistentListOf(aMessage(ts = 300), aMessage(ts = 50)))

        val result = listOf(group).applyClearedHistory(clearedUpTo = 100)

        assertThat((result.items.single() as TimelineItem.GroupedEvents).events.map { it.sentTimeMillis }).containsExactly(300L)
    }

    private fun aMessage(ts: Long) = aTimelineItemEvent().copy(sentTimeMillis = ts)
}
