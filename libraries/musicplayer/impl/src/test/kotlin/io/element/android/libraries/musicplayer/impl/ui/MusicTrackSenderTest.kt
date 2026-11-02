/*
 * Copyright (c) 2026 Larpgram.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */
package io.element.android.libraries.musicplayer.impl.ui

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.media.MediaUploadHandler
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.FakeMatrixClientProvider
import io.element.android.libraries.matrix.test.room.FakeBaseRoom
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.libraries.matrix.test.timeline.FakeTimeline
import io.element.android.libraries.musicplayer.impl.MusicCopyPolicy
import io.element.android.libraries.musicplayer.impl.MusicTrackResolver
import io.element.android.libraries.musicplayer.test.aMusicTrack
import io.element.android.tests.testutils.testCoroutineDispatchers
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class MusicTrackSenderTest {
    @Test
    fun `each recipient gets original bytes when SDK handlers delete their inputs`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val original = File(context.cacheDir, "original.flac").apply { writeText("original lossless audio bytes") }
        val resolver = mockk<MusicTrackResolver>()
        coEvery { resolver.copyForExport(any(), any()) } coAnswers {
            val target = secondArg<File>().apply { parentFile?.mkdirs() }
            original.copyTo(target)
        }
        val client = FakeMatrixClient()
        val received = mutableListOf<String>()
        val paths = mutableSetOf<String>()
        val recipients = listOf(RoomId("!first:example.org"), RoomId("!second:example.org"))
        recipients.forEach { id ->
            val timeline = FakeTimeline().apply {
                sendAudioLambda = { file, _, caption, _, _ ->
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
        val sender = MusicTrackSender(
            context,
            client,
            resolver,
            MusicCopyPolicy(FakeMatrixClientProvider { Result.success(client) }),
            testCoroutineDispatchers(),
        )
        sender.send(aMusicTrack(), recipients, "Caption").getOrThrow()
        assertThat(received).containsExactly("original lossless audio bytes", "original lossless audio bytes")
        assertThat(paths).hasSize(2)
        assertThat(original.readText()).isEqualTo("original lossless audio bytes")
    }
}
