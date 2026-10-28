/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.components.glass

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import io.element.android.compound.tokens.larpgramColors
import kotlin.math.max

/**
 * Screen background of the redesign: flat `background` plus three large, very soft colour spots
 * (`colors.<theme>.backgroundGlow`: top-left, right-middle, bottom-left). They give the glass
 * something to show through. Not used on the chat screen, which has its own wallpaper.
 */
@Composable
fun Modifier.tgGlowBackground(): Modifier {
    val colors = larpgramColors
    val base = colors.background
    val glow = colors.backgroundGlow
    return drawBehind {
        drawRect(base)
        val r = max(size.width, size.height) * 0.62f
        val spots = listOf(
            Offset(size.width * 0.05f, size.height * 0.04f),
            Offset(size.width * 1.0f, size.height * 0.48f),
            Offset(size.width * 0.0f, size.height * 0.95f),
        )
        spots.zip(glow).forEach { (center, color) ->
            drawRect(
                Brush.radialGradient(
                    colors = listOf(color, color.copy(alpha = 0f)),
                    center = center,
                    radius = r,
                ),
            )
        }
    }
}
