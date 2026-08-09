/*
 * Модуль форка: кружочки, круглые видеосообщения как в Telegram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.circles.impl

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.matrix.api.media.ThumbnailInfo
import io.element.android.libraries.matrix.api.media.VideoInfo
import io.element.android.libraries.matrix.api.room.JoinedRoom
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Отправляет записанный кружочек.
 *
 * Идёт штатным `Timeline.sendVideo`, а не сырым событием: в шифрованной комнате SDK сам
 * шифрует медиа, а при ручной заливке через `uploadMedia` в зашифрованном чате лежал бы
 * незашифрованный файл. Своё поле в content поэтому добавить нечем, и кружочек помечается
 * именем файла (см. `LARPGRAM_CIRCLE_FILENAME_PREFIX`).
 */
class CircleSender(
    private val coroutineDispatchers: CoroutineDispatchers,
) {
    suspend fun send(room: JoinedRoom, file: File): Result<Unit> = withContext(coroutineDispatchers.io) {
        runCatching {
            val probed = probe(file)
            // Полезно в логе на живом телефоне: соблюдение квадрата зависит от того,
            // применил ли производитель ViewPort к записи.
            Timber.d("кружочек записан: %dx%d, %s", probed.width, probed.height, probed.duration)
            val thumbnail = extractThumbnail(file)
            try {
                room.liveTimeline.sendVideo(
                    file = file,
                    thumbnailFile = thumbnail,
                    videoInfo = VideoInfo(
                        duration = probed.duration,
                        height = probed.height,
                        width = probed.width,
                        mimetype = MIME_TYPE_MP4,
                        size = file.length(),
                        thumbnailInfo = thumbnail?.let {
                            ThumbnailInfo(
                                height = probed.height,
                                width = probed.width,
                                mimetype = MIME_TYPE_JPEG,
                                size = it.length(),
                            )
                        },
                        thumbnailSource = null,
                        blurhash = null,
                    ),
                    caption = null,
                    formattedCaption = null,
                    inReplyToEventId = null,
                )
                    .getOrThrow()
                    // Без await загрузка не доводится до конца: sendVideo лишь ставит её в
                    // очередь. Удалять файл до этого тоже нельзя, SDK читает его именно
                    // во время загрузки.
                    .await()
                    .getOrThrow()
                Unit
            } finally {
                file.delete()
                thumbnail?.delete()
            }
        }
    }

    /**
     * Первый кадр как обложка. Без неё кружочек до нажатия остаётся чёрным пятном, что
     * выглядит поломкой; в Telegram там всегда видно первый кадр.
     */
    private fun extractThumbnail(file: File): File? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val frame = retriever.getFrameAtTime(0) ?: return null
            val target = File(file.parentFile, "${file.nameWithoutExtension}-thumb.jpg")
            target.outputStream().use { out ->
                frame.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_QUALITY, out)
            }
            frame.recycle()
            target
        } catch (error: Exception) {
            // Без обложки сообщение всё равно уйдёт, просто кружок будет тёмным.
            Timber.w(error, "не удалось сделать обложку кружочка")
            null
        } finally {
            retriever.release()
        }
    }

    /**
     * Размеры и длительность берём из самого файла, а не из того, что мы просили у камеры.
     * Устройство вправе записать не то, что заказано, и тогда в чате поедет геометрия.
     */
    private fun probe(file: File): Probed {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            fun meta(key: Int) = retriever.extractMetadata(key)?.toLongOrNull()
            // После поворота ширина и высота меняются местами, у квадрата это неважно,
            // но кружочек не обязан оказаться идеальным квадратом на любом устройстве.
            val rotation = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION) ?: 0L
            val rawWidth = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val rawHeight = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val swapped = rotation == 90L || rotation == 270L
            Probed(
                width = if (swapped) rawHeight else rawWidth,
                height = if (swapped) rawWidth else rawHeight,
                duration = meta(MediaMetadataRetriever.METADATA_KEY_DURATION)?.milliseconds,
            )
        } catch (error: Exception) {
            // Без метаданных сообщение всё равно уйдёт, просто без размеров.
            Timber.w(error, "не удалось прочитать метаданные кружочка")
            Probed(null, null, null)
        } finally {
            retriever.release()
        }
    }

    private data class Probed(
        val width: Long?,
        val height: Long?,
        val duration: Duration?,
    )

    private companion object {
        const val MIME_TYPE_MP4 = "video/mp4"
        const val MIME_TYPE_JPEG = "image/jpeg"
        const val THUMBNAIL_QUALITY = 85
    }
}
