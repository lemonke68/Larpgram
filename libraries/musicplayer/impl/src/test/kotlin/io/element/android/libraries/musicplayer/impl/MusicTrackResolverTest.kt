/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.musicplayer.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.media.MatrixMediaLoader
import io.element.android.libraries.matrix.api.media.MediaFile
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.FakeMatrixClientProvider
import io.element.android.libraries.musicplayer.test.aMusicTrack
import io.element.android.tests.testutils.testCoroutineDispatchers
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Test

class MusicTrackResolverTest {
    @Test
    fun `late uncooperative download is closed and cannot repopulate released cache`() = runTest {
        val finish = CompletableDeferred<Unit>()
        var closed = 0
        var downloads = 0
        val media = object : MediaFile {
            override fun path() = "/tmp/larpgram-resolver-test"
            override fun persist(path: String) = false
            override fun close() {
                closed++
            }
        }
        val loader = mockk<MatrixMediaLoader>()
        coEvery { loader.downloadMediaFile(any(), any(), any(), any()) } coAnswers {
            downloads++
            withContext(NonCancellable) { finish.await() }
            Result.success(media)
        }
        val provider = FakeMatrixClientProvider { Result.success(FakeMatrixClient(matrixMediaLoader = loader)) }
        val offline = mockk<MusicOfflineStore>()
        every { offline.fileFor(any()) } returns null
        val resolver = MusicTrackResolver(
            provider,
            mockk(relaxed = true),
            mockk(relaxed = true),
            offline,
            testCoroutineDispatchers(),
            backgroundScope
        )
        val track = aMusicTrack()
        resolver.prefetch(track)
        runCurrent()
        resolver.releaseAll()
        finish.complete(Unit)
        runCurrent()
        assertThat(closed).isEqualTo(1)
        resolver.prefetch(track)
        runCurrent()
        assertThat(downloads).isEqualTo(2)
        resolver.releaseAll()
    }
}
