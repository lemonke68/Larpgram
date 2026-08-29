/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package io.element.android.features.preferences.impl.advanced

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.preferences.impl.R
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Surface
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.ui.strings.CommonStrings
import kotlin.math.roundToInt
import android.graphics.Color as AndroidColor

/**
 * Larpgram: пипетка обоев — HSV-пикер как в обычных приложениях: 2D-карта насыщенность/яркость
 * (тап или перетаскивание) + полоса оттенка снизу. Источник правды — hue/sat/value; RGB и hex
 * выводятся из них. Своё, без внешних зависимостей.
 */
@Composable
fun ChatWallpaperColorPickerDialog(
    initialColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialHsv = remember(initialColor) {
        FloatArray(3).also { AndroidColor.colorToHSV(initialColor.toArgb(), it) }
    }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }

    val currentColor = Color.hsv(hue, saturation, value)
    val hex = "#%02X%02X%02X".format(
        (currentColor.red * 255).roundToInt(),
        (currentColor.green * 255).roundToInt(),
        (currentColor.blue * 255).roundToInt(),
    )

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = ElementTheme.colors.bgCanvasDefault,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(R.string.screen_chat_appearance_custom_color_title),
                    style = ElementTheme.typography.fontHeadingSmMedium,
                    color = ElementTheme.colors.textPrimary,
                )

                SaturationValuePanel(
                    hue = hue,
                    saturation = saturation,
                    value = value,
                    currentColor = currentColor,
                    onChange = { s, v ->
                        saturation = s
                        value = v
                    },
                )

                HueBar(
                    hue = hue,
                    onChange = { hue = it },
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .height(36.dp)
                            .fillMaxWidth(0.28f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(currentColor),
                    ) {}
                    Text(
                        text = hex,
                        style = ElementTheme.typography.fontBodyLgMedium,
                        color = ElementTheme.colors.textPrimary,
                    )
                }

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
                        onClick = { onColorSelected(currentColor) },
                    )
                }
            }
        }
    }
}

/** 2D-карта: X = насыщенность (0..1), Y = яркость (1..0). Тап и перетаскивание. */
@Composable
private fun SaturationValuePanel(
    hue: Float,
    saturation: Float,
    value: Float,
    currentColor: Color,
    onChange: (saturation: Float, value: Float) -> Unit,
) {
    fun update(pos: Offset, size: IntSize) {
        val s = (pos.x / size.width).coerceIn(0f, 1f)
        val v = (1f - pos.y / size.height).coerceIn(0f, 1f)
        onChange(s, v)
    }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .padding(top = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                detectTapGestures { update(it, size) }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    update(change.position, size)
                }
            },
    ) {
        val hueColor = Color.hsv(hue, 1f, 1f)
        drawRect(Brush.horizontalGradient(listOf(Color.White, hueColor)))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        val cx = saturation * size.width
        val cy = (1f - value) * size.height
        drawCircle(color = Color.White, radius = 9.dp.toPx(), center = Offset(cx, cy), style = Stroke(width = 3.dp.toPx()))
        drawCircle(color = currentColor, radius = 7.dp.toPx(), center = Offset(cx, cy))
    }
}

/** Полоса оттенка 0..360. Тап и перетаскивание по горизонтали. */
@Composable
private fun HueBar(
    hue: Float,
    onChange: (Float) -> Unit,
) {
    fun update(pos: Offset, size: IntSize) {
        onChange((pos.x / size.width).coerceIn(0f, 1f) * 360f)
    }
    val hueColors = remember {
        (0..360 step 60).map { Color.hsv(it.toFloat().coerceAtMost(359.9f), 1f, 1f) }
    }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                detectTapGestures { update(it, size) }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    update(change.position, size)
                }
            },
    ) {
        drawRect(Brush.horizontalGradient(hueColors))
        val x = (hue / 360f) * size.width
        drawCircle(color = Color.White, radius = size.height / 2f, center = Offset(x, size.height / 2f), style = Stroke(width = 3.dp.toPx()))
    }
}

@PreviewsDayNight
@Composable
internal fun ChatWallpaperColorPickerDialogPreview() = ElementPreview {
    ChatWallpaperColorPickerDialog(
        initialColor = Color(0xFF16232F),
        onColorSelected = {},
        onDismiss = {},
    )
}
