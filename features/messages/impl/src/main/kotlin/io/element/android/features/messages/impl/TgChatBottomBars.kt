/*
 * Copyright (c) 2026 Larpgram.
 * Правка форка: нижние полосы чата Telegram: подписка на канал и заблокированный собеседник. Вынесено из `MessagesView.kt` (аудит C-009).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text

@Composable
internal fun ChannelSubscriberBar(
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(ElementTheme.colors.bgSubtleSecondary)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Telegram's channel bottom bar is a single centred pill. Tap toggles notifications;
        // the label and icon describe the action about to happen.
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .clickable(onClick = onToggleMute)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = if (isMuted) CompoundIcons.Notifications() else CompoundIcons.NotificationsOff(),
                contentDescription = null,
                tint = ElementTheme.colors.iconPrimary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(
                    id = if (isMuted) R.string.screen_channel_unmute else R.string.screen_channel_mute
                ),
                color = ElementTheme.colors.textPrimary,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

// Правка форка (роумлесс, ф4 блок): своя сторона TG-стены. Полоса вместо композера,
// тап снимает блок (unignoreUser).
@Composable
internal fun BlockedUserBar(
    onUnblock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(ElementTheme.colors.bgSubtleSecondary)
            .clickable(onClick = onUnblock)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(id = R.string.screen_room_unblock_user),
            color = ElementTheme.colors.textCriticalPrimary,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
