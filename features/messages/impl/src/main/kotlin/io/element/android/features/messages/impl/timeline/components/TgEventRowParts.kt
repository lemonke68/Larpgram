/*
 * Copyright (c) 2026 Larpgram.
 * Правка форка: аватар и имя отправителя в пузыре Telegram. Вынесено из `TgTimelineItemEventRow.kt` (аудит C-009).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.unit.dp
import io.element.android.libraries.designsystem.colors.AvatarColorsProvider
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.timeline.Timeline
import io.element.android.libraries.matrix.api.timeline.item.event.ProfileDetails
import io.element.android.libraries.matrix.ui.messages.sender.SenderName
import io.element.android.libraries.matrix.ui.messages.sender.SenderNameMode
import io.element.android.libraries.testtags.TestTags
import io.element.android.libraries.testtags.testTag

@Composable
internal fun MessageSenderAvatar(
    senderAvatar: AvatarData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Avatar(
        modifier = modifier
            .testTag(TestTags.timelineItemSenderAvatar)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                hideFromAccessibility()
            },
        avatarData = senderAvatar,
        avatarType = AvatarType.User,
    )
}

/** Правка форка: имя отправителя рисуется первой строкой внутри пузыря, цветом его аватара. */
@Composable
internal fun BubbleSenderName(
    senderId: UserId,
    senderProfile: ProfileDetails,
    senderAvatar: AvatarData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val avatarColors = AvatarColorsProvider.provide(senderAvatar.id)
    SenderName(
        modifier = modifier
            .testTag(TestTags.timelineItemSenderName)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        senderId = senderId,
        senderProfile = senderProfile,
        senderNameMode = SenderNameMode.Timeline(avatarColors.foreground),
    )
}
