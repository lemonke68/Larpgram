/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.channelcomments.ChannelDiscussion
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.UniqueId
import io.element.android.libraries.matrix.api.timeline.MatrixTimelineItem
import io.element.android.libraries.matrix.api.timeline.Timeline
import io.element.android.libraries.matrix.api.timeline.item.event.TimelineItemDebugInfoProvider
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.libraries.matrix.test.timeline.FakeTimeline
import io.element.android.libraries.matrix.test.timeline.aTimelineItemDebugInfo
import io.element.android.libraries.matrix.test.timeline.anEventTimelineItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class TimelineChannelCommentsTest {
    @Test
    fun `old mirror outside initial timeline window is found through pagination`() = runTest {
        val items = MutableStateFlow<List<MatrixTimelineItem>>(emptyList())
        val mirrorId = EventId("\$old-mirror")
        val raw = """{"content":{"${ChannelDiscussion.COMMENT_ID_FIELD}":"old-post"}}"""
        val timeline = FakeTimeline(timelineItems = items)
        var pages = 0
        timeline.paginateLambda = { direction ->
            assertThat(direction).isEqualTo(Timeline.PaginationDirection.BACKWARDS)
            pages++
            items.value = listOf(MatrixTimelineItem.Event(UniqueId("mirror"), anEventTimelineItem(
                eventId = mirrorId,
                debugInfoProvider = TimelineItemDebugInfoProvider { aTimelineItemDebugInfo(originalJson = raw) },
            )))
            Result.success(true)
        }
        val discussion = FakeJoinedRoom(liveTimeline = timeline)
        val client = FakeMatrixClient().apply { givenGetRoomResult(A_ROOM_ID, discussion) }
        val comments = TimelineChannelComments(FakeJoinedRoom(), client)
        assertThat(comments.findMirrorEventId(A_ROOM_ID, "old-post")).isEqualTo(mirrorId)
        assertThat(pages).isEqualTo(1)
    }
}
