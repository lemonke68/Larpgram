/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.components.tg

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Text

/** Ссылка-действие цветом акцента: «Забыли пароль?», «Войти по QR-коду», повтор кода. */
@Composable
fun TgFormLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Text(
        text = text,
        style = ElementTheme.typography.fontBodyLgMedium,
        color = if (enabled) ElementTheme.colors.textActionAccent else ElementTheme.colors.textDisabled,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

@PreviewsDayNight
@Composable
internal fun TgFormLinkPreview() = ElementPreview {
    Column {
        TgFormLink(text = "Забыли пароль?", onClick = {})
        TgFormLink(text = "Отправить ещё раз через 42 с", onClick = {}, enabled = false)
    }
}
