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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.LarpgramType
import io.element.android.compound.tokens.larpgramColors
import io.element.android.libraries.designsystem.theme.components.Text

/**
 * Outlined form field of the redesign (V1 final, auth): 56 dp, radius 14, translucent fill, the
 * label sitting on the border; focused — 2 dp accent border and accent label. Shared by
 * [TgFormTextField] and [TgFormPasswordField].
 */
@Composable
internal fun TgOutlinedField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    supportingText: String?,
    isError: Boolean,
    enabled: Boolean,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val colors = larpgramColors
    val shape = RoundedCornerShape(14.dp)
    val borderColor = when {
        isError -> colors.danger
        isFocused -> colors.accent
        colors.isLight -> Color(0xFFDCD7E6)
        else -> Color(0x29FFFFFF)
    }
    val fill = if (colors.isLight) Color(0x8CFFFFFF) else colors.glassSurface
    Column(modifier = modifier) {
        Box(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(shape)
                    .background(fill)
                    .border(if (isFocused || isError) 2.dp else 1.dp, borderColor, shape)
                    .padding(start = 18.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    singleLine = true,
                    textStyle = LarpgramType.field.copy(color = ElementTheme.colors.textPrimary),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    visualTransformation = visualTransformation,
                    interactionSource = interactionSource,
                )
                trailing?.invoke()
            }
            Text(
                text = label,
                style = LarpgramType.fieldLabel,
                color = when {
                    isError -> colors.danger
                    isFocused -> colors.accentText
                    else -> ElementTheme.colors.textSecondary
                },
                maxLines = 1,
                modifier = Modifier
                    .offset(x = 14.dp)
                    .background(colors.background, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
        if (supportingText != null) {
            Text(
                text = supportingText,
                style = LarpgramType.caption,
                color = if (isError) colors.danger else ElementTheme.colors.textSecondary,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp),
            )
        }
    }
}
