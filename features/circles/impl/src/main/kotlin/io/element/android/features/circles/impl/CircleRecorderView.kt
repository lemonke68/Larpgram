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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.utils.rememberBlurredBackdrop

/** Разрешения, без которых записывать нечего. */
private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

/**
 * Экран записи кружочка: круглое превью камеры, кольцо прогресса, время и кнопки.
 *
 * Свёрстан по макетам «10. Circle Recording» и «11. Circle Recording Free Hand» из
 * Telegram-кита (рендеры в `design/tg-ref/circle-recording/`). Запись ведётся жестами,
 * см. `LarpgramCircleRecordButton` в композере.
 */
@Composable
fun CircleRecorderView(
    state: CircleRecorderState,
    modifier: Modifier = Modifier,
) {
    if (!state.isVisible) return

    val context = LocalContext.current

    // Чат под записью размывается, как в Telegram. Штатного блюра тут нет: Modifier.blur и
    // BLUR_BEHIND появились в API 31, а телефон на Android 10. Поэтому снимок окна через
    // PixelCopy и свой box-фильтр, подробности в BackdropBlur.kt.
    val backdrop = rememberBlurredBackdrop(enabled = state.isVisible)

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
                .background(SCRIM_COLOR),
        ) {
            // Квадратное превью по центру. Круг из него делает маска ниже, а не обрезка:
            // PreviewView рисует в SurfaceView, который живёт в отдельном слое окна и не
            // обрезается ни маской Compose, ни даже границами родителя — картинка,
            // растянутая под квадрат, торчала полосами сверху и снизу (2026-08-15).
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(CIRCLE_WIDTH_FRACTION)
                    .aspectRatio(1f)
                    .padding(RING_GAP),
                contentAlignment = Alignment.Center,
            ) {
                val recorder = state.recorder
                if (LocalInspectionMode.current || recorder == null) {
                    Text(text = "камера", color = Color.White)
                } else {
                    CameraPreview(
                        recorder = recorder,
                        isFrontCamera = state.isFrontCamera,
                    )
                }
            }

            // Маска и кольцо на весь экран: маска закрывает всё, кроме круга, тем же
            // цветом, что и фон, поэтому любой вылет превью прячется под ней.
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = (size.width * CIRCLE_WIDTH_FRACTION) / 2 - RING_GAP.toPx()
                val middle = Offset(size.width / 2, size.height / 2)

                val mask = Path().apply {
                    addRect(Rect(Offset.Zero, size))
                    addOval(Rect(center = middle, radius = radius))
                    fillType = PathFillType.EvenOdd
                }
                // Всё, кроме круга, закрываем размытым чатом: превью на SurfaceView вылезает
                // за свои границы, и без этой маски его края торчали бы поверх.
                if (backdrop != null) {
                    clipPath(mask) {
                        drawImage(
                            image = backdrop,
                            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                            filterQuality = FilterQuality.Low,
                        )
                        // Притемнение поверх: на светлом чате белые подписи иначе не читаются,
                        // и в Telegram фон под записью тоже уходит в тень.
                        drawRect(SCRIM_COLOR.copy(alpha = 0.45f))
                    }
                } else {
                    // Снимок не получился (или API ниже 26) — остаётся обычная тёмная подложка.
                    drawPath(mask, SCRIM_COLOR)
                }

                // Кольцо прогресса по краю круга, растёт по часовой от верха.
                val stroke = RING_STROKE.toPx()
                val ringRadius = radius + RING_GAP.toPx() / 2
                drawArc(
                    color = Color.White.copy(alpha = 0.9f),
                    startAngle = -90f,
                    sweepAngle = 360f * state.progress,
                    useCenter = false,
                    topLeft = Offset(middle.x - ringRadius, middle.y - ringRadius),
                    size = Size(ringRadius * 2, ringRadius * 2),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }

            // Маленькая кнопка над кнопкой записи: замок, пока держишь палец, и стоп
            // после фиксации. Ровно как в макете «Circle Recording Free Hand».
            if (state.mode == CircleRecorderMode.Recording) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 20.dp, bottom = RECORD_BUTTON_SIZE + 32.dp)
                        .size(LOCK_BUTTON_SIZE)
                        .clip(CircleShape)
                        .background(Color.White)
                        .then(
                            if (state.isLocked) {
                                Modifier.clickable { state.eventSink(CircleRecorderEvents.StopAndSend) }
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.isLocked) {
                        // Синий квадрат «стоп» на белом круге, как в макете.
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(ElementTheme.colors.bgAccentRest)
                        )
                    } else {
                        Icon(
                            modifier = Modifier.size(20.dp),
                            imageVector = CompoundIcons.LockSolid(),
                            contentDescription = "Закрепить запись",
                            tint = ElementTheme.colors.iconAccentPrimary,
                        )
                    }
                }
            }

            // Нижний ряд: смена камеры, «таблетка» со временем и кнопка записи.
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconButton(onClick = { state.eventSink(CircleRecorderEvents.FlipCamera) }) {
                    Icon(
                        modifier = Modifier.size(24.dp),
                        imageVector = CompoundIcons.SwitchCameraSolid(),
                        contentDescription = "Сменить камеру",
                        tint = Color.White,
                    )
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(PILL_HEIGHT)
                        .clip(CircleShape)
                        .background(if (ElementTheme.isLightTheme) Color.White else Color(0xFF2C2C2E))
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Красная точка и время — левый край таблетки.
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE5484D))
                    )
                    Text(
                        modifier = Modifier.padding(start = 8.dp),
                        text = formatElapsed(state.elapsedMillis),
                        color = if (ElementTheme.isLightTheme) Color.Black else Color.White,
                    )
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (state.isLocked) {
                            Text(
                                modifier = Modifier.clickable {
                                    state.eventSink(CircleRecorderEvents.CancelRecording)
                                },
                                text = "ОТМЕНА",
                                color = ElementTheme.colors.textActionAccent,
                            )
                        } else {
                            Text(
                                text = "‹ Свайп для отмены",
                                color = if (ElementTheme.isLightTheme) {
                                    Color.Black.copy(alpha = 0.5f)
                                } else {
                                    Color.White.copy(alpha = 0.6f)
                                },
                            )
                        }
                    }
                }

                // Кнопка записи стоит там же, где кнопка кружочка в композере: палец во
                // время записи лежит именно здесь.
                Box(
                    modifier = Modifier
                        .size(RECORD_BUTTON_SIZE)
                        .clip(CircleShape)
                        .background(ElementTheme.colors.bgAccentRest),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.mode == CircleRecorderMode.Sending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White,
                        )
                    } else {
                        Icon(
                            modifier = Modifier.size(28.dp),
                            imageVector = CompoundIcons.VideoCallSolid(),
                            contentDescription = null,
                            tint = Color.White,
                        )
                    }
                }
            }
        }
    }
}

/** Запасной фон, если снять кадр чата не удалось. */
private val SCRIM_COLOR = Color(0xFF0E0E10)

/** Доля ширины экрана под круг: в макете круг занимает почти всю ширину. */
private const val CIRCLE_WIDTH_FRACTION = 0.78f
private val RING_STROKE = 3.dp
private val RING_GAP = 10.dp
private val PILL_HEIGHT = 48.dp
private val LOCK_BUTTON_SIZE = 44.dp
private val RECORD_BUTTON_SIZE = 68.dp

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
                // Оставляем режим по умолчанию (SurfaceView): TextureView здесь давал
                // долгий старт превью и заметные лаги при записи на слабом железе
                // (Kirin 710, проверено 2026-08-15). Круг делаем маской поверх, а не
                // обрезкой, потому что SurfaceView обрезку Compose игнорирует.
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
