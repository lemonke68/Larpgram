/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.category

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.logout.api.direct.DirectLogoutEvents
import io.element.android.features.logout.api.direct.DirectLogoutView
import io.element.android.features.preferences.impl.root.PreferencesRootPresenter
import io.element.android.features.preferences.impl.root.SettingsCategory
import io.element.android.libraries.androidutils.browser.openUrlInChromeCustomTab
import io.element.android.libraries.architecture.NodeInputs
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.architecture.inputs
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.user.MatrixUser

@ContributesNode(SessionScope::class)
@AssistedInject
class SettingsCategoryNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    private val presenter: PreferencesRootPresenter,
    private val directLogoutView: DirectLogoutView,
) : Node(buildContext, plugins = plugins) {
    data class Inputs(val category: SettingsCategory) : NodeInputs

    interface Callback : Plugin {
        fun navigateToUserProfile(matrixUser: MatrixUser)
        fun navigateToAddAccount()
        fun navigateToLinkNewDevice()
        fun navigateToBlockedUsers()
        fun navigateToSecureBackup()
        fun navigateToLockScreenSettings()
        fun navigateToAnalyticsSettings()
        fun startSignOutFlow()
        fun startAccountDeactivationFlow()
    }

    private val inputs = inputs<Inputs>()
    private val callback: Callback = callback()

    private fun onManageAccountClick(
        activity: Activity,
        url: String?,
        isDark: Boolean,
    ) {
        url?.let {
            activity.openUrlInChromeCustomTab(
                null,
                darkTheme = isDark,
                url = it
            )
        }
    }

    @Composable
    override fun View(modifier: Modifier) {
        val state = presenter.present()
        val activity = requireNotNull(LocalActivity.current)
        val isDark = ElementTheme.isLightTheme.not()
        SettingsCategoryView(
            category = inputs.category,
            state = state,
            modifier = modifier,
            onBackClick = this::navigateUp,
            onOpenUserProfile = callback::navigateToUserProfile,
            onAddAccountClick = callback::navigateToAddAccount,
            onLinkNewDeviceClick = callback::navigateToLinkNewDevice,
            onOpenBlockedUsers = callback::navigateToBlockedUsers,
            onSecureBackupClick = callback::navigateToSecureBackup,
            onOpenLockScreenSettings = callback::navigateToLockScreenSettings,
            onOpenAnalytics = callback::navigateToAnalyticsSettings,
            onManageAccountClick = { onManageAccountClick(activity, it, isDark) },
            onSignOutClick = {
                if (state.directLogoutState.canDoDirectSignOut) {
                    state.directLogoutState.eventSink(DirectLogoutEvents.Logout(ignoreSdkError = false))
                } else {
                    callback.startSignOutFlow()
                }
            },
            onDeactivateClick = callback::startAccountDeactivationFlow,
        )

        directLogoutView.Render(state = state.directLogoutState)
    }
}
