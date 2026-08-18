/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.compound.tokens

import androidx.compose.ui.graphics.Color
import io.element.android.compound.tokens.generated.SemanticColors
import io.element.android.compound.tokens.generated.compoundColorsDark
import io.element.android.compound.tokens.generated.compoundColorsLight

/**
 * Larpgram palette.
 *
 * The generated Compound tokens are left untouched: we only override the semantic colors on top of
 * them, so upstream regeneration keeps working.
 *
 * **Акцент фиолетовый, и это решение юзера от 2026-08-16:** свой цвет вместо телеграмного синего,
 * чтобы приложение узнавалось. Значение `#6949A8` снято пипеткой с его же экрана настроек
 * внешнего вида (`design/tg-ref/current/settings_appearance_dark.png`): цвет лежит там ровным
 * пятном, в отличие от пузырей в чате, которые полупрозрачны поверх обоев и на пробу дают
 * разное. Остальная шкала выведена от него по светлоте, тон и насыщенность те же.
 *
 * **Тёмная тема выверена по живому клиенту, светлая пока прикинута.** Скриншотов в светлой теме
 * нет ни одного, точная схема будет вместе с меню кастомизации, где цвет выбирает пользователь.
 */
object LarpgramPalette {
    /** Тот самый фиолетовый: на белом даёт контраст около 6:1, годится и для текста. */
    val accentLight = Color(0xFF6949A7)
    val accentLightHovered = Color(0xFF5B4091)
    val accentLightPressed = Color(0xFF4E367C)
    val accentLightSubtle = Color(0xFFEDE7F8)

    /** Осветлён, иначе на тёмном полотне кнопки и ссылки проваливаются. */
    val accentDark = Color(0xFF9072CA)
    val accentDarkHovered = Color(0xFFA790D5)
    val accentDarkPressed = Color(0xFFBFAEE0)
    val accentDarkSubtle = Color(0xFF291E3E)

    /** Message bubbles. Свой пузырь тёмной темы это ровно снятый с экрана фиолетовый. */
    val bubbleFromMeLight = Color(0xFFE4DBF5)
    val bubbleFromOtherLight = Color(0xFFFFFFFF)
    val bubbleFromMeDark = Color(0xFF6949A8)
    val bubbleFromOtherDark = Color(0xFF262628)

    /** Flat base of the chat wallpaper. The pattern on top of it comes later. */
    val chatWallpaperLight = Color(0xFFC4D0DF)
    val chatWallpaperDark = Color(0xFF222222)

    /**
     * Галочки.
     *
     * Раньше были зелёными в обеих темах, как в классическом телеграмном ките. С фиолетовым
     * пузырём зелёное на нём смотрится случайным, да и в живом клиенте на цветном пузыре
     * галочки белые. В светлой теме пузырь бледный, поэтому там галочки берут акцент.
     */
    val deliveredLight = accentLight
    val deliveredDark = Color(0xFFFFFFFF)
}

val larpgramColorsLight: SemanticColors = compoundColorsLight.copy(
    // Accent: фиолетовый Larpgram.
    bgAccentRest = LarpgramPalette.accentLight,
    bgAccentHovered = LarpgramPalette.accentLightHovered,
    bgAccentPressed = LarpgramPalette.accentLightPressed,
    bgAccentSelected = LarpgramPalette.accentLight.copy(alpha = 0.20f),
    bgAccentSubtle = LarpgramPalette.accentLightSubtle,
    borderAccentPrimary = LarpgramPalette.accentLight,
    borderAccentSubtle = LarpgramPalette.accentLight.copy(alpha = 0.40f),
    iconAccentPrimary = LarpgramPalette.accentLight,
    iconAccentTertiary = LarpgramPalette.accentLight,
    textActionAccent = LarpgramPalette.accentLight,
    textLinkExternal = LarpgramPalette.accentLight,
    // Unread badge: сплошной акцент с белым числом, как в Telegram.
    bgBadgeAccent = LarpgramPalette.accentLight,
    textBadgeAccent = Color(0xFFFFFFFF),
    // Градиенты в том же тоне, иначе кнопки остаются элементовскими.
    gradientActionStop1 = Color(0xFF9C81CF),
    gradientActionStop2 = Color(0xFF7F60BB),
    gradientActionStop3 = LarpgramPalette.accentLight,
    gradientActionStop4 = LarpgramPalette.accentLightPressed,
    // Шапка списка чатов красится именно этим градиентом, а не gradientAction.
    gradientSubtleStop1 = LarpgramPalette.accentLight.copy(alpha = 0.16f),
    gradientSubtleStop2 = LarpgramPalette.accentLight.copy(alpha = 0.12f),
    gradientSubtleStop3 = LarpgramPalette.accentLight.copy(alpha = 0.08f),
    gradientSubtleStop4 = LarpgramPalette.accentLight.copy(alpha = 0.05f),
    gradientSubtleStop5 = LarpgramPalette.accentLight.copy(alpha = 0.02f),
    // Surfaces: Telegram's neutral greys instead of Compound's blue-tinted ones.
    bgSubtlePrimary = Color(0xFFE1E1E4),
    bgSubtleSecondary = Color(0xFFF6F6F6),
    bgCanvasDisabled = Color(0xFFF6F6F6),
)

val larpgramColorsDark: SemanticColors = compoundColorsDark.copy(
    bgAccentRest = LarpgramPalette.accentDark,
    bgAccentHovered = LarpgramPalette.accentDarkHovered,
    bgAccentPressed = LarpgramPalette.accentDarkPressed,
    bgAccentSelected = LarpgramPalette.accentDark.copy(alpha = 0.20f),
    bgAccentSubtle = LarpgramPalette.accentDarkSubtle,
    borderAccentPrimary = LarpgramPalette.accentDark,
    borderAccentSubtle = LarpgramPalette.accentDark.copy(alpha = 0.40f),
    iconAccentPrimary = LarpgramPalette.accentDark,
    iconAccentTertiary = LarpgramPalette.accentDark,
    textActionAccent = LarpgramPalette.accentDark,
    textLinkExternal = LarpgramPalette.accentDark,
    bgBadgeAccent = LarpgramPalette.accentDark,
    textBadgeAccent = Color(0xFFFFFFFF),
    gradientActionStop1 = Color(0xFFAD97D8),
    gradientActionStop2 = Color(0xFF9C81CF),
    gradientActionStop3 = LarpgramPalette.accentDark,
    gradientActionStop4 = Color(0xFF583B91),
    gradientSubtleStop1 = LarpgramPalette.accentDark.copy(alpha = 0.16f),
    gradientSubtleStop2 = LarpgramPalette.accentDark.copy(alpha = 0.12f),
    gradientSubtleStop3 = LarpgramPalette.accentDark.copy(alpha = 0.08f),
    gradientSubtleStop4 = LarpgramPalette.accentDark.copy(alpha = 0.05f),
    gradientSubtleStop5 = LarpgramPalette.accentDark.copy(alpha = 0.02f),
    // Telegram's dark theme is neutral grey, Compound's is tinted towards blue.
    bgCanvasDefault = Color(0xFF1C1C1E),
    bgSubtlePrimary = Color(0xFF313131),
    bgSubtleSecondary = Color(0xFF262628),
    bgCanvasDisabled = Color(0xFF222222),
)
