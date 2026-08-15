/*
 * Правка форка: кнопка записи кружочка с телеграмными жестами.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.textcomposer

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAll
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.theme.components.Icon
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.positionChange

/**
 * Что делать по ходу жеста записи. Одним объектом, чтобы не разносить четыре колбэка по
 * сигнатурам композера.
 */
@Immutable
data class CircleRecordGestures(
    /** Палец опустился на кнопку: открываем камеру и начинаем запись. */
    val onStart: () -> Unit,
    /** Свайп вверх: фиксируем запись, дальше палец не нужен. */
    val onLock: () -> Unit,
    /** Свайп в сторону: выбрасываем запись. */
    val onCancel: () -> Unit,
    /** Палец отпущен без фиксации: останавливаем и отправляем. */
    val onFinish: () -> Unit,
)

/**
 * Кнопка кружочка: жесты как в Telegram.
 *
 * Держишь — пишется, отпустил — улетело, свайп вверх фиксирует запись, свайп в сторону
 * отменяет. Отдельный жест, а не `IconButton`, потому что штатному клику нужен полный цикл
 * «нажал-отпустил», а нам нужно начать запись **в момент касания** и следить за пальцем
 * дальше.
 *
 * Обрабатываем вручную, а не через `detectDragGestures`: тот начинает отдавать события
 * только после того, как палец сдвинулся за порог, а нам нужен сам момент касания.
 */
@Composable
internal fun LarpgramCircleRecordButton(
    gestures: CircleRecordGestures,
    modifier: Modifier = Modifier,
) {
    val currentGestures by rememberUpdatedState(gestures)
    val density = LocalDensity.current
    val lockThresholdPx = with(density) { LOCK_THRESHOLD.toPx() }
    val cancelThresholdPx = with(density) { CANCEL_THRESHOLD.toPx() }

    Icon(
        modifier = modifier
            .padding(bottom = 5.dp, top = 5.dp)
            .size(48.dp)
            .padding(12.dp)
            .pointerInput(lockThresholdPx, cancelThresholdPx) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    currentGestures.onStart()

                    var dragX = 0f
                    var dragY = 0f
                    var locked = false
                    var cancelled = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        // Все пальцы подняты — жест закончился.
                        if (event.changes.fastAll { !it.pressed }) break

                        dragX += change.positionChange().x
                        dragY += change.positionChange().y

                        // Отмена важнее фиксации: если человек увёл палец вбок, он передумал.
                        if (!cancelled && dragX <= -cancelThresholdPx) {
                            cancelled = true
                            currentGestures.onCancel()
                            break
                        }
                        if (!locked && dragY <= -lockThresholdPx) {
                            locked = true
                            currentGestures.onLock()
                        }
                    }

                    // Зафиксированную запись останавливает уже экран записи, а не палец.
                    if (!cancelled && !locked) {
                        currentGestures.onFinish()
                    }
                }
            },
        imageVector = CompoundIcons.VideoCall(),
        contentDescription = "Записать кружочек",
        tint = ElementTheme.colors.iconSecondary,
    )
}

/** Насколько увести палец вверх, чтобы запись зафиксировалась. */
private val LOCK_THRESHOLD = 56.dp

/** Насколько увести палец вбок, чтобы запись отменилась. */
private val CANCEL_THRESHOLD = 72.dp
