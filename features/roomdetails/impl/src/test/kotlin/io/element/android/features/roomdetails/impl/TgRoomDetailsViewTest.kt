/*
 * Правка форка: тесты профиля чата в стиле TG.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

@file:OptIn(ExperimentalTestApi::class)

package io.element.android.features.roomdetails.impl

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.AndroidComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import io.element.android.features.roomdetails.impl.members.aRoomMember
import io.element.android.features.userprofile.api.UserProfileEvent
import io.element.android.features.userprofile.api.UserProfileState
import io.element.android.features.userprofile.shared.aUserProfileState
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.notification.CallIntent
import io.element.android.libraries.matrix.api.room.RoomMember
import io.element.android.libraries.matrix.api.room.RoomNotificationMode
import io.element.android.libraries.matrix.test.A_USER_ID
import io.element.android.libraries.mediaviewer.api.NoOpProfileSharedMediaSection
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.tests.testutils.EnsureNeverCalled
import io.element.android.tests.testutils.EnsureNeverCalledWithParam
import io.element.android.tests.testutils.EnsureNeverCalledWithTwoParams
import io.element.android.tests.testutils.EventsRecorder
import io.element.android.tests.testutils.clickOn
import io.element.android.tests.testutils.ensureCalledOnce
import io.element.android.tests.testutils.ensureCalledOnceWithParam
import io.element.android.tests.testutils.pressBack
import io.element.android.tests.testutils.robolectric.RobolectricTest
import org.junit.Test
import org.robolectric.annotation.Config

class TgRoomDetailsViewTest : RobolectricTest() {
    @Test
    fun `back button calls goBack`() = runAndroidComposeUiTest {
        ensureCalledOnce { callback ->
            setTgRoomDetailsView(goBack = callback)
            pressBack()
        }
    }

    @Test
    fun `dm - Chat tile goes back to the chat`() = runAndroidComposeUiTest {
        ensureCalledOnce { callback ->
            setTgRoomDetailsView(state = aDmState(), goBack = callback)
            clickOn(CommonStrings.larpgram_profile_action_chat)
        }
    }

    @Test
    fun `dm - Call tile starts an audio call`() = runAndroidComposeUiTest {
        ensureCalledOnceWithParam(CallIntent.AUDIO) { callback ->
            setTgRoomDetailsView(state = aDmState(), onJoinCallClick = callback)
            clickOn(CommonStrings.larpgram_profile_action_call)
        }
    }

    @Test
    fun `dm - Video tile starts a video call`() = runAndroidComposeUiTest {
        ensureCalledOnceWithParam(CallIntent.VIDEO) { callback ->
            setTgRoomDetailsView(state = aDmState(), onJoinCallClick = callback)
            clickOn(CommonStrings.common_video)
        }
    }

    @Test
    fun `sound tile mutes the room`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<RoomDetailsEvent>()
        setTgRoomDetailsView(
            state = aRoomDetailsState(
                roomNotificationSettings = aRoomNotificationSettings(mode = RoomNotificationMode.ALL_MESSAGES),
                eventSink = eventsRecorder,
            )
        )
        clickOn(CommonStrings.larpgram_profile_action_mute)
        eventsRecorder.assertSingle(RoomDetailsEvent.MuteNotification)
    }

    @Test
    fun `sound tile unmutes a muted room`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<RoomDetailsEvent>()
        setTgRoomDetailsView(
            state = aRoomDetailsState(
                roomNotificationSettings = aRoomNotificationSettings(mode = RoomNotificationMode.MUTE),
                eventSink = eventsRecorder,
            )
        )
        clickOn(CommonStrings.larpgram_profile_action_unmute)
        eventsRecorder.assertSingle(RoomDetailsEvent.UnmuteNotification)
    }

    @Test
    fun `group - Leave tile asks to leave`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<RoomDetailsEvent>()
        setTgRoomDetailsView(state = aRoomDetailsState(eventSink = eventsRecorder))
        clickOn(CommonStrings.larpgram_profile_action_leave)
        eventsRecorder.assertSingle(RoomDetailsEvent.LeaveRoom(needsConfirmation = true))
    }

    // Строки ниже шапки — элементы LazyColumn: нужен высокий экран, чтобы они были в композиции.
    @Config(qualifiers = "h1500dp")
    @Test
    fun `group - member row opens the member profile`() = runAndroidComposeUiTest {
        val member = aRoomMember(userId = A_USER_ID, displayName = "Alice", role = RoomMember.Role.Admin)
        ensureCalledOnceWithParam(A_USER_ID) { callback ->
            setTgRoomDetailsView(
                state = aRoomDetailsState(members = listOf(member)),
                onMemberClick = callback,
            )
            onNodeWithText("Alice").performClick()
        }
    }

    // Строки ниже шапки — элементы LazyColumn: нужен высокий экран, чтобы они были в композиции.
    @Config(qualifiers = "h1500dp")
    @Test
    fun `group - add members row invites people`() = runAndroidComposeUiTest {
        ensureCalledOnce { callback ->
            setTgRoomDetailsView(
                state = aRoomDetailsState(canInvite = true),
                invitePeople = callback,
            )
            clickOn(CommonStrings.larpgram_profile_add_members)
        }
    }

    // Строки ниже шапки — элементы LazyColumn: нужен высокий экран, чтобы они были в композиции.
    @Config(qualifiers = "h1500dp")
    @Test
    fun `channel - subscribers row opens the member list`() = runAndroidComposeUiTest {
        ensureCalledOnce { callback ->
            setTgRoomDetailsView(
                state = aRoomDetailsState(isChannel = true),
                openRoomMemberList = callback,
            )
            clickOn(R.string.screen_room_details_subscribers)
        }
    }

    @Test
    fun `edit icon opens room edition`() = runAndroidComposeUiTest {
        ensureCalledOnce { callback ->
            setTgRoomDetailsView(state = aRoomDetailsState(canEdit = true), onEditClick = callback)
            onNodeWithContentDescription(activity!!.getString(CommonStrings.action_edit)).performClick()
        }
    }

    @Test
    fun `menu - leave group asks to leave`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<RoomDetailsEvent>()
        setTgRoomDetailsView(state = aRoomDetailsState(eventSink = eventsRecorder))
        openMenu()
        clickOn(R.string.screen_room_details_leave_group)
        eventsRecorder.assertSingle(RoomDetailsEvent.LeaveRoom(needsConfirmation = true))
    }

    @Test
    fun `menu - notifications opens the notification settings`() = runAndroidComposeUiTest {
        ensureCalledOnce { callback ->
            setTgRoomDetailsView(openRoomNotificationSettings = callback)
            openMenu()
            clickOn(R.string.screen_room_details_notification_title)
        }
    }

    @Test
    fun `menu - report opens the report screen`() = runAndroidComposeUiTest {
        ensureCalledOnce { callback ->
            setTgRoomDetailsView(state = aRoomDetailsState(canReportRoom = true), onReportRoomClick = callback)
            openMenu()
            clickOn(CommonStrings.action_report_room)
        }
    }

    @Test
    fun `dm menu - block asks for confirmation`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<UserProfileEvent>()
        setTgRoomDetailsView(
            state = aDmState(dmOtherMemberDetailsState = aUserProfileState(eventSink = eventsRecorder)),
        )
        openMenu()
        clickOn(CommonStrings.larpgram_profile_menu_block)
        eventsRecorder.assertSingle(UserProfileEvent.BlockUser(needsConfirmation = true))
    }

    @Test
    fun `dm menu - delete chat asks to leave`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<RoomDetailsEvent>()
        setTgRoomDetailsView(state = aDmState(eventSink = eventsRecorder))
        openMenu()
        clickOn(R.string.screen_room_details_delete_chat)
        eventsRecorder.assertSingle(RoomDetailsEvent.LeaveRoom(needsConfirmation = true))
    }
}

private fun aDmState(
    dmOtherMemberDetailsState: UserProfileState? = null,
    eventSink: (RoomDetailsEvent) -> Unit = EventsRecorder(expectEvents = false),
) = aRoomDetailsState(
    roomType = RoomDetailsType.Dm(aRoomMember(userId = A_USER_ID, displayName = "Alice")),
    dmOtherMemberDetailsState = dmOtherMemberDetailsState,
    eventSink = eventSink,
)

private fun AndroidComposeUiTest<ComponentActivity>.openMenu() {
    onNodeWithContentDescription(activity!!.getString(CommonStrings.action_open_context_menu)).performClick()
}

private fun AndroidComposeUiTest<ComponentActivity>.setTgRoomDetailsView(
    state: RoomDetailsState = aRoomDetailsState(
        eventSink = EventsRecorder(expectEvents = false),
    ),
    goBack: () -> Unit = EnsureNeverCalled(),
    onEditClick: () -> Unit = EnsureNeverCalled(),
    onShareRoom: () -> Unit = EnsureNeverCalled(),
    openRoomMemberList: () -> Unit = EnsureNeverCalled(),
    openRoomNotificationSettings: () -> Unit = EnsureNeverCalled(),
    invitePeople: () -> Unit = EnsureNeverCalled(),
    openAvatarPreview: (name: String, url: String) -> Unit = EnsureNeverCalledWithTwoParams(),
    openAdminSettings: () -> Unit = EnsureNeverCalled(),
    onJoinCallClick: (CallIntent) -> Unit = EnsureNeverCalledWithParam(),
    onKnockRequestsClick: () -> Unit = EnsureNeverCalled(),
    onSecurityAndPrivacyClick: () -> Unit = EnsureNeverCalled(),
    onMemberClick: (UserId) -> Unit = EnsureNeverCalledWithParam(),
    onReportRoomClick: () -> Unit = EnsureNeverCalled(),
) {
    setContent {
        TgRoomDetailsView(
            state = state,
            sharedMedia = NoOpProfileSharedMediaSection,
            presence = null,
            goBack = goBack,
            onEditClick = onEditClick,
            onShareRoom = onShareRoom,
            openRoomMemberList = openRoomMemberList,
            openRoomNotificationSettings = openRoomNotificationSettings,
            invitePeople = invitePeople,
            openAvatarPreview = openAvatarPreview,
            openAdminSettings = openAdminSettings,
            onJoinCallClick = onJoinCallClick,
            onKnockRequestsClick = onKnockRequestsClick,
            onSecurityAndPrivacyClick = onSecurityAndPrivacyClick,
            onMemberClick = onMemberClick,
            onReportRoomClick = onReportRoomClick,
        )
    }
}
