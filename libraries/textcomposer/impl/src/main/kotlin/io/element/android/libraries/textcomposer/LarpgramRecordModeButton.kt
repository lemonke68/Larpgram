/*
 * Правка форка: одна кнопка на голосовое и кружочек, как в Telegram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.textcomposer

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAll
import kotlinx.coroutines.withTimeoutOrNull
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.theme.components.Icon

/** Что записывает кнопка справа от поля ввода. */
enum class RecordMode {
    Voice,
    Circle,
}

/**
 * Кнопка записи с переключением режима, как в Telegram.
 *
 * Держишь — пишется голосовое или кружочек, смотря какой режим; отпустил — отправилось;
 * свайп вверх фиксирует запись, свайп влево отменяет. **Короткий тап меняет режим**, а не
 * начинает запись: именно так это работает в Telegram, и именно поэтому голосовое здесь
 * не «тап-старт, тап-стоп», как было у апстрима.
 *
 * По умолчанию голосовое: кружочки шлют реже, а привычка у людей телеграмная.
 */
@Composable
internal fun LarpgramRecordModeButton(
    circleGestures: CircleRecordGestures,
    onVoiceStart: () -> Unit,
    onVoiceStop: () -> Unit,
    onVoiceCancel: () -> Unit,
    onVoiceLock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var mode by rememberSaveable { mutableStateOf(RecordMode.Voice) }
    val currentMode by rememberUpdatedState(mode)
    val currentCircleGestures by rememberUpdatedState(circleGestures)
    val currentVoiceStart by rememberUpdatedState(onVoiceStart)
    val currentVoiceStop by rememberUpdatedState(onVoiceStop)
    val currentVoiceCancel by rememberUpdatedState(onVoiceCancel)
    val currentVoiceLock by rememberUpdatedState(onVoiceLock)

    val density = LocalDensity.current
    val lockThresholdPx = with(density) { LOCK_THRESHOLD.toPx() }
    val cancelThresholdPx = with(density) { CANCEL_THRESHOLD.toPx() }

    Icon(
        modifier = modifier
            .padding(bottom = 5.dp, top = 5.dp, end = 6.dp, start = 6.dp)
            .size(48.dp)
            .padding(12.dp)
            .pointerInput(lockThresholdPx, cancelThresholdPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startMode = currentMode

                    // Ждём порог удержания, НЕ начиная запись. Так короткий тап вообще не
                    // трогает камеру и микрофон: раньше запись стартовала сразу и тап её
                    // отменял, из-за чего она успевала мигнуть, а голосовое улетало в
                    // предпросмотр, не записавшись (замечено на телефоне 2026-08-15).
                    val releasedEarly = withTimeoutOrNull(HOLD_THRESHOLD_MS) {
                        waitForUpOrCancellation()
                    }
                    if (releasedEarly != null) {
                        mode = if (startMode == RecordMode.Voice) RecordMode.Circle else RecordMode.Voice
                        return@awaitEachGesture
                    }

                    if (startMode == RecordMode.Circle) {
                        currentCircleGestures.onStart()
                    } else {
                        currentVoiceStart()
                    }

                    var dragX = 0f
                    var dragY = 0f
                    var locked = false
                    var cancelled = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (event.changes.fastAll { !it.pressed }) break

                        dragX += change.positionChange().x
                        dragY += change.positionChange().y

                        // Отмена важнее фиксации: увёл палец вбок — значит передумал.
                        if (!cancelled && dragX <= -cancelThresholdPx) {
                            cancelled = true
                            if (startMode == RecordMode.Circle) {
                                currentCircleGestures.onCancel()
                            } else {
                                currentVoiceCancel()
                            }
                            break
                        }
                        if (!locked && dragY <= -lockThresholdPx) {
                            locked = true
                            if (startMode == RecordMode.Circle) {
                                currentCircleGestures.onLock()
                            } else {
                                // Голосовое фиксируется, как кружок: после отпускания запись
                                // продолжается «без рук», а композер показывает кнопку отправки.
                                currentVoiceLock()
                            }
                        }
                    }

                    if (cancelled || locked) return@awaitEachGesture

                    if (startMode == RecordMode.Circle) {
                        currentCircleGestures.onFinish()
                    } else {
                        currentVoiceStop()
                    }
                }
            },
        imageVector = when (mode) {
            RecordMode.Voice -> CompoundIcons.MicOnSolid()
            RecordMode.Circle -> CompoundIcons.VideoCall()
        },
        contentDescription = when (mode) {
            RecordMode.Voice -> "Записать голосовое, тап — переключить на кружочек"
            RecordMode.Circle -> "Записать кружочек, тап — переключить на голосовое"
        },
        tint = ElementTheme.colors.iconSecondary,
    )
}

/**
 * Сколько надо продержать палец, чтобы это считалось записью, а не переключением режима.
 *
 * 250 мс было мало: тап иногда успевал зацепить запись. 400 мс на ощупь разделяет их
 * уверенно и при этом не воспринимается как задержка.
 */
private const val HOLD_THRESHOLD_MS = 400L

/** Насколько увести палец вверх, чтобы запись зафиксировалась. */
private val LOCK_THRESHOLD = 56.dp

/** Насколько увести палец вбок, чтобы запись отменилась. */
private val CANCEL_THRESHOLD = 72.dp
