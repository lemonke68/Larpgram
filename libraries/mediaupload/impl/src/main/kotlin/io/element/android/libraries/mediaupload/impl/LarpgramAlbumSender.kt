/*
 * Copyright (c) 2026 Larpgram.
 * Правка форка: альбом отдельными медиа-сообщениями вместо галереи MSC4274. Вынесено из
 * `DefaultMediaSender` (аудит C-009).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.mediaupload.impl

import android.system.ErrnoException
import android.system.Os
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.media.MediaUploadHandler
import io.element.android.libraries.matrix.api.timeline.Timeline
import io.element.android.libraries.matrix.api.timeline.item.event.LarpgramAlbum
import io.element.android.libraries.mediaupload.api.MediaUploadInfo
import timber.log.Timber
import java.io.File

/**
 * Альбом уходит не галереей MSC4274, а отдельными обычными медиа-сообщениями с общей меткой в имени
 * файла ([LarpgramAlbum]). Галерею понимает только Element X с флагом, остальные клиенты показывали
 * «неподдерживаемое событие». Подпись и ответ — у первой части, как у альбома в Telegram. Все части
 * сначала ставятся в очередь отправки (порядок сохраняется, в ленте сразу появляется весь альбом),
 * потом ждём загрузку каждой. [onUploadStarted] получает последнюю загрузку, чтобы её можно было отменить.
 */
internal suspend fun Timeline.sendLarpgramAlbum(
    mediaUploadInfos: List<MediaUploadInfo>,
    caption: String?,
    formattedCaption: String?,
    inReplyToEventId: EventId?,
    onUploadStarted: (MediaUploadHandler) -> Unit,
): Result<Unit> {
    val albumId = LarpgramAlbum.newAlbumId()
    val parts = mediaUploadInfos.mapIndexed { index, info ->
        val name = LarpgramAlbum.filename(albumId, index, mediaUploadInfos.size, info.file.extension)
        info.withFile(linkOrCopy(info.file, name))
    }
    return runCatchingExceptions {
        val handlers = parts.mapIndexed { index, part ->
            enqueueMedia(
                uploadInfo = part,
                caption = caption.takeIf { index == 0 },
                formattedCaption = formattedCaption.takeIf { index == 0 },
                inReplyToEventId = inReplyToEventId.takeIf { index == 0 },
            ).getOrThrow()
        }
        handlers.lastOrNull()?.let(onUploadStarted)
        handlers.forEach { it.await().getOrThrow() }
    }.also {
        parts.zip(mediaUploadInfos)
            .filter { (part, original) -> part.file != original.file }
            .forEach { (part, _) -> part.file.delete() }
    }
}

private suspend fun Timeline.enqueueMedia(
    uploadInfo: MediaUploadInfo,
    caption: String?,
    formattedCaption: String?,
    inReplyToEventId: EventId?,
): Result<MediaUploadHandler> = when (uploadInfo) {
    is MediaUploadInfo.Image -> sendImage(
        file = uploadInfo.file,
        thumbnailFile = uploadInfo.thumbnailFile,
        imageInfo = uploadInfo.imageInfo,
        caption = caption,
        formattedCaption = formattedCaption,
        inReplyToEventId = inReplyToEventId,
    )
    is MediaUploadInfo.Video -> sendVideo(
        file = uploadInfo.file,
        thumbnailFile = uploadInfo.thumbnailFile,
        videoInfo = uploadInfo.videoInfo,
        caption = caption,
        formattedCaption = formattedCaption,
        inReplyToEventId = inReplyToEventId,
    )
    is MediaUploadInfo.Audio -> sendAudio(
        file = uploadInfo.file,
        audioInfo = uploadInfo.audioInfo,
        caption = caption,
        formattedCaption = formattedCaption,
        inReplyToEventId = inReplyToEventId,
    )
    is MediaUploadInfo.VoiceMessage -> sendVoiceMessage(
        file = uploadInfo.file,
        audioInfo = uploadInfo.audioInfo,
        waveform = uploadInfo.waveform,
        inReplyToEventId = inReplyToEventId,
    )
    is MediaUploadInfo.AnyFile -> sendFile(
        file = uploadInfo.file,
        fileInfo = uploadInfo.fileInfo,
        caption = caption,
        formattedCaption = formattedCaption,
        inReplyToEventId = inReplyToEventId,
    )
}

/**
 * Файл части альбома под именем-меткой. Жёсткая ссылка вместо переименования: исходный файл должен
 * остаться на месте, экран предпросмотра переиспользует его при повторной отправке.
 */
private fun linkOrCopy(source: File, name: String): File {
    val target = File(source.parentFile, name)
    target.delete()
    try {
        Os.link(source.path, target.path)
    } catch (e: ErrnoException) {
        Timber.w(e, "Hard link failed, copying album part")
    }
    if (target.exists()) return target
    // Не вышло и скопировать — фото всё равно уйдут, только без мозаики у получателя.
    return runCatchingExceptions { source.copyTo(target, overwrite = true) }
        .onFailure { Timber.w(it, "Could not name album part, sending it as is") }
        .getOrDefault(source)
}

private fun MediaUploadInfo.withFile(file: File): MediaUploadInfo = when (this) {
    is MediaUploadInfo.Image -> copy(file = file)
    is MediaUploadInfo.Video -> copy(file = file)
    is MediaUploadInfo.Audio -> copy(file = file)
    is MediaUploadInfo.VoiceMessage -> copy(file = file)
    is MediaUploadInfo.AnyFile -> copy(file = file)
}
