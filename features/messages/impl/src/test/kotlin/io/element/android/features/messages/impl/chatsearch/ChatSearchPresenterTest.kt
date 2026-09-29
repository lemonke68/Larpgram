/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.chatsearch

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.dateformatter.test.FakeDateFormatter
import io.element.android.libraries.eventformatter.test.FakeRoomLatestEventFormatter
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.search.MessageSearchResult
import io.element.android.libraries.matrix.api.timeline.item.event.MessageContent
import io.element.android.libraries.matrix.api.timeline.item.event.ProfileDetails
import io.element.android.libraries.matrix.api.timeline.item.event.TextMessageType
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.A_USER_ID
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.room.FakeBaseRoom
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.libraries.matrix.test.search.FakeMessageSearch
import io.element.android.libraries.matrix.test.search.FakeMessageSearchService
import io.element.android.tests.testutils.test
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ChatSearchPresenterTest {
    private val search = FakeMessageSearch()
    private val service = FakeMessageSearchService(search)
    private val formatter = FakeRoomLatestEventFormatter().apply { givenFormatResult("Zebra banana") }

    @Test
    fun `the search is scoped to the room and results of other rooms are dropped`() = runTest {
        presenter().test {
            val initial = awaitItem()
            assertThat(service.lastRoomId).isEqualTo(A_ROOM_ID)
            initial.query.setTextAndPlaceCursorAtEnd("banana")
            search.emitResults(persistentListOf(aResult(A_ROOM_ID, "\$here"), aResult(RoomId("!other:server"), "\$there")))
            val withResults = expectLatest { it.results.isNotEmpty() }
            assertThat(withResults.results.map { it.eventId }).containsExactly(EventId("\$here"))
            assertThat(withResults.results.single().body).isEqualTo("Zebra banana")
            // Запрос уходит после паузы ввода (200 мс).
            delay(300)
            assertThat(search.lastQuery).isEqualTo("banana")
        }
    }

    @Test
    fun `without the local index the screen says search is unavailable`() = runTest {
        presenter(isAvailable = false).test {
            assertThat(awaitItem().isAvailable).isFalse()
        }
    }

    private fun presenter(isAvailable: Boolean = true) = ChatSearchPresenter(
        room = FakeJoinedRoom(baseRoom = FakeBaseRoom(roomId = A_ROOM_ID)),
        matrixClient = FakeMatrixClient(isMessageSearchAvailable = isAvailable),
        messageSearchService = service,
        latestEventFormatter = formatter,
        dateFormatter = FakeDateFormatter(),
    )

    private fun aResult(roomId: RoomId, eventId: String) = MessageSearchResult(
        roomId = roomId,
        eventId = EventId(eventId),
        senderId = A_USER_ID,
        senderProfile = ProfileDetails.Unavailable,
        content = MessageContent(
            body = "Zebra banana",
            inReplyTo = null,
            isEdited = false,
            threadInfo = null,
            type = TextMessageType("Zebra banana", null),
        ),
        timestamp = 0L,
    )

    private suspend fun app.cash.turbine.TurbineTestContext<ChatSearchState>.expectLatest(
        predicate: (ChatSearchState) -> Boolean,
    ): ChatSearchState {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }
}
