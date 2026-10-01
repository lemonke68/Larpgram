/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.components.tg

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Text

/** Главная кнопка шага: во всю ширину, цвета акцента, как кнопка «Продолжить» у Telegram. */
@Composable
fun TgFormPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showProgress: Boolean = false,
) {
    androidx.compose.material3.Button(
        onClick = onClick,
        enabled = enabled && !showProgress,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ElementTheme.colors.bgAccentRest,
            contentColor = Color.White,
            // Во время запроса кнопка остаётся цветной, меняется только содержимое.
            disabledContainerColor = if (showProgress) ElementTheme.colors.bgAccentRest else ElementTheme.colors.bgActionPrimaryDisabled,
            disabledContentColor = Color.White,
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
    ) {
        if (showProgress) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Color.White,
                strokeWidth = 2.dp,
            )
        } else {
            Text(text = text, style = ElementTheme.typography.fontBodyLgMedium)
        }
    }
}

@PreviewsDayNight
@Composable
internal fun TgFormPrimaryButtonPreview() = ElementPreview {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TgFormPrimaryButton(text = "Войти", onClick = {})
        TgFormPrimaryButton(text = "Войти", onClick = {}, enabled = false)
        TgFormPrimaryButton(text = "Войти", onClick = {}, showProgress = true)
    }
}
