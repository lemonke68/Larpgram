/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.components.tg

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Text
import kotlinx.coroutines.delay

/**
 * Поле кода из письма: [length] клеток, как на экране кода в Telegram (`CodeFieldContainer`).
 * Клавиатура цифровая, вставка из буфера работает (лишнее отбрасывает вызывающий).
 */
@Composable
fun TgCodeField(
    code: String,
    onCodeChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    length: Int = 6,
    isError: Boolean = false,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused = LocalInspectionMode.current || interactionSource.collectIsFocusedAsState().value
    BasicTextField(
        modifier = modifier,
        value = code,
        onValueChange = onCodeChange,
        enabled = enabled,
        interactionSource = interactionSource,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.Done,
        ),
        decorationBox = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(length) { index ->
                    CodeCell(
                        digit = code.getOrNull(index),
                        isCurrent = isFocused && index == code.length,
                        isError = isError,
                    )
                }
            }
        },
    )
}

@Composable
private fun CodeCell(
    digit: Char?,
    isCurrent: Boolean,
    isError: Boolean,
) {
    val shape = RoundedCornerShape(10.dp)
    val borderColor = when {
        isError -> ElementTheme.colors.borderCriticalPrimary
        isCurrent -> ElementTheme.colors.bgAccentRest
        else -> ElementTheme.colors.borderInteractiveSecondary
    }
    Box(
        modifier = Modifier
            .size(width = 44.dp, height = 52.dp)
            .background(ElementTheme.colors.bgCanvasDefault, shape)
            .border(if (isCurrent || isError) 2.dp else 1.dp, borderColor, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (digit != null) {
            Text(
                text = digit.toString(),
                style = ElementTheme.typography.fontHeadingMdBold,
                color = ElementTheme.colors.textPrimary,
            )
        } else if (isCurrent) {
            BlinkingCursor()
        }
    }
}

@Composable
private fun BlinkingCursor() {
    var isVisible by remember { mutableStateOf(true) }
    LaunchedEffect(isVisible) {
        delay(500)
        isVisible = !isVisible
    }
    if (isVisible) {
        Spacer(
            modifier = Modifier
                .size(2.dp, 22.dp)
                .background(ElementTheme.colors.bgAccentRest, RoundedCornerShape(1.dp))
        )
    }
}

@PreviewsDayNight
@Composable
internal fun TgCodeFieldPreview() = ElementPreview {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TgCodeField(code = "123", onCodeChange = {})
        TgCodeField(code = "", onCodeChange = {}, isError = true)
    }
}
