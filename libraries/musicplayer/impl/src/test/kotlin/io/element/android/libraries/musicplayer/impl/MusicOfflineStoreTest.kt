/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.musicplayer.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.FakeMatrixClientProvider
import io.element.android.libraries.matrix.test.media.FakeMatrixMediaLoader
import io.element.android.libraries.musicplayer.api.MusicOfflineStatus
import io.element.android.libraries.musicplayer.test.aMusicTrack
import io.element.android.tests.testutils.robolectric.RobolectricTest
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class MusicOfflineStoreTest : RobolectricTest() {
    private val context = RuntimeEnvironment.getApplication()
    private val mediaLoader = FakeMatrixMediaLoader()
    private val track = aMusicTrack(id = "mix", filename = "Mix.flac")

    @Test
    fun `a saved track is kept on the device and played without the network`() = runTest {
        mediaLoader.path = aDownloadedFile().absolutePath
        val store = createStore()
        settle()
        store.save(listOf(track))
        assertThat(store.status.value[track.mediaKey]).isEqualTo(MusicOfflineStatus.Saving)
        settle()
        assertThat(store.status.value[track.mediaKey]).isEqualTo(MusicOfflineStatus.Saved)
        val saved = requireNotNull(store.fileFor(track.mediaKey))
        assertThat(saved.readText()).isEqualTo("music")
        assertThat(saved.name).endsWith(".flac")

        mediaLoader.shouldFail = true
        val resolver = MusicTrackResolver(
            matrixClientProvider = FakeMatrixClientProvider { Result.success(FakeMatrixClient(matrixMediaLoader = mediaLoader)) },
            tagsExtractor = MusicTagsExtractor(context),
            tagsStore = InMemoryTrackTagsStore(),
            offlineStore = store,
            dispatchers = testCoroutineDispatchers(),
            appCoroutineScope = backgroundScope,
        )
        assertThat(resolver.resolve(track)).isEqualTo(saved)
    }

    @Test
    fun `saved tracks survive a restart and can be removed`() = runTest {
        mediaLoader.path = aDownloadedFile().absolutePath
        createStore().apply {
            settle()
            save(listOf(track))
            settle()
        }
        val restarted = createStore()
        settle()
        assertThat(restarted.status.value[track.mediaKey]).isEqualTo(MusicOfflineStatus.Saved)
        restarted.remove(listOf(track))
        assertThat(restarted.status.value[track.mediaKey]).isNull()
        settle()
        assertThat(restarted.fileFor(track.mediaKey)).isNull()
    }

    @Test
    fun `a failed download is not marked as saved`() = runTest {
        mediaLoader.shouldFail = true
        val store = createStore()
        settle()
        store.save(listOf(track))
        settle()
        assertThat(store.status.value[track.mediaKey]).isNull()
        assertThat(store.fileFor(track.mediaKey)).isNull()
    }

    /** Загрузка в фоне (backgroundScope) с задержкой: advanceUntilIdle её не ждёт. */
    private fun TestScope.settle() {
        advanceTimeBy(10_000)
        runCurrent()
    }

    private fun aDownloadedFile(): File = File.createTempFile("download", ".tmp", context.cacheDir).apply { writeText("music") }

    private fun TestScope.createStore() = MusicOfflineStore(
        context = context,
        matrixClientProvider = FakeMatrixClientProvider { Result.success(FakeMatrixClient(matrixMediaLoader = mediaLoader)) },
        dispatchers = testCoroutineDispatchers(),
        appCoroutineScope = backgroundScope,
    )
}
