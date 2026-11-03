/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.category

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import coil3.SingletonImageLoader
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.element.android.features.preferences.impl.R
import io.element.android.libraries.androidutils.filesize.FileSizeFormatter
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarDispatcher
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarMessage
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.media.MediaCacheUsage
import io.element.android.libraries.musicplayer.api.MusicAutoDownload
import io.element.android.libraries.musicplayer.api.MusicAutoDownloadStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Правка форка: удаляется только восстанавливаемый Coil-кэш, не SDK/записи/очередь загрузок. */
interface ImageCacheStorage {
    suspend fun size(): Long
    suspend fun clear()
}

@ContributesBinding(AppScope::class)
class DefaultImageCacheStorage(
    @ApplicationContext private val context: Context,
    private val dispatchers: CoroutineDispatchers,
) : ImageCacheStorage {
    override suspend fun size(): Long = withContext(dispatchers.io) { SingletonImageLoader.get(context).diskCache?.size ?: 0L }

    override suspend fun clear() {
        withContext(dispatchers.io) {
            SingletonImageLoader.get(context).diskCache?.clear()
            SingletonImageLoader.get(context).memoryCache?.clear()
        }
    }
}

data class MediaStorageState(
    val imageCache: String?,
    val usage: MediaCacheUsage?,
    val isLoading: Boolean,
    val isClearing: Boolean,
    val formatSize: (Long) -> String,
    /** Автозагрузка треков из чатов. */
    val musicAutoDownload: MusicAutoDownload = MusicAutoDownload.Default,
    val onMusicAutoDownloadChange: (MusicAutoDownload) -> Unit = {},
    val clearMediaCache: () -> Unit,
)

@Inject
class MediaStoragePresenter(
    private val cache: ImageCacheStorage,
    private val client: MatrixClient,
    private val formatter: FileSizeFormatter,
    private val snackbarDispatcher: SnackbarDispatcher,
    private val musicAutoDownloadStore: MusicAutoDownloadStore,
) : Presenter<MediaStorageState> {
    @Composable
    override fun present(): MediaStorageState {
        val scope = rememberCoroutineScope()
        var imageCache by remember(client) { mutableStateOf<String?>(null) }
        var usage by remember(client) { mutableStateOf<MediaCacheUsage?>(null) }
        var loading by remember(client) { mutableStateOf(true) }
        var clearing by remember(client) { mutableStateOf(false) }
        val musicAutoDownload by musicAutoDownloadStore.settings.collectAsState()

        suspend fun refresh() {
            loading = true
            runCatchingExceptions {
                val refreshedImages = formatter.format(cache.size())
                val refreshedUsage = client.getMediaCacheUsage().getOrThrow()
                imageCache = refreshedImages
                usage = refreshedUsage
            }.onFailure {
                imageCache = null
                usage = null
                snackbarDispatcher.post(SnackbarMessage(R.string.larpgram_storage_error))
            }
            loading = false
        }
        LaunchedEffect(client) { refresh() }
        return MediaStorageState(
            imageCache = imageCache,
            usage = usage,
            isLoading = loading,
            isClearing = clearing,
            formatSize = { formatter.format(it) },
            musicAutoDownload = musicAutoDownload,
            onMusicAutoDownloadChange = musicAutoDownloadStore::update,
        ) {
            if (!clearing && !loading && usage != null) {
                clearing = true
                scope.launch {
                    try {
                        // Clear both independent caches even if one reports a failure.
                        val sdk = client.clearMediaCache()
                        val images = runCatchingExceptions { cache.clear() }
                        if (sdk.isFailure || images.isFailure) {
                            snackbarDispatcher.post(SnackbarMessage(R.string.larpgram_storage_clear_error))
                        }
                        refresh()
                    } finally {
                        clearing = false
                    }
                }
            }
        }
    }
}
