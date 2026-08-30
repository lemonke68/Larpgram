/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.advanced

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.element.android.features.preferences.impl.R
import io.element.android.libraries.designsystem.components.preferences.PreferencePage
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.ChatWallpaperOption

@Composable
fun ChatThemeSettingsView(
    state: AdvancedSettingsState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferencePage(
        modifier = modifier,
        onBackClick = onBackClick,
        title = stringResource(R.string.screen_chat_theme_settings_title),
    ) {
        ChatThemeSection(state)
    }
}

@PreviewsDayNight
@Composable
internal fun ChatThemeSettingsViewPreview() = ElementPreview {
    ChatThemeSettingsView(
        state = aAdvancedSettingsState(
            chatWallpaperId = ChatWallpaperOption.Navy.id,
        ),
        onBackClick = {},
    )
}
