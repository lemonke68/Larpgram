/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.mediaupload.impl

import android.net.Uri
import android.system.ErrnoException
import android.system.Os
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.androidutils.hash.hash
import io.element.android.libraries.core.extensions.flatMap
import io.element.android.libraries.core.extensions.flatMapCatching
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.RoomScope
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.media.MediaUploadHandler
import io.element.android.libraries.matrix.api.room.CreateTimelineParams
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.api.timeline.Timeline
import io.element.android.libraries.matrix.api.timeline.item.event.LarpgramAlbum
import io.element.android.libraries.mediaupload.api.MediaOptimizationConfig
import io.element.android.libraries.mediaupload.api.MediaOptimizationConfigProvider
import io.element.android.libraries.mediaupload.api.MediaPreProcessor
import io.element.android.libraries.mediaupload.api.MediaSender
import io.element.android.libraries.mediaupload.api.MediaSenderFactory
import io.element.android.libraries.mediaupload.api.MediaSenderRoomFactory
import io.element.android.libraries.mediaupload.api.MediaUploadInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap

@ContributesBinding(RoomScope::class)
class DefaultMediaSenderFactory(
    private val preProcessor: MediaPreProcessor,
    private val room: JoinedRoom,
    private val mediaOptimizationConfigProvider: MediaOptimizationConfigProvider,
) : MediaSenderFactory {
    override fun create(
        timelineMode: Timeline.Mode,
    ): MediaSender {
        return DefaultMediaSender(
            preProcessor = preProcessor,
            room = room,
            timelineMode = timelineMode,
            mediaOptimizationConfigProvider = mediaOptimizationConfigProvider,
        )
    }
}

@ContributesBinding(SessionScope::class)
class DefaultMediaSenderRoomFactory(
    private val preProcessor: MediaPreProcessor,
    private val mediaOptimizationConfigProvider: MediaOptimizationConfigProvider,
) : MediaSenderRoomFactory {
    override fun create(
        room: JoinedRoom,
    ): MediaSender {
        return DefaultMediaSender(
            preProcessor = preProcessor,
            room = room,
            timelineMode = Timeline.Mode.Live,
            mediaOptimizationConfigProvider = mediaOptimizationConfigProvider,
        )
    }
}

