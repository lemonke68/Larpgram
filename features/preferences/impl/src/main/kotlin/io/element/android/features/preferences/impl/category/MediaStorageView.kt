/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.category

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.preferences.impl.R
import io.element.android.features.preferences.impl.root.TgSettingsColors
import io.element.android.features.preferences.impl.root.TgSettingsGroup
import io.element.android.features.preferences.impl.root.TgSettingsItem
import io.element.android.libraries.designsystem.components.dialogs.ConfirmationDialog
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.theme.components.ListItemStyle
import io.element.android.libraries.designsystem.theme.components.ListSupportingText

@Composable
internal fun ColumnScope.MediaStorageView(state: MediaStorageState?) {
    var confirmClear by remember { mutableStateOf(false) }
    TgSettingsGroup {
        val entries = listOf(
            R.string.larpgram_storage_photos to state?.usage?.images,
            R.string.larpgram_storage_videos to state?.usage?.videos,
            R.string.larpgram_storage_audio to state?.usage?.audio,
            R.string.larpgram_storage_other to state?.usage?.other,
            R.string.larpgram_storage_thumbnails to state?.usage?.thumbnails,
        )
        entries.forEach { (title, bytes) ->
            TgSettingsItem(
                title = stringResource(title),
                color = TgSettingsColors.Blue,
                iconVector = CompoundIcons.Folder(),
                trailingContent = ListItemContent.Text(bytes?.let { state?.formatSize?.invoke(it) } ?: "—"),
                onClick = {},
            )
        }
        TgSettingsItem(
            title = stringResource(R.string.larpgram_storage_images),
            color = TgSettingsColors.Blue,
            iconVector = CompoundIcons.Image(),
            trailingContent = ListItemContent.Text(state?.imageCache ?: "—"),
            onClick = {},
        )
        listOf(
            R.string.larpgram_storage_protected to state?.usage?.protectedBytes,
            R.string.larpgram_storage_database to state?.usage?.databaseBytes,
            R.string.larpgram_storage_overhead to state?.usage?.databaseOverheadBytes,
        ).forEach { (title, bytes) ->
            TgSettingsItem(
                title = stringResource(title),
                color = TgSettingsColors.Teal,
                iconVector = CompoundIcons.Folder(),
                trailingContent = ListItemContent.Text(bytes?.let { state?.formatSize?.invoke(it) } ?: "—"),
                onClick = {},
            )
        }
        if (state != null && state.usage != null && !state.isLoading && !state.isClearing) {
            TgSettingsItem(
                title = stringResource(R.string.larpgram_storage_clear_media),
                color = TgSettingsColors.Red,
                iconVector = CompoundIcons.Delete(),
                style = ListItemStyle.Destructive,
                onClick = { confirmClear = true },
            )
        }
    }
    ListSupportingText(text = stringResource(R.string.larpgram_storage_accounting_description))
    ListSupportingText(text = stringResource(R.string.larpgram_storage_quality_at_send))
    if (confirmClear && state != null) {
        ConfirmationDialog(
            title = stringResource(R.string.larpgram_storage_clear_media),
            content = stringResource(R.string.larpgram_storage_clear_media_description),
            submitText = stringResource(R.string.larpgram_storage_clear_media),
            onSubmitClick = {
                confirmClear = false
                state.clearMediaCache()
            },
            onDismiss = { confirmClear = false },
        )
    }
}
