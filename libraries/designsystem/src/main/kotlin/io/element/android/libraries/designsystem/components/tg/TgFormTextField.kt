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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import io.element.android.libraries.designsystem.components.form.textFieldState
import io.element.android.libraries.designsystem.modifiers.bringIntoViewOnImeVisible
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextField
import io.element.android.libraries.designsystem.theme.components.TextFieldValidity

/**
 * Строка формы: подпись, под полем — подсказка или ошибка этого поля.
 *
 * Поля и кнопки этого файла — формы в стиле Telegram: экраны входа и настройки аккаунта (почта,
 * пароль).
 */
@Composable
fun TgFormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    error: String? = null,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
    contentType: ContentType? = null,
) {
    // Своё состояние поля: значение из презентера приходит с опозданием на кадр, и при быстром
    // наборе буквы двоятся или пропадают (так же сделано в LoginPasswordView).
    var fieldValue by textFieldState(stateValue = value)
    TextField(
        value = fieldValue,
        onValueChange = { text ->
            // Вставка из буфера может принести перевод строки.
            val sanitized = text.filterNot { it == '\n' || it == '\r' }
            fieldValue = sanitized
            onValueChange(sanitized)
        },
        label = label,
        supportingText = error ?: hint,
        validity = if (error != null) TextFieldValidity.Invalid else TextFieldValidity.None,
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier
            .fillMaxWidth()
            .bringIntoViewOnImeVisible()
            .semantics { if (contentType != null) this.contentType = contentType },
    )
}

@PreviewsDayNight
@Composable
internal fun TgFormTextFieldPreview() = ElementPreview {
    TgFormTextField(value = "vasya", onValueChange = {}, label = "Ник", hint = "Латинские буквы, цифры и _")
}
