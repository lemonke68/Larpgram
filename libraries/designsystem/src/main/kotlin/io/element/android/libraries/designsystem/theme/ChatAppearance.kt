/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Larpgram chat appearance customization, fed from AppPreferencesStore at the app root
// (ElementThemeApp) and consumed deep in the timeline. Both locals default to the values that
// reproduce the pre-customization rendering, so any composable that reads them without a provider
// (previews, tests) looks exactly as before.

/**
 * Multiplier applied to message text, relative to the 16sp baseline. 1.0 keeps the current size.
 * The message body is drawn by the wysiwyg editor view, which takes its size from
 * `LocalTextStyle.fontSize`, so callers scale that style rather than any density.
 */
val LocalMessageTextScale = staticCompositionLocalOf { 1f }

/** Message bubble corner radius. Matches `TelegramBubbleShape.BUBBLE_RADIUS` when unset. */
val LocalChatBubbleRadius = staticCompositionLocalOf { 20.dp }

/**
 * User-chosen color of the outgoing ("Мои сообщения") bubble. `null` keeps the themed default
 * (`messageFromMeBackground`), so any composable reading it without a provider renders as before.
 */
val LocalOutgoingBubbleColor = staticCompositionLocalOf<Color?> { null }

/**
 * Content (text) color to use inside the outgoing bubble, derived from the chosen bubble color for
 * contrast. `null` means "use the theme default" — set only while an outgoing color override is
 * active, and read by the timeline text view in place of `textPrimary`.
 */
val LocalOutgoingBubbleContentColor = staticCompositionLocalOf<Color?> { null }

/**
 * Readable text color for [bubbleColor]: near-black on light bubbles, white on dark ones. Keeps
 * message text legible when the user picks an arbitrary bubble color regardless of app theme.
 */
fun contentColorForBubble(bubbleColor: Color): Color =
    if (bubbleColor.luminance() > 0.5f) Color(0xFF111116) else Color.White

/** Bounds shared by the settings sliders and the value mapping, kept in one place. */
object ChatAppearanceDefaults {
    const val TEXT_SIZE_MIN_SP = 12
    const val TEXT_SIZE_MAX_SP = 24
    const val BUBBLE_RADIUS_MIN_DP = 0
    const val BUBBLE_RADIUS_MAX_DP = 28

    /** 16sp baseline -> scale 1.0. */
    fun textScaleFor(sizeSp: Int): Float = sizeSp / 16f

    fun bubbleRadiusFor(radiusDp: Int): Dp = radiusDp.dp
}
