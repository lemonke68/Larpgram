/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalTestApi::class)

package io.element.android.features.preferences.impl.category

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.AndroidComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import io.element.android.features.preferences.impl.advanced.aAdvancedSettingsState
import io.element.android.features.preferences.impl.root.PreferencesRootState
import io.element.android.features.preferences.impl.root.SettingsCategory
import io.element.android.features.preferences.impl.root.aPreferencesRootState
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.matrix.ui.components.aMatrixUser
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.tests.testutils.EnsureNeverCalled
import io.element.android.tests.testutils.EnsureNeverCalledWithParam
import io.element.android.tests.testutils.ensureCalledOnce
import io.element.android.tests.testutils.ensureCalledOnceWithParam
import io.element.android.tests.testutils.pressBack
import io.element.android.tests.testutils.robolectric.RobolectricTest
import org.junit.Test

class SettingsCategoryViewTest : RobolectricTest() {
    @Test
    fun `clicking on back invokes back callback`() = runAndroidComposeUiTest {
        ensureCalledOnce { callback ->
            setView(SettingsCategory.Account, aPreferencesRootState(), onBackClick = callback)
            pressBack()
        }
    }

    @Test
    fun `Account - click on edit profile invokes callback`() = runAndroidComposeUiTest {
        val user = aMatrixUser()
        ensureCalledOnceWithParam(user) { callback ->
            setView(SettingsCategory.Account, aPreferencesRootState(myUser = user), onOpenUserProfile = callback)
            onNodeWithText("Изменить профиль").performClick()
        }
    }

    @Test
    fun `Account - click on manage account invokes callback`() = runAndroidComposeUiTest {
        ensureCalledOnceWithParam("aUrl") { callback ->
            setView(SettingsCategory.Account, aPreferencesRootState(accountManagementUrl = "aUrl"), onManageAccountClick = callback)
            clickOn(CommonStrings.action_manage_account_and_devices)
        }
    }

    @Test
    fun `Account - click on sign out invokes callback`() = runAndroidComposeUiTest {
        ensureCalledOnce { callback ->
            setView(SettingsCategory.Account, aPreferencesRootState(), onSignOutClick = callback)
            clickOn(CommonStrings.action_signout)
        }
    }

    @Test
    fun `Account - click on deactivate invokes callback`() = runAndroidComposeUiTest {
        ensureCalledOnce { callback ->
            setView(SettingsCategory.Account, aPreferencesRootState(canDeactivateAccount = true), onDeactivateClick = callback)
            clickOn(CommonStrings.action_delete_account)
        }
    }

    @Test
    fun `Privacy - click on screen lock invokes callback`() = runAndroidComposeUiTest {
        ensureCalledOnce { callback ->
            setView(SettingsCategory.Privacy, aPreferencesRootState(), onOpenLockScreenSettings = callback)
            clickOn(CommonStrings.common_screen_lock)
        }
    }

    @Test
    fun `Privacy - click on encryption invokes callback`() = runAndroidComposeUiTest {
        ensureCalledOnce { callback ->
            setView(SettingsCategory.Privacy, aPreferencesRootState(showSecureBackup = true), onSecureBackupClick = callback)
            clickOn(CommonStrings.common_encryption)
        }
    }

    @Test
    fun `Privacy - click on blocked users invokes callback`() = runAndroidComposeUiTest {
        ensureCalledOnce { callback ->
            setView(SettingsCategory.Privacy, aPreferencesRootState(nbOfBlockedUsers = 1), onOpenBlockedUsers = callback)
            clickOn(CommonStrings.common_blocked_users)
        }
    }

    @Test
    fun `Privacy - click on analytics invokes callback`() = runAndroidComposeUiTest {
        ensureCalledOnce { callback ->
            setView(SettingsCategory.Privacy, aPreferencesRootState(showAnalyticsSettings = true), onOpenAnalytics = callback)
            clickOn(CommonStrings.common_analytics)
        }
    }

    @Test
    fun `Devices - click on link new device invokes callback`() = runAndroidComposeUiTest {
        ensureCalledOnce { callback ->
            setView(SettingsCategory.Devices, aPreferencesRootState(showLinkNewDevice = true), onLinkNewDeviceClick = callback)
            clickOn(CommonStrings.common_link_new_device)
        }
    }
}

private fun AndroidComposeUiTest<ComponentActivity>.clickOn(resId: Int) {
    onNode(hasText(activity!!.getString(resId)) and hasClickAction()).performScrollTo().performClick()
}

private fun AndroidComposeUiTest<ComponentActivity>.setView(
    category: SettingsCategory,
    state: PreferencesRootState,
    onBackClick: () -> Unit = EnsureNeverCalled(),
    onOpenUserProfile: (MatrixUser) -> Unit = EnsureNeverCalledWithParam(),
    onAddAccountClick: () -> Unit = EnsureNeverCalled(),
    onLinkNewDeviceClick: () -> Unit = EnsureNeverCalled(),
    onOpenBlockedUsers: () -> Unit = EnsureNeverCalled(),
    onSecureBackupClick: () -> Unit = EnsureNeverCalled(),
    onOpenLockScreenSettings: () -> Unit = EnsureNeverCalled(),
    onOpenAnalytics: () -> Unit = EnsureNeverCalled(),
    onManageAccountClick: (url: String) -> Unit = EnsureNeverCalledWithParam(),
    onSignOutClick: () -> Unit = EnsureNeverCalled(),
    onDeactivateClick: () -> Unit = EnsureNeverCalled(),
) {
    setContent {
        SettingsCategoryView(
            category = category,
            state = state,
            advancedSettingsState = aAdvancedSettingsState(),
            onBackClick = onBackClick,
            onOpenUserProfile = onOpenUserProfile,
            onAddAccountClick = onAddAccountClick,
            onLinkNewDeviceClick = onLinkNewDeviceClick,
            onOpenBlockedUsers = onOpenBlockedUsers,
            onSecureBackupClick = onSecureBackupClick,
            onOpenLockScreenSettings = onOpenLockScreenSettings,
            onOpenAnalytics = onOpenAnalytics,
            onManageAccountClick = onManageAccountClick,
            onSignOutClick = onSignOutClick,
            onDeactivateClick = onDeactivateClick,
        )
    }
}
