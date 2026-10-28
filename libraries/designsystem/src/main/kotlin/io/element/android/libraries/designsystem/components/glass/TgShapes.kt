/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.components.glass

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * The user's own avatar: an organic, slightly irregular blob (`tokens.json` → `radius.ownAvatarShape`,
 * CSS `44% 56% 48% 52% / 50% 44% 56% 50%`). The only non-round avatar in the app.
 */
val TgOwnAvatarShape: Shape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            addRoundRect(
                RoundRect(
                    left = 0f,
                    top = 0f,
                    right = w,
                    bottom = h,
                    topLeftCornerRadius = CornerRadius(w * 0.44f, h * 0.50f),
                    topRightCornerRadius = CornerRadius(w * 0.56f, h * 0.44f),
                    bottomRightCornerRadius = CornerRadius(w * 0.48f, h * 0.56f),
                    bottomLeftCornerRadius = CornerRadius(w * 0.52f, h * 0.50f),
                )
            )
        }
        return Outline.Generic(path)
    }

    override fun toString() = "TgOwnAvatarShape"
}

/** Auth logo squircle (`radius.logoShape`), drawn rotated by −6°. */
val TgLogoShape: Shape = RoundedCornerShape(topStart = 38.dp, topEnd = 44.dp, bottomEnd = 40.dp, bottomStart = 46.dp)
