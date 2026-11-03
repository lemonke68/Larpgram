/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.circles.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.tests.testutils.test
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DefaultCircleRecorderPresenterTest {
    @Test
    fun `close discards successful recorder callback arriving after release`() = runTest {
        var resultCallback: ((Result<File>) -> Unit)? = null
        val recorder = mockk<CircleRecorder>(relaxed = true)
        every { recorder.start(any()) } answers {
            resultCallback = firstArg()
            true
        }
        val sender = mockk<CircleSender>(relaxed = true)
        val presenter = DefaultCircleRecorderPresenter(FakeJoinedRoom(), FakeMatrixClient(), recorder, sender)
        val file = Files.createTempFile("circle-closed", ".mp4").toFile()
        presenter.test {
            val initial = awaitItem()
            initial.eventSink(CircleRecorderEvents.Open)
            awaitItem().eventSink(CircleRecorderEvents.StartRecording)
            awaitItem().eventSink(CircleRecorderEvents.Close)
            awaitItem()
            checkNotNull(resultCallback)(Result.success(file))
            assertThat(file.exists()).isFalse()
            coVerifyNoSend(sender)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun coVerifyNoSend(sender: CircleSender) {
        io.mockk.coVerify(exactly = 0) { sender.send(any(), any()) }
    }
}
