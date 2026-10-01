/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.components.tg

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Text

/** Вторая по важности кнопка: та же форма, контур цвета акцента. */
@Composable
fun TgFormSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, ElementTheme.colors.bgAccentRest),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = ElementTheme.colors.textActionAccent),
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
    ) {
        Text(text = text, style = ElementTheme.typography.fontBodyLgMedium)
    }
}

@PreviewsDayNight
@Composable
internal fun TgFormSecondaryButtonPreview() = ElementPreview {
    TgFormSecondaryButton(text = "Зарегистрироваться", onClick = {})
}
