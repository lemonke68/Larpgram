/*
 * Правка форка: воспроизведение кружочков прямо в чате.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.messages.impl.timeline.components.event

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemVideoContent
import io.element.android.libraries.matrix.api.media.MatrixMediaLoader
import io.element.android.libraries.matrix.api.media.MediaFile
import io.element.android.libraries.matrix.api.media.toFile
import timber.log.Timber

/**
 * Загрузчик медиа для кружочков.
 *
 * Зачем CompositionLocal: кружочек рисуется глубоко внутри таймлайна, и протащить до него
 * загрузчик параметрами значило бы править цепочку апстримовских компонентов
 * (`TimelineView` → `TimelineItemEventRow` → `TimelineItemVideoView`) при каждом ребейзе.
 * Значение кладётся один раз в `MessagesView`, дальше его забирает только наш код.
 *
 * null означает, что кружочек рисуется вне экрана сообщений (например, в превью Paparazzi):
 * тогда воспроизведения просто нет, а обложка показывается как раньше.
 */
val LocalCircleMediaLoader = staticCompositionLocalOf<MatrixMediaLoader?> { null }

/** Тег для logcat: `adb logcat -s LarpgramCircle`. Пока идёт отладка воспроизведения. */
internal const val CIRCLE_LOG_TAG = "LarpgramCircle"

/**
 * Скачивает файл кружочка и отдаёт готовый ExoPlayer, пока он нужен.
 *
 * **Файл скачивается целиком, а не стримится.** У Matrix нет обычной ссылки на медиа:
 * в шифрованной комнате файл ещё и зашифрован, расшифровывает его SDK, поэтому единственный
 * путь — попросить `downloadMediaFile` и играть с диска. Для кружочков это нормально: они
 * короткие, до минуты, и почти всегда уже лежат в кэше SDK после загрузки обложки.
 */
@Composable
internal fun rememberCirclePlayer(
    content: TimelineItemVideoContent,
    isPlaying: Boolean,
    muted: Boolean,
    loop: Boolean,
    onPlaybackEnded: () -> Unit,
): ExoPlayer? {
    val context = LocalContext.current
    val mediaLoader = LocalCircleMediaLoader.current
    if (mediaLoader == null) return null

    var mediaFile by remember(content.mediaSource) { mutableStateOf<MediaFile?>(null) }

    // Скачиваем только когда человек нажал: тянуть все кружочки в ленте заранее — это трафик
    // и место на устройстве, а посмотрят обычно один.
    LaunchedEffect(content.mediaSource, isPlaying) {
        if (!isPlaying || mediaFile != null) return@LaunchedEffect
        mediaLoader.downloadMediaFile(
            source = content.mediaSource,
            mimeType = content.mimeType,
            filename = content.filename,
        )
            .onSuccess {
                Timber.tag(CIRCLE_LOG_TAG).i("кружочек скачан: %s", it.path())
                mediaFile = it
            }
            .onFailure { Timber.tag(CIRCLE_LOG_TAG).w(it, "не удалось скачать кружочек") }
    }

    val file = mediaFile ?: return null

    // Файл SDK держит за собой, пока его не закрыли, поэтому закрываем вместе с уходом
    // кружочка с экрана.
    //
    // **Закрываем именно `file`, а не `mediaFile`.** Лямбда очистки выполняется позже и
    // читает состояние на момент выполнения: если написать `mediaFile?.close()`, то при
    // появлении файла эффект перезапустится и закроет только что скачанное. `close()` у
    // MediaFile удаляет временный файл, поэтому плеер получал ENOENT и рисовал чёрный круг —
    // словили на живом телефоне 2026-08-14.
    DisposableEffect(file) {
        onDispose { file.close() }
    }

    val player = remember(file) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file.toFile())))
            repeatMode = Player.REPEAT_MODE_OFF
            prepare()
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                Timber.tag(CIRCLE_LOG_TAG).i("состояние плеера: %d", playbackState)
                if (playbackState == Player.STATE_ENDED) {
                    onPlaybackEnded()
                }
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                Timber.tag(CIRCLE_LOG_TAG).i("размер видео: %dx%d", videoSize.width, videoSize.height)
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Timber.tag(CIRCLE_LOG_TAG).e(error, "ошибка воспроизведения кружочка")
            }

            override fun onRenderedFirstFrame() {
                Timber.tag(CIRCLE_LOG_TAG).i("первый кадр отрисован")
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player, isPlaying) {
        if (isPlaying) player.play() else player.pause()
    }

    LaunchedEffect(player, muted) {
        // Как в Telegram: кружок сам проигрывается без звука, звук включается по тапу.
        player.volume = if (muted) 0f else 1f
    }

    LaunchedEffect(player, loop) {
        // Беззвучный кружок крутится по кругу, со звуком играет один раз.
        player.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    return player
}
