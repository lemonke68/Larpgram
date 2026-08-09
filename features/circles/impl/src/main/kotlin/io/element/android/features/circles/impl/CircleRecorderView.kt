/*
 * Модуль форка: кружочки, круглые видеосообщения как в Telegram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.circles.impl

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.element.android.libraries.designsystem.theme.components.Text

/** Разрешения, без которых записывать нечего. */
private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

/**
 * Экран записи кружочка: круглое превью камеры, счётчик и кнопка.
 *
 * Пока тап начинает и тап заканчивает. Телеграмное удержание со свайпами добавится после
 * проверки на живом телефоне: на эмуляторе жесты мышью не отражают того, как это
 * ощущается пальцем, и настраивать их вслепую бессмысленно.
 */
@Composable
fun CircleRecorderView(
    state: CircleRecorderState,
    modifier: Modifier = Modifier,
) {
    if (!state.isVisible) return

    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        state.eventSink(CircleRecorderEvents.PermissionResult(result.values.all { it }))
    }

    LaunchedEffect(Unit) {
        val missing = REQUIRED_PERMISSIONS.any {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing) permissionLauncher.launch(REQUIRED_PERMISSIONS)
    }

    // Именно Dialog, а не Box поверх содержимого: внутри экрана сообщений разметка
    // зажата областью контента, и запись не перекрывала бы ни чат, ни композер.
    Dialog(
        onDismissRequest = { state.eventSink(CircleRecorderEvents.Close) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                // Затемняем чат: во время записи он не нужен, а контраст с круглым превью
                // получается телеграмный.
                .background(Color.Black.copy(alpha = 0.92f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.78f)
                        // Квадрат на экране, круг после обрезки: сама запись тоже квадратная.
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    val recorder = state.recorder
                    if (LocalInspectionMode.current || recorder == null) {
                        Text(text = "камера")
                    } else {
                        CameraPreview(
                            recorder = recorder,
                            isFrontCamera = state.isFrontCamera,
                        )
                    }
                }

                Text(
                    text = formatElapsed(state.elapsedMillis),
                    // Фон здесь всегда тёмный, тема приложения на него не влияет.
                    color = Color.White,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when (state.mode) {
                        CircleRecorderMode.Ready -> {
                            OverlayButton(
                                text = "Отмена",
                                onClick = { state.eventSink(CircleRecorderEvents.Close) },
                            )
                            OverlayButton(
                                text = "Записать",
                                onClick = { state.eventSink(CircleRecorderEvents.StartRecording) },
                            )
                            OverlayButton(
                                text = if (state.isFrontCamera) "Задняя" else "Фронтальная",
                                onClick = { state.eventSink(CircleRecorderEvents.FlipCamera) },
                            )
                        }
                        CircleRecorderMode.Recording -> {
                            OverlayButton(
                                text = "Отменить",
                                onClick = { state.eventSink(CircleRecorderEvents.CancelRecording) },
                            )
                            OverlayButton(
                                text = "Отправить",
                                onClick = { state.eventSink(CircleRecorderEvents.StopAndSend) },
                            )
                            // Камеру можно менять и на ходу: запись это переживает,
                            // потому что помечена как постоянная.
                            OverlayButton(
                                text = if (state.isFrontCamera) "Задняя" else "Фронтальная",
                                onClick = { state.eventSink(CircleRecorderEvents.FlipCamera) },
                            )
                        }
                        CircleRecorderMode.Sending -> CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White,
                        )
                        CircleRecorderMode.Hidden -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraPreview(
    recorder: CircleRecorder,
    isFrontCamera: Boolean,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    // Пересобираем привязку при смене камеры: CameraX не умеет переключать линзу
    // у уже привязанного use case.
    LaunchedEffect(previewView, isFrontCamera) {
        val view = previewView ?: return@LaunchedEffect
        recorder.bind(lifecycleOwner, isFrontCamera).surfaceProvider = view.surfaceProvider
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            PreviewView(context).also {
                it.scaleType = PreviewView.ScaleType.FILL_CENTER
                previewView = it
            }
        },
    )
}

/**
 * Кнопка поверх затемнения.
 *
 * Своя, а не designsystem-овская: у той нет параметра цвета, а она берёт его из темы
 * приложения. На всегда тёмном фоне записи светлая тема давала тёмный текст на тёмном.
 */
@Composable
private fun OverlayButton(
    text: String,
    onClick: () -> Unit,
) {
    androidx.compose.material3.TextButton(
        onClick = onClick,
        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
            contentColor = Color.White,
        ),
    ) {
        Text(text = text)
    }
}

/** мм:сс, как в Telegram. */
private fun formatElapsed(millis: Long): String {
    val totalSeconds = millis / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
