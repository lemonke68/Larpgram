/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.musicplayer.api

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Полоса трека с кружком, общая для пузыря в чате и шторки плеера. Тап перематывает сразу,
 * протяжка — после отпускания. Жесты съедают касание, поэтому тап по полосе в пузыре не ставит
 * трек на паузу, как тап по самому пузырю.
 */
@Composable
fun MusicSeekBar(
    progress: Float,
    activeColor: Color,
    inactiveColor: Color,
    onSeekChange: (Float) -> Unit,
    onSeekFinish: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    thumbRadius: Dp = 5.dp,
) {
    // pointerInput(Unit) живёт дольше одной композиции: берём свежие значения, а не первые.
    val currentProgress by rememberUpdatedState(progress)
    val seekChange by rememberUpdatedState(onSeekChange)
    val seekFinish by rememberUpdatedState(onSeekFinish)
    val gestures = if (enabled) {
        Modifier
            .pointerInput(Unit) {
                detectTapGestures { offset -> seekFinish(fractionAt(offset.x, size.width, thumbRadius.toPx())) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset -> seekChange(fractionAt(offset.x, size.width, thumbRadius.toPx())) },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        seekChange(fractionAt(change.position.x, size.width, thumbRadius.toPx()))
                    },
                    onDragEnd = { seekFinish(currentProgress) },
                    onDragCancel = { seekFinish(currentProgress) },
                )
            }
    } else {
        Modifier
    }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            // Высота под палец, сама полоса тонкая.
            .height(20.dp)
            .then(gestures),
    ) {
        val barHeight = 3.dp.toPx()
        val radius = thumbRadius.toPx()
        // Кружок не вылезает за края: полоса начинается и кончается под его центром.
        val start = radius
        val width = size.width - radius * 2
        val top = (size.height - barHeight) / 2
        val corner = CornerRadius(barHeight / 2)
        val x = start + width * currentProgress
        drawRoundRect(inactiveColor, topLeft = Offset(start, top), size = Size(width, barHeight), cornerRadius = corner)
        drawRoundRect(activeColor, topLeft = Offset(start, top), size = Size(x - start, barHeight), cornerRadius = corner)
        if (enabled) drawCircle(activeColor, radius = radius, center = Offset(x, size.height / 2))
    }
}

private fun fractionAt(x: Float, width: Int, inset: Float): Float =
    ((x - inset) / (width - inset * 2).coerceAtLeast(1f)).coerceIn(0f, 1f)
