/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.advanced

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.libraries.di.SessionScope

/**
 * Larpgram: экран «Настройки темы» — пресеты + тонкая настройка обоев/акцента/пузыря.
 * Отдельный от «Настроек чатов», чтобы главный экран не перегружать (как в Telegram).
 * Стейт общий с остальными настройками через [AdvancedSettingsPresenter].
 */
@ContributesNode(SessionScope::class)
@AssistedInject
class ChatThemeSettingsNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    private val presenter: AdvancedSettingsPresenter,
) : Node(buildContext, plugins = plugins) {
    @Composable
    override fun View(modifier: Modifier) {
        val state = presenter.present()
        ChatThemeSettingsView(
            state = state,
            modifier = modifier,
            onBackClick = ::navigateUp,
        )
    }
}
