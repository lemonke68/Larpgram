/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.components.tg

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.components.form.textFieldState
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Text
import kotlinx.coroutines.delay

/**
 * Ввод кода из письма: клетки, ошибка под ними и «Отправить ещё раз» с обратным отсчётом. Отсчёт
 * перезапускается, когда растёт [codeSentCount].
 */
@Composable
fun TgFormCodeEntry(
    code: String,
    onCodeChange: (String) -> Unit,
    onResendClick: () -> Unit,
    resendText: String,
    resendInText: @Composable (seconds: Int) -> String,
    resendAfterSeconds: Int,
    codeSentCount: Int,
    isBusy: Boolean,
    modifier: Modifier = Modifier,
    length: Int = 6,
    error: String? = null,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    var secondsLeft by remember(codeSentCount) { mutableIntStateOf(resendAfterSeconds) }
    LaunchedEffect(codeSentCount) {
        while (secondsLeft > 0) {
            delay(1_000)
            secondsLeft--
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        var fieldValue by textFieldState(stateValue = code)
        TgCodeField(
            code = fieldValue,
            onCodeChange = { text ->
                fieldValue = text.filter { it.isDigit() }.take(length)
                onCodeChange(fieldValue)
            },
            length = length,
            isError = error != null,
            // Поле не выключаем на время запроса: выключенное теряет фокус, и клавиатура прячется
            // после каждой неверной попытки. Ввод во время запроса игнорирует презентер.
            modifier = Modifier.focusRequester(focusRequester),
        )
        if (error != null) {
            Text(
                text = error,
                style = ElementTheme.typography.fontBodyMdRegular,
                color = ElementTheme.colors.textCriticalPrimary,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        TgFormLink(
            text = if (secondsLeft > 0) resendInText(secondsLeft) else resendText,
            onClick = onResendClick,
            enabled = secondsLeft == 0 && !isBusy,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@PreviewsDayNight
@Composable
internal fun TgFormCodeEntryPreview() = ElementPreview {
    TgFormCodeEntry(
        code = "123",
        onCodeChange = {},
        onResendClick = {},
        resendText = "Отправить код ещё раз",
        resendInText = { "Отправить ещё раз через $it с" },
        resendAfterSeconds = 42,
        codeSentCount = 1,
        isBusy = false,
        error = "Неверный код",
    )
}