class DefaultMediaSender(
    private val preProcessor: MediaPreProcessor,
    private val room: JoinedRoom,
    private val timelineMode: Timeline.Mode,
    private val mediaOptimizationConfigProvider: MediaOptimizationConfigProvider,
) : MediaSender {
    private val ongoingUploadJobs = ConcurrentHashMap<Job.Key, MediaUploadHandler>()
    val hasOngoingMediaUploads get() = ongoingUploadJobs.isNotEmpty()

    override suspend fun preProcessMedia(
        uri: Uri,
        mimeType: String,
        mediaOptimizationConfig: MediaOptimizationConfig,
    ): Result<MediaUploadInfo> {
        Timber.d("Pre-processing media | uri: ${mediaId(uri)} | mimeType: $mimeType")
        return preProcessor
            .process(
                uri = uri,
                mimeType = mimeType,
                deleteOriginal = false,
                mediaOptimizationConfig = mediaOptimizationConfig,
            )
    }

    override suspend fun sendPreProcessedMedia(
        mediaUploadInfo: MediaUploadInfo,
        caption: String?,
        formattedCaption: String?,
        inReplyToEventId: EventId?,
    ): Result<Unit> {
        val mediaLogId = mediaId(mediaUploadInfo.file)
        return getTimeline().flatMap {
            Timber.d("Started sending media $mediaLogId using timeline: ${it.mode}")
            it.sendMedia(
                uploadInfo = mediaUploadInfo,
                caption = caption,
                formattedCaption = formattedCaption,
                inReplyToEventId = inReplyToEventId,
            )
        }
            .handleSendResult(mediaLogId)
    }

    override suspend fun sendMedia(
        uri: Uri,
        mimeType: String,
        caption: String?,
        formattedCaption: String?,
        inReplyToEventId: EventId?,
        mediaOptimizationConfig: MediaOptimizationConfig,
    ): Result<Unit> {
        return preProcessor
            .process(
                uri = uri,
                mimeType = mimeType,
                deleteOriginal = false,
                mediaOptimizationConfig = mediaOptimizationConfig,
            )
            .flatMapCatching { info ->
                getTimeline().getOrThrow().sendMedia(
                    uploadInfo = info,
                    caption = caption,
                    formattedCaption = formattedCaption,
                    inReplyToEventId = inReplyToEventId,
                )
            }
            .handleSendResult(mediaId(uri))
    }

    override suspend fun sendVoiceMessage(
        uri: Uri,
        mimeType: String,
        waveForm: List<Float>,
        inReplyToEventId: EventId?,
    ): Result<Unit> {
        return preProcessor
            .process(
                uri = uri,
                mimeType = mimeType,
                deleteOriginal = true,
                mediaOptimizationConfig = mediaOptimizationConfigProvider.get(),
            )
            .flatMapCatching { info ->
                val audioInfo = (info as MediaUploadInfo.Audio).audioInfo
                val newInfo = MediaUploadInfo.VoiceMessage(
                    file = info.file,
                    audioInfo = audioInfo,
                    waveform = waveForm,
                )
                getTimeline().getOrThrow().sendMedia(
                    uploadInfo = newInfo,
                    caption = null,
                    formattedCaption = null,
                    inReplyToEventId = inReplyToEventId,
                )
            }
            .handleSendResult(mediaId(uri))
    }

    /**
     * Правка форка: альбом уходит не галереей MSC4274, а отдельными обычными медиа-сообщениями
     * с общей меткой в имени файла ([LarpgramAlbum]). Галерею понимает только Element X с флагом,
     * остальные клиенты показывали «неподдерживаемое событие». Подпись и ответ — у первой части,
     * как у альбома в Telegram. Все части сначала ставятся в очередь отправки (порядок сохраняется,
     * в ленте сразу появляется весь альбом), потом ждём загрузку каждой.
     */
    override suspend fun sendGallery(
        mediaUploadInfos: List<MediaUploadInfo>,
        caption: String?,
        formattedCaption: String?,
        inReplyToEventId: EventId?,
    ): Result<Unit> {
        val galleryLogId = "album[${mediaUploadInfos.size} items]"
        Timber.d("Sending $galleryLogId")
        val albumId = LarpgramAlbum.newAlbumId()
        val parts = mediaUploadInfos.mapIndexed { index, info ->
            val name = LarpgramAlbum.filename(albumId, index, mediaUploadInfos.size, info.file.extension)
            info.withFile(linkOrCopy(info.file, name))
        }
        return getTimeline()
            .flatMapCatching { timeline ->
                val handlers = parts.mapIndexed { index, part ->
                    timeline.enqueueMedia(
                        uploadInfo = part,
                        caption = caption.takeIf { index == 0 },
                        formattedCaption = formattedCaption.takeIf { index == 0 },
                        inReplyToEventId = inReplyToEventId.takeIf { index == 0 },
                    ).getOrThrow()
                }
                handlers.lastOrNull()?.let { ongoingUploadJobs[Job] = it }
                handlers.forEach { it.await().getOrThrow() }
                Result.success(Unit)
            }
            .also {
                parts.zip(mediaUploadInfos)
                    .filter { (part, original) -> part.file != original.file }
                    .forEach { (part, _) -> part.file.delete() }
            }
            .handleSendResult(galleryLogId)
    }

    /**
     * Файл части альбома под именем-меткой. Жёсткая ссылка вместо переименования: исходный файл
     * должен остаться на месте, экран предпросмотра переиспользует его при повторной отправке.
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

    private fun Result<Unit>.handleSendResult(mediaId: String) = this
        .onFailure { error ->
            val job = ongoingUploadJobs.remove(Job)
            Timber.e(error, "Sending media $mediaId failed. Removing ongoing upload job. Total: ${ongoingUploadJobs.size}")
            if (error !is CancellationException) {
                job?.cancel()
            }
        }
        .onSuccess {
            Timber.d("Sent media $mediaId successfully. Removing ongoing upload job. Total: ${ongoingUploadJobs.size}")
            ongoingUploadJobs.remove(Job)
        }

    private suspend fun Timeline.sendMedia(
        uploadInfo: MediaUploadInfo,
        caption: String?,
        formattedCaption: String?,
        inReplyToEventId: EventId?,
    ): Result<Unit> {
        val handler = enqueueMedia(uploadInfo, caption, formattedCaption, inReplyToEventId)

        // We handle the cancellations here manually, so we suppress the warning
        @Suppress("RunCatchingNotAllowed")
        return handler
            .mapCatching { uploadHandler ->
                Timber.d("Added ongoing upload job, total: ${ongoingUploadJobs.size + 1}")
                ongoingUploadJobs[Job] = uploadHandler
                uploadHandler.await()
            }
    }

    private suspend fun Timeline.enqueueMedia(
        uploadInfo: MediaUploadInfo,
        caption: String?,
        formattedCaption: String?,
        inReplyToEventId: EventId?,
    ): Result<MediaUploadHandler> {
        return when (uploadInfo) {
            is MediaUploadInfo.Image -> {
                sendImage(
                    file = uploadInfo.file,
                    thumbnailFile = uploadInfo.thumbnailFile,
                    imageInfo = uploadInfo.imageInfo,
                    caption = caption,
                    formattedCaption = formattedCaption,
                    inReplyToEventId = inReplyToEventId,
                )
            }
            is MediaUploadInfo.Video -> {
                sendVideo(
                    file = uploadInfo.file,
                    thumbnailFile = uploadInfo.thumbnailFile,
                    videoInfo = uploadInfo.videoInfo,
                    caption = caption,
                    formattedCaption = formattedCaption,
                    inReplyToEventId = inReplyToEventId,
                )
            }
            is MediaUploadInfo.Audio -> {
                sendAudio(
                    file = uploadInfo.file,
                    audioInfo = uploadInfo.audioInfo,
                    caption = caption,
                    formattedCaption = formattedCaption,
                    inReplyToEventId = inReplyToEventId,
                )
            }
            is MediaUploadInfo.VoiceMessage -> {
                sendVoiceMessage(
                    file = uploadInfo.file,
                    audioInfo = uploadInfo.audioInfo,
                    waveform = uploadInfo.waveform,
                    inReplyToEventId = inReplyToEventId,
                )
            }
            is MediaUploadInfo.AnyFile -> {
                sendFile(
                    file = uploadInfo.file,
                    fileInfo = uploadInfo.fileInfo,
                    caption = caption,
                    formattedCaption = formattedCaption,
                    inReplyToEventId = inReplyToEventId,
                )
            }
        }
    }

    private suspend fun getTimeline(): Result<Timeline> {
        return when (timelineMode) {
            is Timeline.Mode.Thread -> {
                room.createTimeline(CreateTimelineParams.Threaded(threadRootEventId = timelineMode.threadRootId))
            }
            else -> Result.success(room.liveTimeline)
        }
    }

    /**
     * Clean up any temporary files or resources used during the media processing.
     */
    override fun cleanUp() = preProcessor.cleanUp()
}

private fun MediaUploadInfo.withFile(file: File): MediaUploadInfo = when (this) {
    is MediaUploadInfo.Image -> copy(file = file)
    is MediaUploadInfo.Video -> copy(file = file)
    is MediaUploadInfo.Audio -> copy(file = file)
    is MediaUploadInfo.VoiceMessage -> copy(file = file)
    is MediaUploadInfo.AnyFile -> copy(file = file)
}

private fun mediaId(uri: Uri?): String = uri?.path.orEmpty().hash()
private fun mediaId(file: File): String = file.path.orEmpty().hash()
