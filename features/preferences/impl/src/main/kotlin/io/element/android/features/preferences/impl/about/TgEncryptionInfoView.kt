/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.about

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.preferences.impl.R
import io.element.android.features.preferences.impl.root.tgSettingsPageColor
import io.element.android.libraries.designsystem.components.preferences.PreferencePage
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Text

/**
 * A plain-language explanation of what Larpgram's encryption and history escrow (variant B)
 * protect against and what they don't. Source of truth: docs/08-encryption.md.
 */
@Composable
fun TgEncryptionInfoView(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferencePage(
        modifier = modifier,
        onBackClick = onBackClick,
        title = stringResource(R.string.larpgram_encryption_info_title),
        containerColor = tgSettingsPageColor(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
            Paragraph(R.string.larpgram_encryption_info_intro)
            Paragraph(R.string.larpgram_encryption_info_history)
            Section(
                title = R.string.larpgram_encryption_info_protects_title,
                items = listOf(
                    R.string.larpgram_encryption_info_protects_server,
                    R.string.larpgram_encryption_info_protects_admin_db,
                    R.string.larpgram_encryption_info_protects_email,
                ),
            )
            Section(
                title = R.string.larpgram_encryption_info_limits_title,
                items = listOf(
                    R.string.larpgram_encryption_info_limits_live_admin,
                    R.string.larpgram_encryption_info_limits_weak_password,
                    R.string.larpgram_encryption_info_limits_server_recovery,
                    R.string.larpgram_encryption_info_limits_forgot_password,
                    R.string.larpgram_encryption_info_limits_metadata,
                ),
            )
        }
    }
}

@Composable
private fun Paragraph(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = ElementTheme.typography.fontBodyMdRegular,
        color = ElementTheme.colors.textPrimary,
    )
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun Section(@StringRes title: Int, items: List<Int>) {
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(title),
        style = ElementTheme.typography.fontBodyMdMedium,
        color = ElementTheme.colors.textPrimary,
    )
    Spacer(Modifier.height(8.dp))
    items.forEach { item ->
        Text(
            text = "• " + stringResource(item),
            style = ElementTheme.typography.fontBodyMdRegular,
            color = ElementTheme.colors.textSecondary,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
}

@PreviewsDayNight
@Composable
internal fun TgEncryptionInfoViewPreview() = ElementPreview {
    TgEncryptionInfoView(onBackClick = {})
}
