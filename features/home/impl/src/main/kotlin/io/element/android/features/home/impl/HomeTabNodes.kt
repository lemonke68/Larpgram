/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl

import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import dev.zacsweers.metro.Inject
import io.element.android.features.home.api.HomeEntryPoint
import io.element.android.features.preferences.api.PreferencesEntryPoint
import io.element.android.features.userprofile.api.UserProfileEntryPoint
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.core.UserId

/**
 * Builds the nodes of the Telegram-style home tabs (Settings, Profile) and the edit-profile screen
 * opened from the profile tab. Kept out of Element's HomeFlowNode (аудит C-009).
 */
@Inject
class HomeTabNodes(
    private val preferencesEntryPoint: PreferencesEntryPoint,
    private val userProfileEntryPoint: UserProfileEntryPoint,
) {
    fun settings(
        parentNode: Node,
        buildContext: BuildContext,
        callback: HomeEntryPoint.Callback,
        onAtRootChange: (Boolean) -> Unit,
    ): Node = preferencesEntryPoint.createNode(
        parentNode = parentNode,
        buildContext = buildContext,
        params = PreferencesEntryPoint.Params(PreferencesEntryPoint.InitialTarget.Root, isTab = true),
        callback = preferencesCallback(callback, onAtRootChange),
    )

    fun profile(
        parentNode: Node,
        buildContext: BuildContext,
        sessionId: SessionId,
        callback: HomeEntryPoint.Callback,
        onEditProfileClick: () -> Unit,
    ): Node = userProfileEntryPoint.createNode(
        parentNode = parentNode,
        buildContext = buildContext,
        params = UserProfileEntryPoint.Params(userId = UserId(sessionId.value)),
        callback = object : UserProfileEntryPoint.Callback {
            override fun navigateToRoom(roomId: RoomId) = callback.navigateToRoom(roomId = roomId, eventId = null, joinedRoom = null)
            override fun navigateToSettings() = callback.navigateToSettings()
            override fun navigateToEditProfile() = onEditProfileClick()
        },
    )

    fun editProfile(
        parentNode: Node,
        buildContext: BuildContext,
        callback: HomeEntryPoint.Callback,
    ): Node = preferencesEntryPoint.createNode(
        parentNode = parentNode,
        buildContext = buildContext,
        params = PreferencesEntryPoint.Params(PreferencesEntryPoint.InitialTarget.EditProfile),
        callback = preferencesCallback(callback, onAtRootChange = null),
    )

    /** Один экран настроек поверх вкладок: «Почта» или «Устройства» из баннеров списка чатов. */
    fun settingsScreen(
        parentNode: Node,
        buildContext: BuildContext,
        callback: HomeEntryPoint.Callback,
        target: PreferencesEntryPoint.InitialTarget,
    ): Node = preferencesEntryPoint.createNode(
        parentNode = parentNode,
        buildContext = buildContext,
        params = PreferencesEntryPoint.Params(target),
        callback = preferencesCallback(callback, onAtRootChange = null),
    )

    private fun preferencesCallback(
        callback: HomeEntryPoint.Callback,
        onAtRootChange: ((Boolean) -> Unit)?,
    ) = object : PreferencesEntryPoint.Callback {
        override fun navigateToAddAccount() = callback.navigateToAddAccount()
        override fun navigateToLinkNewDevice() = callback.navigateToLinkNewDevice()
        override fun navigateToBugReport() = callback.navigateToBugReport()
        override fun navigateToSecureBackup() = callback.navigateToSecureBackup()
        override fun navigateToRoomNotificationSettings(roomId: RoomId) = callback.navigateToRoomNotificationSettings(roomId)
        override fun navigateToEvent(roomId: RoomId, eventId: EventId) = callback.navigateToEvent(roomId, eventId)
        override fun navigateToRoom(roomId: RoomId) = callback.navigateToRoom(roomId = roomId, eventId = null, joinedRoom = null)
        override fun onNestedNavigationStateChanged(isAtRoot: Boolean) {
            onAtRootChange?.invoke(isAtRoot)
        }
    }
}
