/*
 * Copyright (c) 2026 Larpgram.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */
package io.element.android.libraries.mediaviewer.impl.viewer

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.media.MediaSource
import io.element.android.libraries.matrix.api.media.MediaUploadHandler
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.media.FakeMatrixMediaLoader
import io.element.android.libraries.matrix.test.room.FakeBaseRoom
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.libraries.matrix.test.timeline.FakeTimeline
import io.element.android.libraries.matrix.ui.protection.RoomCopyProtection
import io.element.android.libraries.mediaviewer.api.aPdfMediaInfo
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class MediaCopySenderTest {
    @Test
    fun `image and thumbnail inputs survive SDK cleanup and a preview filename collision`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val original = File(context.cacheDir, "preview.jpg")
        val bitmap = android.graphics.Bitmap.createBitmap(3, 2, android.graphics.Bitmap.Config.ARGB_8888)
        original.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, it) }
        bitmap.recycle()
        val bytes = original.readBytes()
        val client = FakeMatrixClient(matrixMediaLoader = FakeMatrixMediaLoader().apply { path = original.path })
        var sent = 0
        val recipients = listOf(RoomId("!first:example.org"), RoomId("!second:example.org"))
        recipients.forEach { id ->
            val timeline = FakeTimeline().apply {
                sendImageLambda = { file, thumbnail, _, _, _, _ ->
                    assertThat(file.readBytes()).isEqualTo(bytes)
                    assertThat(requireNotNull(thumbnail).readBytes()).isEqualTo(bytes)
                    assertThat(file.path).isNotEqualTo(thumbnail.path)
                    sent++
                    Result.success(object : MediaUploadHandler {
                        override suspend fun await(): Result<Unit> {
                            file.delete()
                            thumbnail.delete()
                            return Result.success(Unit)
                        }
                        override fun cancel() {
                            file.delete()
                            thumbnail.delete()
                        }
                    })
                }
            }
            client.givenGetRoomResult(id, FakeJoinedRoom(baseRoom = FakeBaseRoom(roomId = id), liveTimeline = timeline))
        }
        val data = aMediaViewerPageData(mediaInfo = io.element.android.libraries.mediaviewer.api.anImageMediaInfo().copy(filename = "preview.jpg"))
            .copy(thumbnailSource = MediaSource("mxc://example.org/thumb"))
        MediaCopySender(context, client, RoomCopyProtection(client), testCoroutineDispatchers()).send(A_ROOM_ID, data, recipients, "").getOrThrow()
        assertThat(sent).isEqualTo(2)
        assertThat(original.readBytes()).isEqualTo(bytes)
    }

    @Test
    fun `each recipient gets the file after the previous SDK handler deletes its upload`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val original = File(context.cacheDir, "original.pdf").apply { writeText("original document bytes") }
        val loader = FakeMatrixMediaLoader().apply { path = original.path }
        val client = FakeMatrixClient(matrixMediaLoader = loader)
        val received = mutableListOf<String>()
        val paths = mutableSetOf<String>()
        val recipients = listOf(RoomId("!first:example.org"), RoomId("!second:example.org"))
        recipients.forEach { id ->
            val timeline = FakeTimeline().apply {
                sendFileLambda = { file, _, caption, _, _ ->
                    assertThat(caption).isEqualTo("Caption")
                    received.add(file.readText())
                    paths.add(file.path)
                    Result.success(object : MediaUploadHandler {
                        override suspend fun await(): Result<Unit> {
                            file.delete()
                            return Result.success(Unit)
                        }
                        override fun cancel() {
                            file.delete()
                        }
                    })
                }
            }
            client.givenGetRoomResult(id, FakeJoinedRoom(baseRoom = FakeBaseRoom(roomId = id), liveTimeline = timeline))
        }
        val sender = MediaCopySender(context, client, RoomCopyProtection(client), testCoroutineDispatchers())
        sender.send(A_ROOM_ID, aMediaViewerPageData(mediaInfo = aPdfMediaInfo(), mediaSource = MediaSource("mxc://example.org/file")), recipients, "Caption")
            .getOrThrow()
        assertThat(received).containsExactly("original document bytes", "original document bytes")
        assertThat(paths).hasSize(2)
        assertThat(original.readText()).isEqualTo("original document bytes")
    }
}
