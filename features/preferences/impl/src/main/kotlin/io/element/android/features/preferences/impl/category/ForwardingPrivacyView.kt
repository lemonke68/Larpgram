/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.category

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.preferences.impl.R
import io.element.android.features.preferences.impl.root.TgSettingsColors
import io.element.android.features.preferences.impl.root.TgSettingsGroup
import io.element.android.features.preferences.impl.root.TgSettingsItem
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.ui.strings.CommonStrings

@Composable
internal fun ColumnScope.ForwardingPrivacyView(state: ForwardingPrivacyState?) {
    if (state == null) return
    val failed = state.allowed == null && !state.isBusy
    TgSettingsGroup {
        TgSettingsItem(
            title = stringResource(R.string.larpgram_forwarding_privacy_title),
            subtitle = stringResource(
                when {
                    failed -> R.string.larpgram_forwarding_privacy_error
                    state.isBusy -> R.string.larpgram_forwarding_privacy_loading
                    else -> R.string.larpgram_forwarding_privacy_link
                }
            ),
            iconVector = CompoundIcons.Forward(),
            color = TgSettingsColors.Purple,
            trailingContent = if (failed) {
                ListItemContent.Text(stringResource(CommonStrings.action_retry))
            } else {
                ListItemContent.Switch(checked = state.allowed == true, enabled = !state.isBusy)
            },
            onClick = { if (failed) state.retry() else state.change(state.allowed != true) },
        )
    }
    Text(
        text = stringResource(R.string.larpgram_forwarding_privacy_description),
        style = ElementTheme.typography.fontBodyXsRegular,
        color = ElementTheme.colors.textSecondary,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
    )
}
