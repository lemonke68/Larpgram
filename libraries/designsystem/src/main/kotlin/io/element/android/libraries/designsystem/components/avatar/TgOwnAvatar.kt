/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.components.avatar

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import io.element.android.libraries.designsystem.components.avatar.internal.InitialOrImageAvatar
import io.element.android.libraries.designsystem.components.glass.TgOwnAvatarShape

/**
 * The user's own avatar in the organic blob shape (redesign V1 final): Profile, the bottom bar
 * and the account switcher. Everyone else stays round.
 */
@Composable
fun TgOwnAvatar(
    avatarData: AvatarData,
    modifier: Modifier = Modifier,
    size: Dp? = null,
    contentDescription: String? = null,
) {
    InitialOrImageAvatar(
        avatarData = avatarData,
        hideAvatarImage = false,
        forcedAvatarSize = size,
        avatarShape = TgOwnAvatarShape,
        contentDescription = contentDescription,
        modifier = modifier,
    )
}
