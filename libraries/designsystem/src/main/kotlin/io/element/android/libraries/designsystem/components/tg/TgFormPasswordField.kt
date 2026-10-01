/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.components.tg

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import io.element.android.libraries.designsystem.components.form.textFieldState
import io.element.android.libraries.designsystem.modifiers.bringIntoViewOnImeVisible
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.PasswordVisibilityToggle
import io.element.android.libraries.designsystem.theme.components.TextField
import io.element.android.libraries.designsystem.theme.components.TextFieldValidity

@Composable
fun TgFormPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    error: String? = null,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
    isNewPassword: Boolean = false,
) {
    var isVisible by remember { mutableStateOf(false) }
    var fieldValue by textFieldState(stateValue = value)
    TextField(
        value = fieldValue,
        onValueChange = { text ->
            val sanitized = text.filterNot { it == '\n' || it == '\r' }
            fieldValue = sanitized
            onValueChange(sanitized)
        },
        label = label,
        supportingText = error ?: hint,
        validity = if (error != null) TextFieldValidity.Invalid else TextFieldValidity.None,
        enabled = enabled,
        singleLine = true,
        // Пока идёт запрос, пароль прячем: поле выключено, глазок не нажать.
        visualTransformation = if (isVisible && enabled) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            PasswordVisibilityToggle(visible = isVisible, onToggle = { isVisible = !isVisible })
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier
            .fillMaxWidth()
            .bringIntoViewOnImeVisible()
            .semantics { contentType = if (isNewPassword) ContentType.NewPassword else ContentType.Password },
    )
}

@PreviewsDayNight
@Composable
internal fun TgFormPasswordFieldPreview() = ElementPreview {
    TgFormPasswordField(value = "password", onValueChange = {}, label = "Пароль", error = "Пароль слишком простой")
}
