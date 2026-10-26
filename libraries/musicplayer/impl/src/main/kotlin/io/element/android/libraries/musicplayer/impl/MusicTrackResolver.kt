/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.musicplayer.impl

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.di.annotations.AppCoroutineScope
import io.element.android.libraries.matrix.api.MatrixClientProvider
import io.element.android.libraries.matrix.api.media.MediaFile
import io.element.android.libraries.musicplayer.api.MusicTrack
import io.element.android.libraries.musicplayer.api.MusicTrackTags
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber
import java.io.File
import java.io.IOException

/**
 * Скачивает (и расшифровывает) файл трека во временный файл через медиа-загрузчик сессии —
 * тот же путь, что у просмотрщика медиа. Держит открытыми несколько последних файлов
 * (текущий, следующий, предыдущий); вытесненные закрываются и удаляются с диска.
 */
@SingleIn(AppScope::class)
@Inject
class MusicTrackResolver(
    private val matrixClientProvider: MatrixClientProvider,
    private val tagsExtractor: MusicTagsExtractor,
    private val tagsStore: TrackTagsStore,
    private val dispatchers: CoroutineDispatchers,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) {
    private val lock = Any()
    private val files = LinkedHashMap<String, MediaFile>(MAX_OPEN_FILES, 0.75f, true)
    private val pending = HashMap<String, Deferred<File>>()
    private val knownTagKeys = HashSet<String>()

    private val _tagsResolved = MutableSharedFlow<Pair<String, MusicTrackTags>>(extraBufferCapacity = 16)
    val tagsResolved: SharedFlow<Pair<String, MusicTrackTags>> = _tagsResolved.asSharedFlow()

    fun markTagsKnown(keys: Collection<String>) = synchronized(lock) { knownTagKeys.addAll(keys) }

    /** Скачать заранее, не дожидаясь (следующий трек очереди). */
    fun prefetch(track: MusicTrack) {
        start(track)
    }

    suspend fun resolve(track: MusicTrack): File = start(track).await()

    private fun start(track: MusicTrack): Deferred<File> = synchronized(lock) {
        files[track.id]?.let { mediaFile ->
            val file = File(mediaFile.path())
            if (file.exists()) return@synchronized CompletableDeferred(file)
            files.remove(track.id)?.close()
        }
        pending[track.id]?.let { return@synchronized it }
        appCoroutineScope.async(dispatchers.io) { download(track) }.also { pending[track.id] = it }
    }

    private suspend fun download(track: MusicTrack): File {
        try {
            val client = matrixClientProvider.getOrRestore(track.sessionId).getOrElse { throw IOException("No session", it) }
            val mediaFile = client.matrixMediaLoader
                .downloadMediaFile(source = track.mediaSource, mimeType = track.mimeType, filename = track.filename)
                .getOrElse { throw IOException("Download failed", it) }
            val evicted = synchronized(lock) {
                files.put(track.id, mediaFile)?.takeIf { it !== mediaFile }?.let { listOf(it) }.orEmpty() + trim()
            }
            evicted.forEach { runCatching { it.close() } }
            val file = File(mediaFile.path())
            readTagsIfNeeded(track, file)
            return file
        } finally {
            synchronized(lock) { pending.remove(track.id) }
        }
    }

    private fun trim(): List<MediaFile> {
        val evicted = mutableListOf<MediaFile>()
        val iterator = files.entries.iterator()
        while (files.size > MAX_OPEN_FILES && iterator.hasNext()) {
            evicted.add(iterator.next().value)
            iterator.remove()
        }
        return evicted
    }

    private suspend fun readTagsIfNeeded(track: MusicTrack, file: File) {
        val isKnown = synchronized(lock) { !knownTagKeys.add(track.mediaKey) }
        if (isKnown) return
        val tags = tagsExtractor.extract(file, track.mediaKey) ?: MusicTrackTags()
        tagsStore.put(track.mediaKey, tags)
        _tagsResolved.emit(track.mediaKey to tags)
    }

    fun releaseAll() {
        val toClose = synchronized(lock) {
            pending.clear()
            files.values.toList().also { files.clear() }
        }
        toClose.forEach { mediaFile -> runCatching { mediaFile.close() }.onFailure { Timber.w(it, "Music: close failed") } }
    }

    private companion object {
        const val MAX_OPEN_FILES = 4
    }
}
