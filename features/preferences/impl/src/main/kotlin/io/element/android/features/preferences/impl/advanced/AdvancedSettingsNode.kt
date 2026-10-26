/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 * Правка форка (Larpgram): Element removed this in v26.10 (split settings into media/, moderation/,
 * location/, root/, developer/appsettings/). Restored as fork-owned copy: our TgAdvancedSettingsView
 * and TG-категории (SettingsCategoryView) still need one combined state.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.advanced

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.libraries.androidutils.system.openAppSettingsPage
import io.element.android.libraries.di.SessionScope

@ContributesNode(SessionScope::class)
@AssistedInject
class AdvancedSettingsNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    private val presenter: AdvancedSettingsPresenter,
) : Node(buildContext, plugins = plugins) {
    @Composable
    override fun View(modifier: Modifier) {
        val state = presenter.present()
        val context = LocalContext.current
        // Правка форка: экран «Дополнительно» форка (элементовский удалён в v26.10).
        TgAdvancedSettingsView(
            state = state,
            modifier = modifier,
            onBackClick = ::navigateUp,
            onOpenAppSettingsClick = context::openAppSettingsPage
        )
    }
}
