/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.emoji.api.picker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import io.element.android.emojibasebindings.Emoji

/**
 * Правка форка: клавиатура эмодзи как в Telegram — панель на месте экранной клавиатуры. Одна
 * вертикальная лента с разделами по категориям (недавние первыми) и ряд значков категорий
 * сверху, который следует за прокруткой.
 *
 * Данные — то же [EmojiPickerState], что у пикера реакций.
 */
@Immutable
interface EmojiKeyboardRenderer {
    @Composable
    fun Render(
        state: EmojiPickerState,
        onSelectEmoji: (Emoji) -> Unit,
        modifier: Modifier = Modifier,
    )
}

object NoOpEmojiKeyboardRenderer : EmojiKeyboardRenderer {
    @Composable
    override fun Render(
        state: EmojiPickerState,
        onSelectEmoji: (Emoji) -> Unit,
        modifier: Modifier,
    ) = Unit
}
