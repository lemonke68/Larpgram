/*
 * Правка форка: пустое «Избранное», как в Telegram (`ChatActivity`, пустой Saved Messages).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.messages.impl.timeline.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.floatingDateBadgeBackground
import io.element.android.libraries.matrix.ui.saved.SavedMessagesAvatar
import io.element.android.libraries.ui.strings.CommonStrings

/** Карточка на обоях: закладка, «Ваше Избранное» и три строки о том, зачем оно. */
@Composable
internal fun SavedMessagesEmptyView(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .padding(horizontal = 32.dp)
            .widthIn(max = 280.dp),
        shape = RoundedCornerShape(20.dp),
        color = ElementTheme.colors.floatingDateBadgeBackground,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.CenterHorizontally),
                imageVector = SavedMessagesAvatar.icon,
                contentDescription = null,
                tint = ElementTheme.colors.iconAccentPrimary,
            )
            Text(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                text = stringResource(CommonStrings.larpgram_saved_messages_empty_title),
                style = ElementTheme.typography.fontBodyLgMedium,
                color = ElementTheme.colors.textPrimary,
            )
            listOf(
                CommonStrings.larpgram_saved_messages_empty_forward,
                CommonStrings.larpgram_saved_messages_empty_files,
                CommonStrings.larpgram_saved_messages_empty_devices,
            ).forEach { line ->
                Text(
                    text = "•  " + stringResource(line),
                    style = ElementTheme.typography.fontBodyMdRegular,
                    color = ElementTheme.colors.textPrimary,
                )
            }
        }
    }
}

@PreviewsDayNight
@Composable
internal fun SavedMessagesEmptyViewPreview() = ElementPreview {
    SavedMessagesEmptyView()
}
