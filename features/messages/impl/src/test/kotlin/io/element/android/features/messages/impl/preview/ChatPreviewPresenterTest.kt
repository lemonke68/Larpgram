/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.preview

import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.fixtures.aTimelineItemsFactoryCreator
import io.element.android.features.messages.impl.timeline.TimelineChannelComments
import io.element.android.features.messages.impl.timeline.protection.aTimelineProtectionState
import io.element.android.features.messages.test.timeline.FakeHtmlConverterProvider
import io.element.android.libraries.chatcleanup.test.FakeChatCleanupService
import io.element.android.libraries.matrix.api.timeline.MatrixTimelineItem
import io.element.android.libraries.matrix.api.timeline.Timeline
import io.element.android.libraries.matrix.test.A_UNIQUE_ID
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.libraries.matrix.test.timeline.FakeTimeline
import io.element.android.libraries.matrix.test.timeline.anEventTimelineItem
import io.element.android.libraries.matrix.ui.saved.NoOpSavedMessages
import io.element.android.tests.testutils.consumeItemsUntilPredicate
import io.element.android.tests.testutils.lambda.lambdaRecorder
import io.element.android.tests.testutils.lambda.value
import io.element.android.tests.testutils.test
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ChatPreviewPresenterTest {
    @Test
    fun `present - shows the timeline without marking anything as read`() = runTest {
        // markAsRead и sendReadReceipt у FakeTimeline по умолчанию падают: любой вызов провалит тест.
        val timeline = FakeTimeline(
            timelineItems = flowOf(listOf(MatrixTimelineItem.Event(A_UNIQUE_ID, anEventTimelineItem()))),
        )
        val presenter = createChatPreviewPresenter(timeline)
        presenter.test {
            val state = consumeItemsUntilPredicate { it.timelineItems.isNotEmpty() }.last()
            assertThat(state.timelineItems).hasSize(1)
            // В превью нельзя ни писать, ни реагировать.
            assertThat(state.timelineRoomInfo.userHasPermissionToSendMessage).isFalse()
            assertThat(state.timelineRoomInfo.userHasPermissionToSendReaction).isFalse()
        }
    }

    @Test
    fun `present - loadMore paginates backwards`() = runTest {
        val paginateLambda = lambdaRecorder<Timeline.PaginationDirection, Result<Boolean>> { Result.success(false) }
        val timeline = FakeTimeline().apply { this.paginateLambda = paginateLambda }
        val presenter = createChatPreviewPresenter(timeline)
        presenter.test {
            awaitItem().loadMore()
            testScheduler.advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }
        paginateLambda.assertions().isCalledOnce().with(value(Timeline.PaginationDirection.BACKWARDS))
    }

    private fun TestScope.createChatPreviewPresenter(timeline: FakeTimeline): ChatPreviewPresenter {
        val room = FakeJoinedRoom(liveTimeline = timeline)
        return ChatPreviewPresenter(
            room = room,
            timelineItemsFactoryCreator = aTimelineItemsFactoryCreator(),
            timelineProtectionPresenter = { aTimelineProtectionState() },
            timelineChannelComments = TimelineChannelComments(room, FakeMatrixClient()),
            chatCleanupService = FakeChatCleanupService(),
            savedMessages = NoOpSavedMessages(),
            htmlConverterProvider = FakeHtmlConverterProvider(),
            dispatchers = testCoroutineDispatchers(),
        )
    }
}
