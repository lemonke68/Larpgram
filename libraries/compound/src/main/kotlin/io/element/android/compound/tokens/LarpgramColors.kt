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
 * Larpgram palette, derived from the Telegram UI kit.
 *
 * The generated Compound tokens are left untouched: we only override the semantic colors on top of
 * them, so upstream regeneration keeps working. Two axes are changed:
 *
 * - the accent goes from Element green to Telegram blue;
 * - the surfaces go from Compound's blue-tinted greys to Telegram's neutral ones.
 *
 * Success colors deliberately stay green: in Telegram the delivery ticks are green, and they read
 * as "sent", not as "accent".
 */
object LarpgramPalette {
    /** Telegram blue, sampled from the unread badge in the kit. */
    val accentLight = Color(0xFF007EE5)
    val accentLightHovered = Color(0xFF0071CE)
    val accentLightPressed = Color(0xFF0063B7)
    val accentLightSubtle = Color(0xFFE7F2FB)

    /** Lifted for contrast against the dark surfaces. */
    val accentDark = Color(0xFF4187EB)
    val accentDarkHovered = Color(0xFF5E99F0)
    val accentDarkPressed = Color(0xFF7AACF4)
    val accentDarkSubtle = Color(0xFF14243A)

    /** Message bubbles. */
    val bubbleFromMeLight = Color(0xFFE1FEC6)
    val bubbleFromOtherLight = Color(0xFFFFFFFF)
    val bubbleFromMeDark = Color(0xFF313131)
    val bubbleFromOtherDark = Color(0xFF262628)

    /** Flat base of the chat wallpaper. The pattern on top of it comes later. */
    val chatWallpaperLight = Color(0xFFC4D0DF)
    val chatWallpaperDark = Color(0xFF222222)

    /** Delivery ticks. */
    val deliveredLight = Color(0xFF21C004)
    val deliveredDark = Color(0xFF21C004)
}

val larpgramColorsLight: SemanticColors = compoundColorsLight.copy(
    // Accent: Telegram blue.
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
    // Unread badge: solid blue with white text, the way Telegram draws it.
    bgBadgeAccent = LarpgramPalette.accentLight,
    textBadgeAccent = Color(0xFFFFFFFF),
    // Accent gradients follow the same hue so buttons stop being green.
    gradientActionStop1 = Color(0xFF5FB2F2),
    gradientActionStop2 = Color(0xFF2C97EC),
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
    gradientActionStop1 = Color(0xFF7AACF4),
    gradientActionStop2 = Color(0xFF5E99F0),
    gradientActionStop3 = LarpgramPalette.accentDark,
    gradientActionStop4 = Color(0xFF2C6FD0),
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
