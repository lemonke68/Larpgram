/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.attachments.tgattach

import io.element.android.features.messages.impl.attachments.preview.error.sendAttachmentError
import io.element.android.libraries.channelcomments.ChannelPostMirror
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarDispatcher
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarMessage
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.mediaupload.api.MediaOptimizationConfig
import io.element.android.libraries.mediaupload.api.MediaSender
import io.element.android.libraries.mediaupload.api.MediaUploadInfo
import io.element.android.libraries.preferences.api.store.VideoCompressionPreset
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/** Альбом Telegram — не больше 10 фото/видео; больше выбранного уходит несколькими альбомами. */
internal const val TG_ALBUM_MAX_ITEMS = 10

/** Сколько можно выбрать за раз в меню вложений (как у системного пикера Element). */
internal const val TG_ATTACH_MAX_SELECTION = 60

/** Настройки сжатия для «Отправить без сжатия»: картинки как есть, видео в лучшем пресете. */
internal val UncompressedMediaConfig = MediaOptimizationConfig(
    compressImages = false,
    videoCompressionPreset = VideoCompressionPreset.HIGH,
)

/**
 * Правка форка: отправка из меню вложений Telegram сразу, без экрана предпросмотра Element.
 *
 * Та же последовательность, что у `AttachmentsPreviewPresenter`: предобработка (сжатие, EXIF),
 * затем одно медиа или альбом-галерея (MSC4274). Больше [TG_ALBUM_MAX_ITEMS] — несколько альбомов
 * подряд, подпись и ответ — у первого. Пост канала зеркалится в группу обсуждения, как с экрана
 * предпросмотра.
 */
internal suspend fun sendGalleryMediaNow(
    media: List<GalleryMedia>,
    caption: String?,
    inReplyToEventId: EventId?,
    mediaOptimizationConfig: MediaOptimizationConfig,
    mediaSender: MediaSender,
    room: JoinedRoom,
    matrixClient: MatrixClient,
    snackbarDispatcher: SnackbarDispatcher,
) {
    if (media.isEmpty()) return
    val isMediaOrGallery: (String?) -> Boolean = {
        ChannelPostMirror.isMirrorableMessage(it) || ChannelPostMirror.isGalleryEvent(it)
    }
    val preMediaIds = ChannelPostMirror.myPostIds(room, isMediaOrGallery)
    runCatchingExceptions {
        val infos = media.map { item ->
            mediaSender.preProcessMedia(
                uri = item.uri,
                mimeType = item.mimeType,
                mediaOptimizationConfig = mediaOptimizationConfig,
            ).getOrThrow()
        }
        infos.chunked(TG_ALBUM_MAX_ITEMS).forEachIndexed { index, chunk ->
            sendChunk(
                mediaSender = mediaSender,
                chunk = chunk,
                caption = caption.takeIf { index == 0 },
                inReplyToEventId = inReplyToEventId.takeIf { index == 0 },
            )
        }
    }.onFailure { cause ->
        Timber.e(cause, "Failed to send gallery media")
        if (cause is CancellationException) {
            mediaSender.cleanUp()
            throw cause
        }
        snackbarDispatcher.post(SnackbarMessage(sendAttachmentError(cause)))
    }.onSuccess {
        runCatchingExceptions { ChannelPostMirror.mirrorLastPost(room, matrixClient, preMediaIds, matches = isMediaOrGallery) }
            .onFailure { Timber.w(it, "Failed to mirror channel media post") }
    }
    mediaSender.cleanUp()
}

private suspend fun sendChunk(
    mediaSender: MediaSender,
    chunk: List<MediaUploadInfo>,
    caption: String?,
    inReplyToEventId: EventId?,
) {
    if (chunk.size == 1) {
        mediaSender.sendPreProcessedMedia(
            mediaUploadInfo = chunk.first(),
            caption = caption,
            formattedCaption = null,
            inReplyToEventId = inReplyToEventId,
        ).getOrThrow()
    } else {
        mediaSender.sendGallery(
            mediaUploadInfos = chunk,
            caption = caption,
            formattedCaption = null,
            inReplyToEventId = inReplyToEventId,
        ).getOrThrow()
    }
}
