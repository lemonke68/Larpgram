/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.musicplayer.impl

import android.content.Context
import androidx.core.content.edit
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.di.annotations.AppCoroutineScope
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.matrix.api.MatrixClientProvider
import io.element.android.libraries.musicplayer.api.MusicOfflineStatus
import io.element.android.libraries.musicplayer.api.MusicTrack
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.security.MessageDigest

/**
 * «Сохранить на устройстве»: расшифрованные копии треков в files/music_offline — не в общем кэше
 * медиа, поэтому система их не вычищает. Удаляются только явно. Сохраняет по одному треку.
 */
@SingleIn(AppScope::class)
@Inject
class MusicOfflineStore(
    @ApplicationContext context: Context,
    private val matrixClientProvider: MatrixClientProvider,
    private val dispatchers: CoroutineDispatchers,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) {
    private val dir = File(context.filesDir, "music_offline")
    private val prefs = context.getSharedPreferences("larpgram_music_offline", Context.MODE_PRIVATE)
    private val queue = Channel<MusicTrack>(Channel.UNLIMITED)

    private val _status = MutableStateFlow<ImmutableMap<String, MusicOfflineStatus>>(persistentMapOf())
    val status: StateFlow<ImmutableMap<String, MusicOfflineStatus>> = _status.asStateFlow()

    init {
        appCoroutineScope.launch(dispatchers.io) {
            val saved = prefs.all.keys.filter { fileFor(it) != null }
            _status.update { current -> (saved.associateWith { MusicOfflineStatus.Saved } + current).toImmutableMap() }
            for (track in queue) save(track)
        }
    }

    /** Сохранённая копия, если она есть и цела. */
    fun fileFor(mediaKey: String): File? {
        val name = prefs.getString(mediaKey, null) ?: return null
        return File(dir, name).takeIf { it.isFile && it.length() > 0 }
    }

    fun save(tracks: List<MusicTrack>) {
        val toSave = tracks.distinctBy { it.mediaKey }.filter { _status.value[it.mediaKey] == null }
        if (toSave.isEmpty()) return
        _status.update { current -> (current + toSave.associate { it.mediaKey to MusicOfflineStatus.Saving }).toImmutableMap() }
        toSave.forEach { queue.trySend(it) }
    }

    fun remove(tracks: List<MusicTrack>) {
        val keys = tracks.map { it.mediaKey }.toSet()
        _status.update { current -> (current - keys).toImmutableMap() }
        appCoroutineScope.launch(dispatchers.io) {
            keys.forEach { key ->
                fileFor(key)?.delete()
                prefs.edit { remove(key) }
            }
        }
    }

    private suspend fun save(track: MusicTrack) {
        // Убрали из очереди, пока ждал своей очереди.
        if (_status.value[track.mediaKey] != MusicOfflineStatus.Saving) return
        val result = runCatching {
            dir.mkdirs()
            val client = matrixClientProvider.getOrRestore(track.sessionId).getOrThrow()
            val mediaFile = client.matrixMediaLoader
                .downloadMediaFile(source = track.mediaSource, mimeType = track.mimeType, filename = track.filename)
                .getOrThrow()
            val name = fileNameFor(track)
            val target = File(dir, name)
            val persisted = mediaFile.use { it.persist(target.absolutePath) }
            check(persisted && target.isFile) { "Could not persist the track" }
            name
        }
        result
            .onSuccess { name ->
                if (_status.value[track.mediaKey] == MusicOfflineStatus.Saving) {
                    prefs.edit { putString(track.mediaKey, name) }
                    _status.update { (it + (track.mediaKey to MusicOfflineStatus.Saved)).toImmutableMap() }
                } else {
                    // Пока качалось, пользователь передумал.
                    File(dir, name).delete()
                }
            }
            .onFailure {
                Timber.w(it, "Music: could not save a track on the device")
                _status.update { current -> (current - track.mediaKey).toImmutableMap() }
            }
    }

    private fun fileNameFor(track: MusicTrack): String {
        val extension = track.filename.substringAfterLast('.', "").takeIf { it.length in 1..5 && it.all(Char::isLetterOrDigit) }
        val hash = MessageDigest.getInstance("SHA-1").digest(track.mediaKey.toByteArray()).joinToString("") { "%02x".format(it) }
        return if (extension != null) "$hash.$extension" else hash
    }
}
