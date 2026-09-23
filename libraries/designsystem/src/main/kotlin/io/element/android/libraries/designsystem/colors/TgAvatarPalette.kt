/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.colors

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.element.android.compound.theme.ElementTheme

/**
 * Правка форка: палитра аватаров Telegram.
 *
 * Семь пар цветов для заглушки без фото (вертикальный градиент сверху вниз, белая буква) и
 * цвет имени отправителя того же индекса. Значения из исходников Telegram Android 12.10.3:
 * `ThemeColors.java` (`key_avatar_background*`, `key_avatar_background2*`,
 * `key_avatar_nameInMessage*`) с переопределениями из `day.attheme` и `night.attheme`.
 * Порядок как в `Theme.keys_avatar_background`: красный, оранжевый, фиолетовый, зелёный, голубой,
 * синий, розовый.
 */
object TgAvatarPalette {
    const val SIZE = 7

    data class Gradient(val top: Color, val bottom: Color)

    private val lightGradients = listOf(
        Gradient(Color(0xFFFF845E), Color(0xFFD45246)),
        Gradient(Color(0xFFFEBB5B), Color(0xFFF68136)),
        Gradient(Color(0xFFB694F9), Color(0xFF6C61DF)),
        Gradient(Color(0xFF9AD164), Color(0xFF46BA43)),
        Gradient(Color(0xFF5BCBE3), Color(0xFF359AD4)),
        Gradient(Color(0xFF5CAFFA), Color(0xFF408ACF)),
        Gradient(Color(0xFFFF8AAC), Color(0xFFD95574)),
    )

    private val darkGradients = listOf(
        Gradient(Color(0xFFDC805B), Color(0xFFD9614F)),
        Gradient(Color(0xFFF2BC64), Color(0xFFE58943)),
        Gradient(Color(0xFFB694F9), Color(0xFF6C61DF)),
        Gradient(Color(0xFF9AD164), Color(0xFF46BA43)),
        Gradient(Color(0xFF5BCBE3), Color(0xFF359AD4)),
        Gradient(Color(0xFF5CAFFA), Color(0xFF408ACF)),
        Gradient(Color(0xFFFF8AAC), Color(0xFFD95574)),
    )

    private val lightNameColors = listOf(
        Color(0xFFCC5049),
        Color(0xFFD67722),
        Color(0xFF836FE6),
        Color(0xFF40A920),
        Color(0xFF309EBA),
        Color(0xFF2586D6),
        Color(0xFFCB4F86),
    )

    private val darkNameColors = listOf(
        Color(0xFFE88878),
        Color(0xFFE8AB5A),
        Color(0xFF8C8DEA),
        Color(0xFF7DD071),
        Color(0xFF53CCC6),
        Color(0xFF5CADEA),
        Color(0xFFE8749A),
    )

    /** Стабильный индекс цвета для id пользователя или комнаты. */
    fun indexFor(id: String): Int = id.toHash(SIZE)

    fun gradient(id: String, isDark: Boolean): Gradient =
        (if (isDark) darkGradients else lightGradients)[indexFor(id)]

    fun nameColor(id: String, isDark: Boolean): Color =
        (if (isDark) darkNameColors else lightNameColors)[indexFor(id)]

    @Composable
    fun gradient(id: String): Gradient = gradient(id, isDark = !ElementTheme.isLightTheme)

    @Composable
    fun nameColor(id: String): Color = nameColor(id, isDark = !ElementTheme.isLightTheme)
}
