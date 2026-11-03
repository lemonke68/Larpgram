/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.gifs.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.media.MediaUploadHandler
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.libraries.matrix.test.timeline.FakeTimeline
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test
import java.io.File
import java.nio.file.Files

class GifSenderTest {
    @Test
    fun `concurrent sends of same gif keep separate files until their uploads finish`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("GIF89a-first"))
        server.enqueue(MockResponse().setBody("GIF89a-second"))
        server.start()
        val dir = Files.createTempDirectory("gif-sender").toFile()
        try {
            val files = mutableListOf<File>()
            val gates = List(2) { CompletableDeferred<Unit>() }
            val timeline = FakeTimeline().apply {
                sendImageLambda = { file, _, _, _, _, _ ->
                    val index = files.size
                    files += file
                    Result.success(object : MediaUploadHandler {
                        override suspend fun await(): Result<Unit> {
                            gates[index].await()
                            return Result.success(Unit)
                        }
                        override fun cancel() {
                            gates[index].complete(Unit)
                        }
                    })
                }
            }
            val room = FakeJoinedRoom(liveTimeline = timeline)
            val sender = GifSender(OkHttpClient(), testCoroutineDispatchers(), dir)
            val gif = Gif("same", "same", server.url("/gif").toString(), server.url("/preview").toString(), 10, 10, null)
            val first = async { sender.send(room, gif) }
            advanceTimeBy(10)
            runCurrent()
            val second = async { sender.send(room, gif) }
            advanceTimeBy(10)
            runCurrent()
            assertThat(files).hasSize(2)
            assertThat(files[0]).isNotEqualTo(files[1])
            gates[0].complete(Unit)
            advanceTimeBy(10)
            runCurrent()
            assertThat(first.await().isSuccess).isTrue()
            assertThat(files[0].exists()).isFalse()
            assertThat(files[1].readText()).isEqualTo("GIF89a-second")
            gates[1].complete(Unit)
            assertThat(second.await().isSuccess).isTrue()
            assertThat(dir.listFiles()).isEmpty()
        } finally {
            server.shutdown()
            dir.deleteRecursively()
        }
    }
}
