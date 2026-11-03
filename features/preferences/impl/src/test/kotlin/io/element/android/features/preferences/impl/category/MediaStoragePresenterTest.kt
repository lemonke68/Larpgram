/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.category

import com.google.common.truth.Truth.assertThat
import io.element.android.features.preferences.impl.R
import io.element.android.libraries.androidutils.filesize.FakeFileSizeFormatter
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarDispatcher
import io.element.android.libraries.matrix.api.media.MediaCacheUsage
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.musicplayer.test.FakeMusicAutoDownloadStore
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class MediaStoragePresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    private val usage = MediaCacheUsage(10, 20, 30, 40, 5, 50, 200)

    @Test
    fun `usage separates media categories uploads image cache and database allocation`() = runTest {
        val presenter = MediaStoragePresenter(
            cache = FakeImageCache(),
            client = FakeMatrixClient(getMediaCacheUsageLambda = { Result.success(usage) }),
            formatter = FakeFileSizeFormatter(),
            snackbarDispatcher = SnackbarDispatcher(),
            musicAutoDownloadStore = FakeMusicAutoDownloadStore(),
        )
        presenter.test {
            assertThat(awaitItem().isLoading).isTrue()
            val ready = awaitItem()
            assertThat(ready.isLoading).isFalse()
            assertThat(ready.imageCache).isEqualTo("99 Bytes")
            assertThat(ready.usage).isEqualTo(usage)
            assertThat(ready.formatSize(105)).isEqualTo("105 Bytes")
        }
    }

    @Test
    fun `clear is guarded against repeated taps and refreshes both independent caches`() = runTest {
        val finishClear = CompletableDeferred<Unit>()
        val cache = FakeImageCache(onClear = { finishClear.await() })
        var sdkCleared = 0
        val client = FakeMatrixClient(
            getMediaCacheUsageLambda = { Result.success(if (sdkCleared == 0) usage else usage.copy(images = 0)) },
            clearMediaCacheLambda = {
                sdkCleared++
                Result.success(Unit)
            },
        )
        MediaStoragePresenter(cache, client, FakeFileSizeFormatter(), SnackbarDispatcher(), FakeMusicAutoDownloadStore()).test {
            awaitItem()
            val ready = awaitItem()
            ready.clearMediaCache()
            ready.clearMediaCache()
            assertThat(awaitItem().isClearing).isTrue()
            assertThat(sdkCleared).isEqualTo(1)
            finishClear.complete(Unit)
            val done = awaitItem()
            assertThat(done.isClearing).isFalse()
            assertThat(done.isLoading).isFalse()
            assertThat(done.usage?.images).isEqualTo(0)
            assertThat(done.usage?.protectedBytes).isEqualTo(50)
            assertThat(done.imageCache).isEqualTo("0 Bytes")
            assertThat(cache.clearCalls).isEqualTo(1)
        }
    }

    @Test
    fun `SDK clear failure still clears image cache and reports failure`() = runTest {
        val finishClear = CompletableDeferred<Unit>()
        val cache = FakeImageCache(onClear = { finishClear.await() })
        val snackbar = SnackbarDispatcher()
        val client = FakeMatrixClient(
            getMediaCacheUsageLambda = { Result.success(usage) },
            clearMediaCacheLambda = { Result.failure(IllegalStateException("store busy")) },
        )
        MediaStoragePresenter(cache, client, FakeFileSizeFormatter(), snackbar, FakeMusicAutoDownloadStore()).test {
            awaitItem()
            awaitItem().clearMediaCache()
            assertThat(awaitItem().isClearing).isTrue()
            finishClear.complete(Unit)
            val done = awaitItem()
            assertThat(done.isClearing).isFalse()
            assertThat(done.usage).isEqualTo(usage)
            assertThat(done.imageCache).isEqualTo("0 Bytes")
            assertThat(cache.clearCalls).isEqualTo(1)
            assertThat(snackbar.snackbarMessage.first()?.messageResId).isEqualTo(R.string.larpgram_storage_clear_error)
        }
    }

    @Test
    fun `failed accounting does not present a fabricated zero or enable clearing`() = runTest {
        var sdkCleared = 0
        val client = FakeMatrixClient(
            getMediaCacheUsageLambda = { Result.failure(IllegalStateException("store busy")) },
            clearMediaCacheLambda = {
                sdkCleared++
                Result.success(Unit)
            },
        )
        MediaStoragePresenter(FakeImageCache(), client, FakeFileSizeFormatter(), SnackbarDispatcher(), FakeMusicAutoDownloadStore()).test {
            awaitItem()
            val failed = awaitItem()
            assertThat(failed.isLoading).isFalse()
            assertThat(failed.usage).isNull()
            assertThat(failed.imageCache).isNull()
            failed.clearMediaCache()
            assertThat(sdkCleared).isEqualTo(0)
            expectNoEvents()
        }
    }

    private class FakeImageCache(private val onClear: suspend () -> Unit = {}) : ImageCacheStorage {
        var clearCalls = 0
        private var bytes = 99L
        override suspend fun size(): Long = bytes
        override suspend fun clear() {
            clearCalls++
            onClear()
            bytes = 0
        }
    }
}
