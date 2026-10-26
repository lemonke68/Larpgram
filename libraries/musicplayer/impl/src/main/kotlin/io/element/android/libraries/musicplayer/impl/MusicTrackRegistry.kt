/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.musicplayer.impl

import android.net.Uri
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.musicplayer.api.MusicTrack
import java.util.concurrent.ConcurrentHashMap

/**
 * Треки очереди по id: плейлист ExoPlayer хранит только uri вида larpgram-music://track/<id>,
 * а файл по нему находит [MusicDataSourceResolver] на потоке загрузки.
 */
@SingleIn(AppScope::class)
@Inject
class MusicTrackRegistry {
    private val tracks = ConcurrentHashMap<String, MusicTrack>()

    fun register(newTracks: Collection<MusicTrack>) {
        newTracks.forEach { tracks[it.id] = it }
    }

    fun get(id: String): MusicTrack? = tracks[id]

    fun clear() = tracks.clear()

    companion object {
        const val SCHEME = "larpgram-music"
        private const val AUTHORITY = "track"

        fun uriFor(track: MusicTrack): Uri = Uri.Builder().scheme(SCHEME).authority(AUTHORITY).appendPath(track.id).build()

        fun idFrom(uri: Uri): String? = uri.takeIf { it.scheme == SCHEME }?.lastPathSegment
    }
}
