/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.components.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import io.element.android.compound.theme.ElementTheme

/**
 * Правка форка: «стеклянные» панели экрана чата, как в Telegram 12 — поле ввода, шапка,
 * плашки поверх обоев. Под панелью размытая лента (haze), сверху полупрозрачная заливка цвета
 * панели и тонкая светлая обводка.
 *
 * Цифры из исходников Telegram Android 12.10.3: `BlurredBackgroundColorProviderThemed` (заливка
 * `chat_messagePanelBackground` с альфой 0.76, обводка и тень по яркости фона),
 * `BlurredBackgroundDrawable` (обводка 1dp сверху и 2/3dp снизу).
 */
val LocalChatGlassState = staticCompositionLocalOf<HazeState?> { null }

/**
 * Правка форка: сколько места снизу экрана чата занимают плавающие панели (поле ввода). Лента
 * уходит под них, поэтому добавляет это к нижнему отступу списка и поднимает кнопки прокрутки.
 */
val LocalChatBottomOverlayHeight = compositionLocalOf { 0.dp }

object TgGlassDefaults {
    /** Скругление пилюли поля ввода (`ChatInputViewsContainer.INPUT_BUBBLE_RADIUS`). */
    val inputRadius: Dp = 22.dp

    /** Минимальная высота поля ввода и размер его кнопок (`ChatActivityEnterView.DEFAULT_HEIGHT`). */
    val inputHeight: Dp = 44.dp

    /** Отступ пилюли от краёв экрана и от низа (`ChatActivity`, `INPUT_BUBBLE_BOTTOM`). */
    val inputSideMargin: Dp = 7.dp
    val inputBottomMargin: Dp = 9.dp

    private val blurRadius: Dp = 16.dp
    private const val FILL_ALPHA = 0.76f

    @Composable
    fun panelColor(): Color = if (ElementTheme.isLightTheme) Color.White else Color(0xFF1E1E1F)

    @Composable
    internal fun style(): HazeStyle {
        val panel = panelColor()
        return HazeStyle(
            backgroundColor = panel,
            tint = HazeTint(panel.copy(alpha = FILL_ALPHA)),
            blurRadius = blurRadius,
            noiseFactor = 0f,
        )
    }

    /** Цвет иконок на стекле (`glass_defaultIcon`). */
    @Composable
    fun iconColor(): Color = if (ElementTheme.isLightTheme) Color(0x991B2227) else Color(0xA0FFFFFF)
}

/**
 * Правка форка: стеклянная подложка по форме [shape]. Если размывать нечего (нет
 * [LocalChatGlassState], превью, тесты) — непрозрачная заливка цвета панели.
 */
@Composable
fun Modifier.tgGlass(shape: Shape): Modifier {
    val hazeState = LocalChatGlassState.current
    val isLight = ElementTheme.isLightTheme
    val panel = TgGlassDefaults.panelColor()
    val stroke = if (isLight) {
        Brush.verticalGradient(listOf(Color.White, Color.White))
    } else {
        Brush.verticalGradient(listOf(Color(0x28FFFFFF), Color(0x14FFFFFF)))
    }
    val shadowModifier = if (isLight) {
        Modifier.shadow(elevation = 2.dp, shape = shape, ambientColor = Color(0x20000000), spotColor = Color(0x20000000))
    } else {
        Modifier
    }
    val fillModifier = if (hazeState != null) {
        Modifier.hazeEffect(state = hazeState, style = TgGlassDefaults.style())
    } else {
        Modifier.background(panel)
    }
    return this
        .then(shadowModifier)
        .clip(shape)
        .then(fillModifier)
        .border(width = 1.dp, brush = stroke, shape = shape)
}
