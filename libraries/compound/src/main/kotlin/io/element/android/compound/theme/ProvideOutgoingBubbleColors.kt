/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.compound.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

/**
 * Правка форка: внутри своего (цветного) пузыря текст и иконки темы перекрашены в [contentColor],
 * контрастный к пузырю. Иначе вложенные элементы брали textSecondary/iconPrimary темы и
 * получалось серое на оранжевом: длительность голосового, «PDF • 1,5 КБ», голоса в опросе,
 * черный квадрат файла (аудит приложения A-017, A-020, A-021, A-038). Вторичный текст — тот же
 * цвет с прозрачностью, как в Telegram (`chat_outFileInfoText`), подложки — полупрозрачные.
 */
@Composable
fun ProvideOutgoingBubbleColors(
    contentColor: Color,
    content: @Composable () -> Unit,
) {
    val base = LocalCompoundColors.current
    val colors = remember(base, contentColor) {
        val secondary = contentColor.copy(alpha = 0.72f)
        base.copy(
            textPrimary = contentColor,
            textSecondary = secondary,
            textDisabled = contentColor.copy(alpha = 0.45f),
            iconPrimary = contentColor,
            iconSecondary = secondary,
            iconTertiary = contentColor.copy(alpha = 0.6f),
            iconQuaternary = contentColor.copy(alpha = 0.45f),
            bgSubtlePrimary = contentColor.copy(alpha = 0.18f),
            bgSubtleSecondary = contentColor.copy(alpha = 0.12f),
            borderInteractivePrimary = contentColor.copy(alpha = 0.5f),
            borderInteractiveSecondary = contentColor.copy(alpha = 0.3f),
        )
    }
    CompositionLocalProvider(LocalCompoundColors provides colors, content = content)
}
