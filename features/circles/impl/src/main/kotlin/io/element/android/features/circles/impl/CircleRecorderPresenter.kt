/*
 * Модуль форка: кружочки, круглые видеосообщения как в Telegram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.circles.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.di.RoomScope
import io.element.android.libraries.matrix.api.room.JoinedRoom
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.io.File

/**
 * Экран записи кружочка.
 *
 * Жесты телеграмные (сделаны 2026-08-14 на живом телефоне): держишь кнопку — пишется,
 * отпустил — отправилось, свайп вверх фиксирует запись, свайп влево отменяет. Сама
 * обработка жеста живёт в кнопке композера (`LarpgramCircleRecordButton`), сюда приходят
 * уже готовые события.
 */
@Inject
@ContributesBinding(RoomScope::class)
class CircleRecorderPresenter(
    private val room: JoinedRoom,
    private val recorder: CircleRecorder,
    private val sender: CircleSender,
) : Presenter<CircleRecorderState> {
    @Composable
    override fun present(): CircleRecorderState {
        val coroutineScope = rememberCoroutineScope()
        var mode by remember { mutableStateOf<CircleRecorderMode>(CircleRecorderMode.Hidden) }
        var elapsed by remember { mutableLongStateOf(0L) }
        var isFrontCamera by remember { mutableStateOf(true) }
        var needsPermission by remember { mutableStateOf(false) }
        // Отмена доходит до колбэка записи не мгновенно, а файл дописывается в любом
        // случае. Флаг говорит обработчику результата, что этот файл никому не нужен.
        var discardResult by remember { mutableStateOf(false) }
        // Зафиксирована ли запись свайпом вверх (см. CircleRecorderState.isLocked).
        var isLocked by remember { mutableStateOf(false) }

        // Счётчик времени и жёсткая остановка на максимуме.
        LaunchedEffect(mode) {
            if (mode != CircleRecorderMode.Recording) return@LaunchedEffect
            elapsed = 0L
            while (elapsed < CIRCLE_MAX_DURATION_MS) {
                delay(TICK_MS)
                elapsed += TICK_MS
            }
            // Дошли до предела: останавливаем и отправляем, как Telegram.
            recorder.stop()
            mode = CircleRecorderMode.Sending
        }

        // Камеру надо отпускать, иначе она останется занятой после ухода с экрана.
        DisposableEffect(Unit) {
            onDispose { recorder.release() }
        }

        fun handleResult(result: Result<File>) {
            if (discardResult) {
                discardResult = false
                result.getOrNull()?.delete()
                mode = CircleRecorderMode.Hidden
                return
            }
            val file = result.getOrElse { error ->
                Timber.e(error, "кружочек не записался")
                mode = CircleRecorderMode.Hidden
                return
            }
            coroutineScope.launch {
                sender.send(room, file)
                    .onFailure { Timber.e(it, "кружочек не отправился") }
                mode = CircleRecorderMode.Hidden
            }
        }

        fun handleEvent(event: CircleRecorderEvents) {
            when (event) {
                CircleRecorderEvents.Open -> {
                    elapsed = 0L
                    mode = CircleRecorderMode.Ready
                }
                CircleRecorderEvents.Close -> {
                    recorder.release()
                    mode = CircleRecorderMode.Hidden
                }
                CircleRecorderEvents.StartRecording -> {
                    discardResult = false
                    isLocked = false
                    // Камера привязывается не мгновенно, а запись по жесту начинается в
                    // момент касания кнопки, когда экран записи только открылся. Поэтому
                    // ждём готовности, а не отказываемся с первой попытки: иначе первое
                    // нажатие всегда уходило бы впустую.
                    coroutineScope.launch {
                        val started = withTimeoutOrNull(START_TIMEOUT_MS) {
                            while (!recorder.start(::handleResult)) {
                                delay(START_RETRY_MS)
                            }
                            true
                        }
                        if (started == true) {
                            // Палец могли отпустить, пока камера просыпалась: тогда запись
                            // уже не нужна, и её надо сразу свернуть.
                            if (mode == CircleRecorderMode.Hidden) {
                                discardResult = true
                                recorder.cancel()
                            } else {
                                mode = CircleRecorderMode.Recording
                            }
                        } else {
                            Timber.e("камера не готова, запись не началась")
                            mode = CircleRecorderMode.Hidden
                        }
                    }
                }
                CircleRecorderEvents.LockRecording -> isLocked = true
                CircleRecorderEvents.StopAndSend -> {
                    // Палец подняли раньше, чем камера успела начать писать: отправлять
                    // нечего, просто закрываем экран. Запуск, если он ещё в пути, увидит
                    // Hidden и свернётся сам.
                    if (mode != CircleRecorderMode.Recording) {
                        mode = CircleRecorderMode.Hidden
                        return
                    }
                    recorder.stop()
                    mode = CircleRecorderMode.Sending
                }
                CircleRecorderEvents.CancelRecording -> {
                    discardResult = true
                    recorder.cancel()
                    mode = CircleRecorderMode.Hidden
                }
                CircleRecorderEvents.FlipCamera -> isFrontCamera = !isFrontCamera
                is CircleRecorderEvents.PermissionResult -> {
                    needsPermission = !event.granted
                    if (!event.granted) mode = CircleRecorderMode.Hidden
                }
            }
        }

        return CircleRecorderState(
            mode = mode,
            elapsedMillis = elapsed,
            isFrontCamera = isFrontCamera,
            needsPermission = needsPermission,
            recorder = recorder,
            isLocked = isLocked,
            eventSink = ::handleEvent,
        )
    }

    private companion object {
        const val TICK_MS = 100L

        /** Сколько ждём готовности камеры после касания кнопки. */
        const val START_TIMEOUT_MS = 3000L
        const val START_RETRY_MS = 50L
    }
}
