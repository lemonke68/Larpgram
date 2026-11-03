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
import io.element.android.libraries.designsystem.components.dialogs.SingleSelectionDialog
import io.element.android.libraries.designsystem.components.dialogs.listOptionOf
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.theme.components.ListItemStyle
import io.element.android.libraries.designsystem.theme.components.ListSupportingText
import io.element.android.libraries.musicplayer.api.MusicAutoDownload

@Composable
internal fun ColumnScope.MediaStorageView(state: MediaStorageState?) {
    var confirmClear by remember { mutableStateOf(false) }
    if (state != null) {
        MusicAutoDownloadGroup(state)
    }
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

/** «Музыка в чатах»: когда качать треки заранее, чтобы в пузыре сразу были теги и обложка. */
@Composable
private fun ColumnScope.MusicAutoDownloadGroup(state: MediaStorageState) {
    var networkDialog by remember { mutableStateOf(false) }
    var sizeDialog by remember { mutableStateOf(false) }
    val settings = state.musicAutoDownload
    val networks = MusicAutoDownload.Network.entries
    val networkTitles = networks.map { stringResource(it.titleRes()) }
    val sizes = MusicAutoDownload.sizeOptions
    val sizeTitles = sizes.map { size -> size?.let(state.formatSize) ?: stringResource(R.string.larpgram_music_autodownload_no_limit) }
    TgSettingsGroup {
        TgSettingsItem(
            title = stringResource(R.string.larpgram_music_autodownload_network),
            color = TgSettingsColors.Blue,
            iconVector = CompoundIcons.Download(),
            trailingContent = ListItemContent.Text(networkTitles[networks.indexOf(settings.network)]),
            onClick = { networkDialog = true },
        )
        if (settings.network != MusicAutoDownload.Network.Never) {
            TgSettingsItem(
                title = stringResource(R.string.larpgram_music_autodownload_limit),
                color = TgSettingsColors.Blue,
                iconVector = CompoundIcons.Files(),
                trailingContent = ListItemContent.Text(sizeTitles[sizes.indexOf(settings.maxSizeBytes).coerceAtLeast(0)]),
                onClick = { sizeDialog = true },
            )
        }
    }
    ListSupportingText(text = stringResource(R.string.larpgram_music_autodownload_description))
    if (networkDialog) {
        SingleSelectionDialog(
            title = stringResource(R.string.larpgram_music_autodownload_network),
            options = listOptionOf(*networkTitles.toTypedArray()),
            initialSelection = networks.indexOf(settings.network),
            onSelectOption = { index ->
                networkDialog = false
                state.onMusicAutoDownloadChange(settings.copy(network = networks[index]))
            },
            onDismissRequest = { networkDialog = false },
        )
    }
    if (sizeDialog) {
        SingleSelectionDialog(
            title = stringResource(R.string.larpgram_music_autodownload_limit),
            options = listOptionOf(*sizeTitles.toTypedArray()),
            initialSelection = sizes.indexOf(settings.maxSizeBytes).takeIf { it >= 0 },
            onSelectOption = { index ->
                sizeDialog = false
                state.onMusicAutoDownloadChange(settings.copy(maxSizeBytes = sizes[index]))
            },
            onDismissRequest = { sizeDialog = false },
        )
    }
}

private fun MusicAutoDownload.Network.titleRes() = when (this) {
    MusicAutoDownload.Network.Never -> R.string.larpgram_music_autodownload_never
    MusicAutoDownload.Network.WifiOnly -> R.string.larpgram_music_autodownload_wifi
    MusicAutoDownload.Network.Always -> R.string.larpgram_music_autodownload_always
}
