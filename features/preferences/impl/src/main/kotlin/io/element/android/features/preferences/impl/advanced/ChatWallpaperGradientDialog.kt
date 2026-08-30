/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package io.element.android.features.preferences.impl.advanced

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.preferences.impl.R
import io.element.android.libraries.designsystem.theme.ChatWallpaperGradient
import io.element.android.libraries.designsystem.theme.components.Slider
import io.element.android.libraries.designsystem.theme.components.Surface
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.ui.strings.CommonStrings
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Larpgram: редактор двухцветного линейного градиента обоев. Два цвета (каждый через тот же
 * HSV-пикер, что и остальные цвета) + угол ползунком, живое превью. OK отдаёт спеку строкой.
 */
@Composable
fun ChatWallpaperGradientDialog(
    initial: ChatWallpaperGradient?,
    onApply: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var startColor by remember { mutableStateOf(initial?.let { Color(it.startArgb) } ?: DEFAULT_GRADIENT_START) }
    var endColor by remember { mutableStateOf(initial?.let { Color(it.endArgb) } ?: DEFAULT_GRADIENT_END) }
    var angle by remember { mutableIntStateOf(initial?.angleDeg ?: DEFAULT_GRADIENT_ANGLE) }

    var editingStart by remember { mutableStateOf(false) }
    var editingEnd by remember { mutableStateOf(false) }

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = ElementTheme.colors.bgCanvasDefault,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(R.string.screen_chat_appearance_gradient_title),
                    style = ElementTheme.typography.fontHeadingSmMedium,
                    color = ElementTheme.colors.textPrimary,
                )

                // Живое превью градиента.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .padding(top = 16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .gradientBackground(startColor, endColor, angle),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    GradientColorButton(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.screen_chat_appearance_gradient_color_1),
                        color = startColor,
                        onClick = { editingStart = true },
                    )
                    GradientColorButton(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.screen_chat_appearance_gradient_color_2),
                        color = endColor,
                        onClick = { editingEnd = true },
                    )
                }

                Text(
                    modifier = Modifier.padding(top = 16.dp),
                    text = stringResource(R.string.screen_chat_appearance_gradient_angle, angle),
                    style = ElementTheme.typography.fontBodyMdRegular,
                    color = ElementTheme.colors.textSecondary,
                )
                Slider(
                    value = angle.toFloat(),
                    onValueChange = { angle = it.roundToInt() },
                    valueRange = 0f..360f,
                    steps = 23,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        text = stringResource(CommonStrings.action_cancel),
                        onClick = onDismiss,
                    )
                    TextButton(
                        text = stringResource(CommonStrings.action_ok),
                        onClick = {
                            onApply(ChatWallpaperGradient(startColor.toArgb(), endColor.toArgb(), angle).format())
                        },
                    )
                }
            }
        }
    }

    if (editingStart) {
        ChatWallpaperColorPickerDialog(
            initialColor = startColor,
            onColorSelected = {
                startColor = it
                editingStart = false
            },
            onDismiss = { editingStart = false },
        )
    }
    if (editingEnd) {
        ChatWallpaperColorPickerDialog(
            initialColor = endColor,
            onColorSelected = {
                endColor = it
                editingEnd = false
            },
            onDismiss = { editingEnd = false },
        )
    }
}

@Composable
private fun GradientColorButton(
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ElementTheme.colors.bgSubtlePrimary)
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(color)
                .border(width = 1.dp, color = ElementTheme.colors.borderInteractiveSecondary, shape = CircleShape),
        )
        Text(
            text = label,
            style = ElementTheme.typography.fontBodySmRegular,
            color = ElementTheme.colors.textPrimary,
        )
    }
}

/**
 * Фон-градиент вдоль угла (0° = слева направо, 90° = сверху вниз) для превью и свотчей. Считает
 * офсеты по реальному размеру (Brush.linearGradient принимает пиксели), поэтому годится любой размер.
 */
internal fun Modifier.gradientBackground(start: Color, end: Color, angleDeg: Int): Modifier = drawBehind {
    val rad = Math.toRadians(angleDeg.toDouble())
    val dx = cos(rad).toFloat()
    val dy = sin(rad).toFloat()
    val half = (abs(dx) * size.width + abs(dy) * size.height) / 2f
    val cx = size.width / 2f
    val cy = size.height / 2f
    drawRect(
        Brush.linearGradient(
            colors = listOf(start, end),
            start = Offset(cx - dx * half, cy - dy * half),
            end = Offset(cx + dx * half, cy + dy * half),
        )
    )
}

private val DEFAULT_GRADIENT_START = Color(0xFF3D82D6)
private val DEFAULT_GRADIENT_END = Color(0xFF7B54C4)
private const val DEFAULT_GRADIENT_ANGLE = 45
