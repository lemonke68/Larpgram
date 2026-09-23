/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.colors

import androidx.compose.runtime.Composable
import io.element.android.compound.theme.AvatarColors

object AvatarColorsProvider {
    /**
     * Правка форка: цвета из палитры Telegram ([TgAvatarPalette]). `background` — верх градиента
     * заглушки, `foreground` — цвет имени того же индекса (имя отправителя в пузыре, в медиа-деталях).
     */
    @Composable
    fun provide(id: String): AvatarColors {
        return AvatarColors(
            background = TgAvatarPalette.gradient(id).top,
            foreground = TgAvatarPalette.nameColor(id),
        )
    }
}

internal fun String.toHash(maxSize: Int): Int {
    return toList().sumOf { it.code } % maxSize
}
