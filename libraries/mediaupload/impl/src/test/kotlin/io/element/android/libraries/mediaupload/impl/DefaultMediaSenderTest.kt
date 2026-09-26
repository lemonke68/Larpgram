/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.mediaupload.impl

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.core.mimetype.MimeTypes
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.media.FileInfo
import io.element.android.libraries.matrix.api.media.ImageInfo
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.api.timeline.Timeline
import io.element.android.libraries.matrix.api.timeline.item.event.LarpgramAlbum
import io.element.android.libraries.matrix.test.media.FakeMediaUploadHandler
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.libraries.matrix.test.timeline.FakeTimeline
import io.element.android.libraries.mediaupload.api.MediaOptimizationConfig
import io.element.android.libraries.mediaupload.api.MediaOptimizationConfigProvider
import io.element.android.libraries.mediaupload.api.MediaPreProcessor
import io.element.android.libraries.mediaupload.api.MediaUploadInfo
import io.element.android.libraries.mediaupload.test.FakeMediaPreProcessor
import io.element.android.libraries.preferences.api.store.VideoCompressionPreset
import io.element.android.tests.testutils.lambda.any
import io.element.android.tests.testutils.lambda.lambdaRecorder
import io.element.android.tests.testutils.lambda.value
import io.element.android.tests.testutils.robolectric.RobolectricTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DefaultMediaSenderTest : RobolectricTest() {
    private val mediaOptimizationConfig = MediaOptimizationConfig(
        compressImages = true,
        videoCompressionPreset = VideoCompressionPreset.STANDARD,
    )

    @Test
    fun `given an attachment when sending it the preprocessor always runs`() = runTest {
        val preProcessor = FakeMediaPreProcessor()
        val sender = createDefaultMediaSender(
            preProcessor = preProcessor,
            room = FakeJoinedRoom(
                liveTimeline = FakeTimeline().apply {
                    sendFileLambda = lambdaRecorder<
                        File,
                        FileInfo,
                        String?,
                        String?,
                        EventId?,
                        Result<FakeMediaUploadHandler>,
                        > { _, _, _, _, _ ->
                        Result.success(FakeMediaUploadHandler())
                    }
                },
            )
        )

        val uri = Uri.parse("content://image.jpg")
        sender.sendMedia(uri = uri, mimeType = MimeTypes.Jpeg, mediaOptimizationConfig = mediaOptimizationConfig)

        assertThat(preProcessor.processCallCount).isEqualTo(1)
    }

    @Test
    fun `given an attachment when sending it the Room will call sendMedia`() = runTest {
        val sendImageResult =
            lambdaRecorder { _: File, _: File?, _: ImageInfo, _: String?, _: String?, _: EventId? ->
                Result.success(FakeMediaUploadHandler())
            }
        val room = FakeJoinedRoom(
            liveTimeline = FakeTimeline().apply {
                sendImageLambda = sendImageResult
            },
        )
        val sender = createDefaultMediaSender(room = room)

        val uri = Uri.parse("content://image.jpg")
        sender.sendMedia(uri = uri, mimeType = MimeTypes.Jpeg, mediaOptimizationConfig = mediaOptimizationConfig)
    }

    @Test
    fun `given a failure in the preprocessor when sending the whole process fails`() = runTest {
        val preProcessor = FakeMediaPreProcessor().apply {
            givenResult(Result.failure(Exception()))
        }
        val sender = createDefaultMediaSender(preProcessor)

        val uri = Uri.parse("content://image.jpg")
        val result = sender.sendMedia(uri = uri, mimeType = MimeTypes.Jpeg, mediaOptimizationConfig = mediaOptimizationConfig)

        assertThat(result.exceptionOrNull()).isNotNull()
    }

    @Test
    fun `given a failure in the media upload when sending the whole process fails`() = runTest {
        val preProcessor = FakeMediaPreProcessor().apply {
            givenImageResult()
        }
        val sendImageResult =
            lambdaRecorder { _: File, _: File?, _: ImageInfo, _: String?, _: String?, _: EventId? ->
                Result.failure<FakeMediaUploadHandler>(Exception())
            }
        val room = FakeJoinedRoom(
            liveTimeline = FakeTimeline().apply {
                sendImageLambda = sendImageResult
            },
        )
        val sender = createDefaultMediaSender(
            preProcessor = preProcessor,
            room = room,
        )

        val uri = Uri.parse("content://image.jpg")
        val result = sender.sendMedia(uri = uri, mimeType = MimeTypes.Jpeg, mediaOptimizationConfig = mediaOptimizationConfig)

        assertThat(result.exceptionOrNull()).isNotNull()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `given a cancellation in the media upload when sending the job is cancelled`() = runTest(StandardTestDispatcher()) {
        val sendFileResult =
            lambdaRecorder<File, FileInfo, String?, String?, EventId?, Result<FakeMediaUploadHandler>> { _, _, _, _, _ ->
                Result.success(FakeMediaUploadHandler())
            }
        val room = FakeJoinedRoom(
            liveTimeline = FakeTimeline().apply {
                sendFileLambda = sendFileResult
            },
        )
        val sender = createDefaultMediaSender(room = room)
        val sendJob = launch {
            val uri = Uri.parse("content://image.jpg")
            sender.sendMedia(uri = uri, mimeType = MimeTypes.Jpeg, mediaOptimizationConfig = mediaOptimizationConfig)
        }
        // Wait until several internal tasks run and the file is being uploaded
        advanceTimeBy(3L)

        // Assert the file is being uploaded
        assertThat(sender.hasOngoingMediaUploads).isTrue()

        // Cancel the coroutine
        sendJob.cancel()

        // Wait for the coroutine cleanup to happen
        advanceTimeBy(1L)

        // Assert the file is not being uploaded anymore
        assertThat(sender.hasOngoingMediaUploads).isFalse()
        sendFileResult.assertions().isCalledOnce()
    }

    @Test
    fun `an album is sent as ordinary media, caption and reply only on the first part`() = runTest {
        val dir = Files.createTempDirectory("album").toFile()
        val originals = (1..3).map { File(dir, "photo$it.jpg").apply { writeText("x") } }
        val sentNames = mutableListOf<String>()
        val sendImageResult =
            lambdaRecorder { file: File, _: File?, _: ImageInfo, _: String?, _: String?, _: EventId? ->
                sentNames.add(file.name)
                assertThat(file.exists()).isTrue()
                Result.success(FakeMediaUploadHandler())
            }
        val room = FakeJoinedRoom(
            liveTimeline = FakeTimeline().apply { sendImageLambda = sendImageResult },
        )
        val sender = createDefaultMediaSender(room = room)
        val replyTo = EventId("\$reply")

        val result = sender.sendGallery(
            mediaUploadInfos = originals.map { MediaUploadInfo.Image(file = it, imageInfo = anImageInfo(), thumbnailFile = null) },
            caption = "Отпуск",
            formattedCaption = null,
            inReplyToEventId = replyTo,
        )

        assertThat(result.isSuccess).isTrue()
        val parts = sentNames.map { LarpgramAlbum.parse(it) }
        assertThat(parts.map { it?.index }).containsExactly(0, 1, 2).inOrder()
        assertThat(parts.map { it?.albumId }.distinct()).hasSize(1)
        sendImageResult.assertions().isCalledExactly(3).withSequence(
            listOf(any(), any(), any(), value("Отпуск"), any(), value(replyTo)),
            listOf(any(), any(), any(), value(null), any(), value(null)),
            listOf(any(), any(), any(), value(null), any(), value(null)),
        )
        // Исходники остаются (повторная отправка с предпросмотра), временные части удалены.
        assertThat(originals.all { it.exists() }).isTrue()
        assertThat(dir.listFiles()!!.map { it.name }).containsExactly("photo1.jpg", "photo2.jpg", "photo3.jpg")
        dir.deleteRecursively()
    }

    private fun anImageInfo() = ImageInfo(
        height = 100,
        width = 100,
        mimetype = MimeTypes.Jpeg,
        size = 1000,
        thumbnailInfo = null,
        thumbnailSource = null,
        blurhash = null,
    )

    private fun createDefaultMediaSender(
        preProcessor: MediaPreProcessor = FakeMediaPreProcessor(),
        room: JoinedRoom = FakeJoinedRoom(),
        mediaOptimizationConfigProvider: MediaOptimizationConfigProvider = MediaOptimizationConfigProvider { mediaOptimizationConfig },
    ) = DefaultMediaSender(
        preProcessor = preProcessor,
        room = room,
        timelineMode = Timeline.Mode.Live,
        mediaOptimizationConfigProvider = mediaOptimizationConfigProvider,
    )
}
