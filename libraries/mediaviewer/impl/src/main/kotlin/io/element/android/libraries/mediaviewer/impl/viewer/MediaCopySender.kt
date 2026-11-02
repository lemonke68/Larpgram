/*
 * Copyright (c) 2026 Larpgram.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */
package io.element.android.libraries.mediaviewer.impl.viewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import dev.zacsweers.metro.Inject
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.media.AudioInfo
import io.element.android.libraries.matrix.api.media.FileInfo
import io.element.android.libraries.matrix.api.media.ImageInfo
import io.element.android.libraries.matrix.api.media.ThumbnailInfo
import io.element.android.libraries.matrix.api.media.VideoInfo
import io.element.android.libraries.matrix.api.room.ForwardEventException
import io.element.android.libraries.matrix.ui.protection.RoomCopyProtection
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

/** Send only the selected gallery file, preserving original bytes and omitting author metadata. */
@Inject
class MediaCopySender(
    @ApplicationContext private val context: Context,
    private val client: MatrixClient,
    private val copyProtection: RoomCopyProtection,
    private val dispatchers: CoroutineDispatchers,
) {
    suspend fun send(
        sourceRoomId: RoomId,
        data: MediaViewerPageData.MediaViewerData,
        recipients: List<RoomId>,
        caption: String,
    ): Result<Unit> = withContext(dispatchers.io) {
        runCatchingExceptions {
            if (recipients.isEmpty()) return@runCatchingExceptions
            copyProtection.requireAllowed(sourceRoomId)
            val directory = File(context.cacheDir, "temp/media-send/${UUID.randomUUID()}")
            try {
                directory.mkdirs()
                val filename = data.mediaInfo.filename.replace('/', '_').takeUnless { it.isBlank() || it == "." || it == ".." } ?: "media"
                val sourceFile = File(directory, "source/$filename").apply { parentFile?.mkdirs() }
                val file = client.matrixMediaLoader.downloadMediaFile(data.mediaSource, data.mediaInfo.mimeType, filename)
                    .getOrThrow().use { File(it.path()).copyTo(sourceFile) }
                val mime = data.mediaInfo.mimeType
                val dimensions = if (mime.startsWith("image/")) {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.path, bounds)
                    bounds.outWidth.toLong().takeIf { it > 0 } to bounds.outHeight.toLong().takeIf { it > 0 }
                } else {
                    null
                }
                val video = if (mime.startsWith("video/")) readVideo(file) else null
                val thumbnail = if (dimensions != null || video != null) {
                    val destination = File(directory, "thumbnail/preview.jpg").apply { parentFile?.mkdirs() }
                    val source = data.thumbnailSource
                    val downloaded = source?.let {
                        client.matrixMediaLoader.downloadMediaFile(it, "image/jpeg", "preview.jpg").getOrNull()?.use { media ->
                            File(media.path()).copyTo(destination)
                        }
                    }
                    downloaded ?: if (video != null) makeVideoPreview(file, destination) else null
                } else {
                    null
                }
                val thumbnailInfo = thumbnail?.let { preview ->
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(preview.path, bounds)
                    ThumbnailInfo(bounds.outHeight.toLong(), bounds.outWidth.toLong(), bounds.outMimeType, preview.length())
                }
                withContext(NonCancellable) {
                    val failed = recipients.distinct().filter { id ->
                        runCatchingExceptions {
                            copyProtection.requireAllowed(sourceRoomId)
                            val room = client.getJoinedRoom(id) ?: error("Recipient is not joined")
                            room.use {
                                // SDK handlers delete their inputs; each recipient needs its own
                                // upload files while the master stays available for the next one.
                                val recipientDirectory = File(directory, UUID.randomUUID().toString()).apply { mkdirs() }
                                val recipientFile = file.copyTo(File(recipientDirectory, file.name))
                                val recipientThumbnail = thumbnail?.copyTo(
                                    File(recipientDirectory, "thumbnail/${thumbnail.name}").apply { parentFile?.mkdirs() }
                                )
                                val timeline = it.liveTimeline
                                val body = caption.takeIf(String::isNotBlank)
                                val upload = when {
                                    dimensions != null -> timeline.sendImage(
                                        recipientFile,
                                        recipientThumbnail,
                                        ImageInfo(dimensions.second, dimensions.first, mime, file.length(), thumbnailInfo, null, null),
                                        body,
                                        null,
                                        null,
                                    )
                                    video != null -> timeline.sendVideo(
                                        recipientFile,
                                        recipientThumbnail,
                                        VideoInfo(video.duration?.milliseconds, video.height, video.width, mime, file.length(), thumbnailInfo, null, null),
                                        body,
                                        null,
                                        null,
                                    )
                                    mime.startsWith("audio/") -> timeline.sendAudio(recipientFile, AudioInfo(null, file.length(), mime), body, null, null)
                                    else -> timeline.sendFile(recipientFile, FileInfo(mime, file.length(), null, null), body, null, null)
                                }
                                upload.getOrThrow().await().getOrThrow()
                            }
                        }.isFailure
                    }
                    if (failed.isNotEmpty()) throw ForwardEventException(failed)
                }
            } finally {
                directory.deleteRecursively()
            }
        }
    }
}

private data class VideoMetadata(val width: Long?, val height: Long?, val duration: Long?)

private fun readVideo(file: File): VideoMetadata = MediaMetadataRetriever().let { metadata ->
    try {
        metadata.setDataSource(file.path)
        val width = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toLongOrNull()
        val height = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toLongOrNull()
        val rotated = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() in listOf(90, 270)
        VideoMetadata(
            width = if (rotated) height else width,
            height = if (rotated) width else height,
            duration = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
        )
    } finally {
        metadata.release()
    }
}

private fun makeVideoPreview(file: File, destination: File): File? = runCatchingExceptions {
    val metadata = MediaMetadataRetriever()
    try {
        metadata.setDataSource(file.path)
        val frame = metadata.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return@runCatchingExceptions null
        val scale = minOf(1f, 320f / maxOf(frame.width, frame.height))
        val preview = Bitmap.createScaledBitmap(frame, (frame.width * scale).toInt().coerceAtLeast(1), (frame.height * scale).toInt().coerceAtLeast(1), true)
        try {
            destination.outputStream().use { check(preview.compress(Bitmap.CompressFormat.JPEG, 80, it)) }
        } finally {
            if (preview !== frame) preview.recycle()
            frame.recycle()
        }
        destination
    } finally {
        metadata.release()
    }
}.getOrNull()
