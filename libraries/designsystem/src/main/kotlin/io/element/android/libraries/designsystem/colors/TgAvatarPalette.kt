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
import io.element.android.compound.tokens.larpgramExtraColorsDark
import io.element.android.compound.tokens.larpgramExtraColorsLight

/**
 * Правка форка: палитра аватаров Larpgram (редизайн V1 final, `tokens.json` → `avatarGradients`).
 *
 * Восемь пар цветов для заглушки без фото (линейный градиент под 140°, белые инициалы), одни и те
 * же в обеих темах. Цвет имени отправителя — из `senderNames` своей темы.
 */
object TgAvatarPalette {
    const val SIZE = 8

    /** [top] is the lighter, first stop of the 140° gradient, [bottom] the darker second one. */
    data class Gradient(val top: Color, val bottom: Color)

    private val gradients = listOf(
        // violet, blue, pink, green, orange, teal, cyan, rose
        Gradient(Color(0xFFB69DF8), Color(0xFF6D4BD8)),
        Gradient(Color(0xFF8FD3FF), Color(0xFF3D7FD6)),
        Gradient(Color(0xFFFF8AAC), Color(0xFFD95574)),
        Gradient(Color(0xFF9AD164), Color(0xFF46BA43)),
        Gradient(Color(0xFFFEBB5B), Color(0xFFF68136)),
        Gradient(Color(0xFF53EDD6), Color(0xFF28C9B7)),
        Gradient(Color(0xFF5BCBE3), Color(0xFF359AD4)),
        Gradient(Color(0xFFFF8AAC), Color(0xFFC2477A)),
    )

    /** Стабильный индекс цвета для id пользователя или комнаты. */
    fun indexFor(id: String): Int = id.toHash(SIZE)

    @Suppress("UNUSED_PARAMETER")
    fun gradient(id: String, isDark: Boolean): Gradient = gradients[indexFor(id)]

    fun nameColor(id: String, isDark: Boolean): Color {
        val names = (if (isDark) larpgramExtraColorsDark else larpgramExtraColorsLight).senderNames
        return names[id.toHash(names.size)]
    }

    @Composable
    fun gradient(id: String): Gradient = gradient(id, isDark = !ElementTheme.isLightTheme)

    @Composable
    fun nameColor(id: String): Color = nameColor(id, isDark = !ElementTheme.isLightTheme)
}
