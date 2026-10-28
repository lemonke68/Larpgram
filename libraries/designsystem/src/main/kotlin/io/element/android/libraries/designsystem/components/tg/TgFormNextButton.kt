/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.components.tg

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.compound.tokens.larpgramColors
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Icon

/**
 * Telegram's "next" button of the auth screens (redesign V1 final): a 62 dp accent rounded square
 * (radius 22) with a white arrow, bottom right.
 */
@Composable
fun TgFormNextButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showProgress: Boolean = false,
) {
    val colors = larpgramColors
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = modifier
            .size(62.dp)
            .shadow(if (enabled) 12.dp else 0.dp, shape, ambientColor = colors.accent, spotColor = colors.accent.copy(alpha = 0.5f))
            .clip(shape)
            .background(if (enabled || showProgress) colors.accent else colors.accent.copy(alpha = 0.45f))
            .clickable(enabled = enabled && !showProgress, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (showProgress) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
        } else {
            Icon(imageVector = CompoundIcons.ArrowRight(), contentDescription = contentDescription, tint = Color.White)
        }
    }
}

@PreviewsDayNight
@Composable
internal fun TgFormNextButtonPreview() = ElementPreview {
    TgFormNextButton(contentDescription = "Войти", onClick = {})
}
