/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.utils.snackbar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.theme.components.Text

/**
 * Плашка с обратным отсчётом и «Отменить», как `UndoView` Telegram (удаление чата, очистка
 * истории). Отсчёт идёт до [endsAtMillis] (`System.currentTimeMillis()`), тем же временем живёт
 * отложенное действие, так что цифра на кружке и настоящий таймер не расходятся.
 */
class TgUndoSnackbarVisuals(
    override val message: String,
    override val actionLabel: String,
    val endsAtMillis: Long,
    val totalMillis: Long,
) : SnackbarVisuals {
    override val withDismissAction: Boolean = false

    // Закрываем сами по окончании отсчёта: у Material3 нет длительности «ровно 5 секунд».
    override val duration: SnackbarDuration = SnackbarDuration.Indefinite
}

/** Цвета `undo_background` / `undo_cancelColor` / `undo_infoColor` Telegram, одни на обе темы. */
private val UndoBackground = Color(0xEA272F38)
private val UndoCancel = Color(0xFF85CAFF)
private val UndoInfo = Color.White

@Composable
internal fun TgUndoSnackbar(
    data: SnackbarData,
    visuals: TgUndoSnackbarVisuals,
    modifier: Modifier = Modifier,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(visuals) {
        while (now < visuals.endsAtMillis) {
            withFrameMillis { now = System.currentTimeMillis() }
        }
        data.dismiss()
    }
    TgUndoSnackbarContent(
        message = visuals.message,
        actionLabel = visuals.actionLabel,
        leftMillis = (visuals.endsAtMillis - now).coerceIn(0, visuals.totalMillis),
        totalMillis = visuals.totalMillis,
        onUndo = data::performAction,
        modifier = modifier,
    )
}

@Composable
private fun TgUndoSnackbarContent(
    message: String,
    actionLabel: String,
    leftMillis: Long,
    totalMillis: Long,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            // Отступы 8dp по бокам и снизу, радиус 10dp — как UndoView в списке чатов.
            .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(UndoBackground),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .padding(start = 15.dp, end = 12.dp)
                .size(18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(18.dp)) {
                val stroke = 2.dp.toPx()
                drawArc(
                    color = UndoInfo,
                    startAngle = -90f,
                    sweepAngle = -360f * leftMillis / totalMillis,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
            val seconds = ((leftMillis + 999) / 1000).coerceAtLeast(1)
            // Старая цифра уезжает вниз на 10dp и гаснет, новая приходит сверху, 150 мс.
            AnimatedContent(
                targetState = seconds,
                transitionSpec = {
                    (slideInVertically(tween(150)) { -it / 2 } + fadeIn(tween(150)))
                        .togetherWith(slideOutVertically(tween(150)) { it / 2 } + fadeOut(tween(150)))
                },
                label = "undo-seconds",
            ) { value ->
                Text(
                    text = value.toString(),
                    color = UndoInfo,
                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                )
            }
        }
        Text(
            text = message,
            color = UndoInfo,
            style = TextStyle(fontSize = 15.sp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 13.dp),
        )
        Text(
            text = actionLabel.uppercase(),
            color = UndoCancel,
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold),
            modifier = Modifier
                .padding(start = 8.dp, end = 11.dp)
                .clip(RoundedCornerShape(2.dp))
                .clickable(onClick = onUndo)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Preview
@Composable
internal fun TgUndoSnackbarPreview() = ElementPreview {
    TgUndoSnackbarContent(
        message = "Чат удалится у обоих",
        actionLabel = "Отменить",
        leftMillis = 3_400,
        totalMillis = 5_000,
        onUndo = {},
    )
}
