/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl

import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import io.element.android.features.preferences.api.PreferencesEntryPoint
import io.element.android.features.userprofile.api.UserProfileEntryPoint
import com.google.common.truth.Truth.assertThat
import io.element.android.features.home.api.HomeEntryPoint
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.services.analytics.test.FakeAnalyticsService
import io.element.android.tests.testutils.lambda.lambdaError
import io.element.android.tests.testutils.node.TestParentNode
import io.element.android.tests.testutils.robolectric.RobolectricTest
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultHomeEntryPointTest : RobolectricTest() {
    @Test
    fun `test node builder`() = runTest {
        val entryPoint = DefaultHomeEntryPoint()
        val parentNode = TestParentNode.create { buildContext, plugins ->
            HomeFlowNode(
                buildContext = buildContext,
                plugins = plugins,
                matrixClient = FakeMatrixClient(),
                preferencesEntryPoint = object : PreferencesEntryPoint {
                    override fun createNode(
                        parentNode: Node,
                        buildContext: BuildContext,
                        params: PreferencesEntryPoint.Params,
                        callback: PreferencesEntryPoint.Callback,
                    ): Node = lambdaError()

                    override fun createAppDeveloperSettingsNode(
                        parentNode: Node,
                        buildContext: BuildContext,
                        callback: PreferencesEntryPoint.DeveloperSettingsCallback,
                    ): Node = lambdaError()
                },
                userProfileEntryPoint = object : UserProfileEntryPoint {
                    override fun createNode(
                        parentNode: Node,
                        buildContext: BuildContext,
                        params: UserProfileEntryPoint.Params,
                        callback: UserProfileEntryPoint.Callback,
                    ): Node = lambdaError()
                },
                presenter = createHomePresenter(),
                inviteFriendsUseCase = { lambdaError() },
                analyticsService = FakeAnalyticsService(),
                acceptDeclineInviteView = { _, _, _, _ -> lambdaError() },
                directLogoutView = { _ -> lambdaError() },
                reportRoomEntryPoint = { _, _, _ -> lambdaError() },
                declineInviteAndBlockUserEntryPoint = { _, _, _ -> lambdaError() },
                changeRoomMemberRolesEntryPoint = { _, _, _, _ -> lambdaError() },
                leaveRoomRenderer = { _, _, _ -> lambdaError() },
                sessionCoroutineScope = backgroundScope,
            )
        }
        val callback = object : HomeEntryPoint.Callback {
            override fun navigateToRoom(roomId: RoomId, joinedRoom: JoinedRoom?) = lambdaError()
            override fun navigateToCreateRoom() = lambdaError()
            override fun navigateToCreateSpace() = lambdaError()
            override fun navigateToCreateChannel() = lambdaError()
            override fun navigateToSettings() = lambdaError()
            override fun navigateToSetUpRecovery() = lambdaError()
            override fun navigateToEnterRecoveryKey() = lambdaError()
            override fun navigateToRoomSettings(roomId: RoomId) = lambdaError()
            override fun navigateToBugReport() = lambdaError()
            override fun navigateToAddAccount() = lambdaError()
            override fun navigateToLinkNewDevice() = lambdaError()
            override fun navigateToSecureBackup() = lambdaError()
            override fun navigateToRoomNotificationSettings(roomId: RoomId) = lambdaError()
            override fun navigateToEvent(roomId: RoomId, eventId: EventId) = lambdaError()
        }
        val result = entryPoint.createNode(
            parentNode = parentNode,
            buildContext = BuildContext.root(null),
            callback = callback,
        )
        assertThat(result).isInstanceOf(HomeFlowNode::class.java)
        assertThat(result.plugins).contains(callback)
    }
}
