/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.root

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.emoji.api.picker.EmojiPickerRenderer
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.matrix.ui.saved.SavedMessages
import kotlinx.coroutines.launch

@ContributesNode(SessionScope::class)
@AssistedInject
class PreferencesRootNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    private val presenter: PreferencesRootPresenter,
    private val emojiPickerRenderer: EmojiPickerRenderer,
    private val savedMessages: SavedMessages,
) : Node(buildContext, plugins = plugins) {
    interface Callback : Plugin {
        fun navigateToCategory(category: SettingsCategory)
        fun navigateToAddAccount()
        fun navigateToBugReport()
        fun navigateToAbout()
        fun navigateToDeveloperSettings()
        fun navigateToLabs()
        fun navigateToAdvancedSettings()
        fun navigateToUserProfile(matrixUser: MatrixUser)
        fun navigateToRoom(roomId: RoomId)
    }

    private val callback: Callback = callback()

    @Composable
    override fun View(modifier: Modifier) {
        val state = presenter.present()
        val coroutineScope = rememberCoroutineScope()
        PreferencesRootView(
            state = state,
            emojiPickerRenderer = emojiPickerRenderer,
            modifier = modifier,
            onBackClick = this::navigateUp,
            onAddAccountClick = callback::navigateToAddAccount,
            onOpenCategory = callback::navigateToCategory,
            onOpenUserProfile = callback::navigateToUserProfile,
            onOpenAbout = callback::navigateToAbout,
            onOpenRageShake = callback::navigateToBugReport,
            onOpenLabs = callback::navigateToLabs,
            onOpenDeveloperSettings = callback::navigateToDeveloperSettings,
            onOpenAdvancedSettings = callback::navigateToAdvancedSettings,
            // Правка форка: «Избранное» создаётся при первом открытии.
            onOpenSavedMessages = {
                coroutineScope.launch {
                    savedMessages.getOrCreate().onSuccess(callback::navigateToRoom)
                }
            },
        )
    }
}
