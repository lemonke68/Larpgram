/*
 * Модуль форка: кружочки, круглые видеосообщения как в Telegram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.circles.impl

/**
 * @param mode что сейчас происходит с записью.
 * @param elapsedMillis сколько записано, для полоски и счётчика.
 * @param isFrontCamera с какой камеры снимаем.
 * @param needsPermission true если камеру или микрофон не разрешили.
 * @param recorder нужен вью, чтобы привязать превью камеры к экрану. Обычно объектам не
 *   место в состоянии, но здесь он один на комнату и не меняется, так что сравнение по
 *   ссылке работает как надо, а альтернатива это тащить DI в composable. null в превью
 *   и тестах: камера им не нужна.
 */
data class CircleRecorderState(
    val mode: CircleRecorderMode,
    val elapsedMillis: Long,
    val isFrontCamera: Boolean,
    val needsPermission: Boolean,
    val recorder: CircleRecorder?,
    val eventSink: (CircleRecorderEvents) -> Unit,
) {
    val isVisible: Boolean get() = mode != CircleRecorderMode.Hidden

    /** Доля от максимальной длительности, 0..1. Для кольца вокруг кнопки. */
    val progress: Float
        get() = (elapsedMillis.toFloat() / CIRCLE_MAX_DURATION_MS).coerceIn(0f, 1f)
}

sealed interface CircleRecorderMode {
    /** Экран записи закрыт. */
    data object Hidden : CircleRecorderMode

    /** Камера показывает картинку, запись ещё не идёт. */
    data object Ready : CircleRecorderMode

    /** Идёт запись. */
    data object Recording : CircleRecorderMode

    /** Запись остановлена, файл заливается. */
    data object Sending : CircleRecorderMode
}

sealed interface CircleRecorderEvents {
    /** Открыть экран записи. */
    data object Open : CircleRecorderEvents

    /** Закрыть, ничего не отправив. */
    data object Close : CircleRecorderEvents

    /** Начать запись. */
    data object StartRecording : CircleRecorderEvents

    /** Остановить и отправить. */
    data object StopAndSend : CircleRecorderEvents

    /** Остановить и выбросить. */
    data object CancelRecording : CircleRecorderEvents

    /** Переключить фронтальную и основную камеру. */
    data object FlipCamera : CircleRecorderEvents

    /** Разрешения выданы или в них отказано. */
    data class PermissionResult(val granted: Boolean) : CircleRecorderEvents
}
