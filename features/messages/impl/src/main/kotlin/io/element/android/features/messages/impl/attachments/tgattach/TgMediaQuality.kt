/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.attachments.tgattach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.element.android.features.messages.impl.R
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.mediaupload.api.MediaOptimizationConfig
import io.element.android.libraries.preferences.api.store.VideoCompressionPreset

/** Правка форка: качество выбирается для текущей отправки, одинаково в шторке и предпросмотре. */
internal fun mediaQualityConfig(highQuality: Boolean, sendAsFile: Boolean = false) = MediaOptimizationConfig(
    compressImages = !highQuality && !sendAsFile,
    videoCompressionPreset = if (highQuality || sendAsFile) VideoCompressionPreset.HIGH else VideoCompressionPreset.STANDARD,
    sendAsFile = sendAsFile,
)

@Composable
internal fun TgMediaQualitySelector(highQuality: Boolean, onSelect: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.larpgram_media_quality), modifier = Modifier.weight(1f))
        FilterChip(selected = !highQuality, onClick = { onSelect(false) }, label = { Text("SD") })
        FilterChip(selected = highQuality, onClick = { onSelect(true) }, label = { Text("HD") })
    }
}
