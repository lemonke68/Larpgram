/*
 * Copyright (c) 2026 Larpgram.
 * Правка форка: кружочки, круглые видеосообщения как в Telegram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import android.view.TextureView
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemVideoContent
import io.element.android.features.messages.impl.timeline.protection.ProtectedView
import io.element.android.libraries.core.media.AudiblePlaybackController
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.ui.media.MediaRequestData
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Кружочек в таймлайне: квадратное видео, обрезанное в круг.
 *
 * Как отличаем кружочек от обычного видео: по имени файла. Штатная отправка
 * (`Timeline.sendVideo`) не даёт добавить своё поле в content, а отказаться от неё нельзя,
 * иначе в шифрованных комнатах медиа поедет незашифрованным. Имя файла до клиента
 * доезжает, поэтому маркер живёт там.
 */
@Composable
fun LarpgramCircleVideoView(
    content: TimelineItemVideoContent,
    hideMediaContent: Boolean,
    onContentClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
    onShowContentClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = content.caption ?: stringResource(CommonStrings.common_video)

    // Правка форка: кружок ведёт себя как в Telegram.
    //
    // Появился на экране — крутится сам и молча. Тапнул — включается звук и запускается с
    // начала. Тапнул ещё раз — пауза. Полноэкранный просмотрщик для кружочков больше не
    // открывается; у обычных видео он остался, а скачать или переслать кружок можно из меню
    // по долгому нажатию.
    var isVisible by remember(content.mediaSource) { mutableStateOf(false) }
    var withSound by remember(content.mediaSource) { mutableStateOf(false) }
    var isPaused by remember(content.mediaSource) { mutableStateOf(false) }

    val isPlaying = isVisible && !isPaused
    val player = rememberCirclePlayer(
        content = content,
        isPlaying = isPlaying,
        muted = !withSound,
        loop = !withSound,
        onPlaybackEnded = {
            // Со звуком кружок играет один раз, как в Telegram: доиграл — вернулся к
            // беззвучному кругу, а не зациклился с громким звуком в ленте.
            withSound = false
        },
    )

    // Larpgram: моно-звук. Пока этот кружок звучит — держим аудио-фокус; кто-то другой (кружок
    // или голосовое) забрал фокус — возвращаемся к беззвучному кругу. Токен = mediaSource кружочка.
    val audioToken = content.mediaSource
    val currentAudible by AudiblePlaybackController.current.collectAsState()
    val isAudible = withSound && !isPaused && isVisible
    LaunchedEffect(isAudible) {
        if (isAudible) AudiblePlaybackController.requestFocus(audioToken) else AudiblePlaybackController.release(audioToken)
    }
    LaunchedEffect(currentAudible) {
        if (withSound && currentAudible != audioToken) {
            withSound = false
            isPaused = false
        }
    }
    DisposableEffect(audioToken) {
        onDispose { AudiblePlaybackController.release(audioToken) }
    }

    fun onCircleClick() {
        when {
            // Играет со звуком — ставим на паузу.
            withSound && !isPaused -> isPaused = true
            // Всё остальное (беззвучный круг, пауза) — запускаем со звуком с начала.
            else -> {
                isPaused = false
                withSound = true
                player?.seekTo(0)
            }
        }
    }
    // Правка форка: длительность и кольцо прогресса, как у кружочка в Telegram (аудит A-021).
    // Пока играет со звуком — остаток времени и кольцо по краю; иначе — полная длительность.
    val totalMs = content.duration.inWholeMilliseconds
    var progress by remember(content.mediaSource) { mutableFloatStateOf(0f) }
    var remainingMs by remember(content.mediaSource) { mutableLongStateOf(totalMs) }
    LaunchedEffect(player, withSound, isPaused) {
        if (player == null || !withSound || isPaused) {
            if (!withSound) {
                progress = 0f
                remainingMs = totalMs
            }
            return@LaunchedEffect
        }
        while (isActive) {
            val duration = player.duration.takeIf { it > 0 } ?: totalMs
            if (duration > 0) {
                progress = (player.currentPosition.toFloat() / duration).coerceIn(0f, 1f)
                remainingMs = (duration - player.currentPosition).coerceAtLeast(0)
            }
            delay(100)
        }
    }

    val durationLabel = LocalCircleDurationLabel.current
    if (durationLabel != null) {
        val text = if (totalMs > 0) formatCircleDuration(remainingMs) else null
        SideEffect { durationLabel.text = text }
    }

    // Пока файл не скачался, показываем обложку: чёрный круг вместо кружочка выглядит
    // как поломка (на этом уже обжигались, когда не было thumbnail).
    val showVideo = isPlaying && player != null

    // На время просмотра со звуком кружок подрастает, как в Telegram: смотреть удобнее, и
    // сразу видно, какой из кружочков в ленте сейчас играет. Беззвучный фон не трогаем.
    val circleSize by animateDpAsState(
        targetValue = if (withSound && !isPaused) CIRCLE_SIZE_EXPANDED else CIRCLE_SIZE,
        label = "circleSize",
    )

    Box(
        modifier = modifier
            .size(circleSize)
            .clip(CircleShape)
            .background(Color.Black)
            // Автовоспроизведение: кружок крутится, только пока он на экране. Иначе
            // десяток кружочков в ленте декодировались бы разом, съедая батарею и память.
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInWindow()
                // Половины круга достаточно: у Telegram кружок оживает ещё на подходе,
                // а не когда встал ровно по центру.
                isVisible = bounds.height >= coordinates.size.height / 2f
            },
        contentAlignment = Alignment.Center,
    ) {
        ProtectedView(
            hideContent = hideMediaContent,
            onShowClick = onShowContentClick,
        ) {
            if (showVideo) {
                AndroidView(
                    modifier = Modifier
                        .size(circleSize)
                        .clip(CircleShape)
                        .combinedClickable(
                            onClick = ::onCircleClick,
                            onLongClick = onLongClick,
                            onLongClickLabel = stringResource(CommonStrings.action_open_context_menu),
                        ),
                    // TextureView, а НЕ PlayerView с его SurfaceView: поверхность живёт в
                    // отдельном слое окна и обрезку Compose игнорирует, поэтому круглая маска
                    // на неё не действует — вместо картинки видна прозрачная дыра со звуком.
                    // Проверено на телефоне 2026-08-14.
                    //
                    // Растяжение по площади здесь безопасно: кружочки квадратные и по записи,
                    // и по отрисовке.
                    factory = { context ->
                        TextureView(context).also { view ->
                            player.setVideoTextureView(view)
                            view.tag = player
                        }
                    },
                    // Привязываем поверхность ровно один раз на плеер. `update` вызывается на
                    // каждой перерисовке, а повторный setVideoTextureView сбрасывает поверхность,
                    // и картинка не успевает появиться — остаётся чёрный круг со звуком.
                    update = { view ->
                        if (view.tag !== player) {
                            player.setVideoTextureView(view)
                            view.tag = player
                        }
                    },
                    onRelease = { view ->
                        player.clearVideoTextureView(view)
                        view.tag = null
                    },
                )
            }
            AsyncImage(
                modifier = Modifier
                    .size(circleSize)
                    .clip(CircleShape)
                    .alpha(if (showVideo) 0f else 1f)
                    .then(
                        if (onContentClick != null) {
                            Modifier.combinedClickable(
                                onClick = ::onCircleClick,
                                onLongClick = onLongClick,
                                onLongClickLabel = stringResource(CommonStrings.action_open_context_menu),
                            )
                        } else {
                            Modifier
                        }
                    ),
                model = MediaRequestData(
                    source = content.thumbnailSource ?: content.mediaSource,
                    kind = MediaRequestData.Kind.Thumbnail(
                        width = CIRCLE_THUMBNAIL_PX,
                        height = CIRCLE_THUMBNAIL_PX,
                    ),
                ),
                // Кадр квадратный, но обрезаем на всякий случай: чужой клиент мог прислать не квадрат.
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
                contentDescription = description,
            )
            // Кнопка играет роль подсказки «это видео», поэтому во время проигрывания её нет.
            if (!showVideo) {
                Icon(
                    modifier = Modifier.size(40.dp),
                    imageVector = CompoundIcons.PlaySolid(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.surface,
                )
            }
        }
        if (withSound && progress > 0f) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val stroke = 3.dp.toPx()
                drawArc(
                    color = Color.White,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        // Длительность — отдельная плашка слева снизу, вне круга, в пару к плашке времени: её рисует
        // строка сообщения (см. LocalCircleDurationLabel). Внутри круга — только там, где такой строки нет.
        if (totalMs > 0 && durationLabel == null) {
            Text(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 28.dp, bottom = 14.dp)
                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                text = formatCircleDuration(remainingMs),
                style = ElementTheme.typography.fontBodyXsMedium,
                color = Color.White,
            )
        }
    }
}

/**
 * Куда кружок отдаёт подпись длительности, если её рисует строка сообщения: плашка стоит вне
 * круга, в углу напротив времени отправки, как в Telegram (`ChatMessageCell`, `durationLayout`).
 */
@Stable
class CircleDurationLabel {
    var text by mutableStateOf<String?>(null)
}

val LocalCircleDurationLabel = staticCompositionLocalOf<CircleDurationLabel?> { null }

private fun formatCircleDuration(ms: Long): String {
    val totalSeconds = (ms + 999) / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

/** Как в Telegram: кружок занимает заметную, но не всю ширину. */
private val CIRCLE_SIZE = 200.dp

/**
 * Размер на время просмотра со звуком.
 *
 * 280 dp, а не «на весь экран»: у самого узкого разумного телефона (320 dp) кружок с
 * отступами таймлайна должен помещаться, не упираясь в края.
 */
private val CIRCLE_SIZE_EXPANDED = 280.dp
private const val CIRCLE_THUMBNAIL_PX = 400L
