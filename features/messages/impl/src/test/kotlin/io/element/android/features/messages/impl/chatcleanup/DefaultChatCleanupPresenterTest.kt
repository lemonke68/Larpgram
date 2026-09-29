/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.chatcleanup

import app.cash.turbine.TurbineTestContext
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.chatcleanup.test.FakeChatCleanupService
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarDispatcher
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.room.RoomInfo
import io.element.android.libraries.matrix.api.room.powerlevels.RoomPowerLevels
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.room.FakeBaseRoom
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.libraries.matrix.test.room.aRoomInfo
import io.element.android.libraries.matrix.test.room.defaultRoomPowerLevelValues
import io.element.android.libraries.matrix.test.room.powerlevels.FakeRoomPermissions
import io.element.android.libraries.matrix.ui.saved.NoOpSavedMessages
import io.element.android.tests.testutils.test
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultChatCleanupPresenterTest {
    private val localPeer = MatrixUser(UserId("@alice:server.org"), displayName = "Alice")
    private val remotePeer = MatrixUser(UserId("@bob:other.org"), displayName = "Bob")

    @Test
    fun `a DM with someone on our server can be cleared and deleted for both`() = runTest {
        presenter(aRoomInfo(isDm = true, heroes = listOf(localPeer)), canSendState = true).test {
            // Права на отметку приходят асинхронно.
            val state = expectLatest { it.chatName == "Alice" && it.canClearForBoth }
            assertThat(state.chatKind).isEqualTo(ChatKind.Dm)
            assertThat(state.canClearForBoth).isTrue()
            assertThat(state.canDeleteForBoth).isTrue()
            assertThat(state.canClearHistory).isTrue()
            assertThat(state.canDeleteChat).isTrue()
        }
    }

    @Test
    fun `a DM with someone on another server is deleted only for us`() = runTest {
        presenter(aRoomInfo(isDm = true, heroes = listOf(remotePeer)), canSendState = true).test {
            val state = expectLatest { it.chatName == "Bob" && it.canClearForBoth }
            assertThat(state.canDeleteForBoth).isFalse()
            assertThat(state.canClearForBoth).isTrue()
        }
    }

    @Test
    fun `a channel has no clear history and a group has no for-both options`() = runTest {
        val channelLevels = RoomPowerLevels(values = defaultRoomPowerLevelValues().copy(eventsDefault = 50), users = persistentMapOf())
        presenter(aRoomInfo(name = "News", roomPowerLevels = channelLevels)).test {
            val state = expectLatest { it.chatKind == ChatKind.Channel }
            assertThat(state.canClearHistory).isFalse()
            assertThat(state.canDeleteChat).isTrue()
        }
        presenter(aRoomInfo(name = "Crew")).test {
            val state = expectLatest { it.chatName == "Crew" }
            assertThat(state.chatKind).isEqualTo(ChatKind.Group)
            assertThat(state.canClearForBoth).isFalse()
            assertThat(state.canDeleteForBoth).isFalse()
        }
    }

    @Test
    fun `saved messages can be cleared but not deleted`() = runTest {
        presenter(aRoomInfo(name = "Saved"), savedRoom = true).test {
            val state = expectLatest { it.chatKind == ChatKind.SavedMessages }
            assertThat(state.canClearHistory).isTrue()
            assertThat(state.canDeleteChat).isFalse()
        }
    }

    @Test
    fun `events go to the cleanup service`() = runTest {
        val service = FakeChatCleanupService()
        presenter(aRoomInfo(isDm = true, heroes = listOf(localPeer)), service = service).test {
            val state = expectLatest { it.chatName == "Alice" }
            state.eventSink(ChatCleanupEvent.ClearHistory(upToTs = 42, forBoth = false))
            state.eventSink(ChatCleanupEvent.DeleteChat(forBoth = true))
            state.eventSink(ChatCleanupEvent.DeleteChat(forBoth = false))
            this@runTest.runCurrent()
            assertThat(service.clearedHistory.value[A_ROOM_ID]).isEqualTo(42L)
            assertThat(service.deletedForBoth).containsExactly(A_ROOM_ID)
            assertThat(service.deletedChats).containsExactly(A_ROOM_ID)
        }
    }

    private fun presenter(
        roomInfo: RoomInfo,
        canSendState: Boolean = false,
        savedRoom: Boolean = false,
        service: FakeChatCleanupService = FakeChatCleanupService(),
    ) = DefaultChatCleanupPresenter(
        room = FakeJoinedRoom(
            baseRoom = FakeBaseRoom(
                roomId = A_ROOM_ID,
                initialRoomInfo = roomInfo,
                roomPermissions = FakeRoomPermissions(canSendState = { canSendState }),
            ),
        ),
        matrixClient = FakeMatrixClient(sessionId = UserId("@me:server.org")),
        chatCleanupService = service,
        savedMessages = NoOpSavedMessages(if (savedRoom) A_ROOM_ID else null),
        snackbarDispatcher = SnackbarDispatcher(),
    )

    private suspend fun TurbineTestContext<ChatCleanupState>.expectLatest(
        predicate: (ChatCleanupState) -> Boolean,
    ): ChatCleanupState {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }
}
