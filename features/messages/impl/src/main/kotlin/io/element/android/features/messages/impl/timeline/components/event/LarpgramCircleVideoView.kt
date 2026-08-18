/*
 * Правка форка: кружочки, круглые видеосообщения как в Telegram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.messages.impl.timeline.components.event

import android.view.TextureView
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemVideoContent
import io.element.android.features.messages.impl.timeline.protection.ProtectedView
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.matrix.ui.media.MediaRequestData
import io.element.android.libraries.ui.strings.CommonStrings

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
    }
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
