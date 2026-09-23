/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.components.avatar.internal

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import io.element.android.libraries.designsystem.components.avatar.AvatarData

@Composable
internal fun InitialLetterAvatar(
    avatarData: AvatarData,
    avatarShape: Shape,
    forcedAvatarSize: Dp?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    // Правка форка: заглушка как в Telegram — градиент и белые инициалы.
    TgGradientAvatar(
        id = avatarData.id,
        text = avatarData.initials,
        size = forcedAvatarSize ?: avatarData.size.dp,
        avatarShape = avatarShape,
        contentDescription = contentDescription,
        modifier = modifier
    )
}
