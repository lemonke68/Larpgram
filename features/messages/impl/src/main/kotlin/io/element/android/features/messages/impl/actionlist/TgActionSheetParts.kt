/*
 * Copyright (c) 2026 Larpgram.
 * Правка форка: части меню действий с сообщением Telegram. Вынесено из `ActionListView.kt` (аудит C-009).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.actionlist

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.components.messages.MessageDeliveryState
import io.element.android.libraries.designsystem.components.messages.MessageDeliveryTicks
import io.element.android.libraries.designsystem.theme.components.Text

/**
 * Правка форка: полупрозрачный грей телеграмного меню.
 *
 * Высокая, но не полная непрозрачность: сквозь меню должен угадываться размытый фон, но текст
 * действий обязан читаться. Значения подобраны на эмуляторе поверх `rememberBlurredBackdrop`.
 */
@Composable
@ReadOnlyComposable
internal fun larpgramActionSheetColor(): Color = if (ElementTheme.isLightTheme) {
    // Правка форка: прозрачнее по просьбе юзера — сквозь меню заметен размытый фон.
    Color(0xFFFFFFFF).copy(alpha = 0.72f)
} else {
    Color(0xFF1C1C1E).copy(alpha = 0.70f)
}

/** Строка «галочка + время отправки» в шапке меню действий. */
@Composable
internal fun DeliveryStatusRow(
    state: MessageDeliveryState,
    sentTime: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MessageDeliveryTicks(state = state)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = sentTime,
            style = ElementTheme.typography.fontBodyMdRegular,
            color = ElementTheme.colors.textSecondary,
        )
    }
}
