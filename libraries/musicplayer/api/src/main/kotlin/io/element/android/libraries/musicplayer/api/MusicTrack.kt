/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.musicplayer.api

import androidx.compose.runtime.Immutable
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.media.MediaSource

/**
 * Трек очереди: ссылка на аудио-событие комнаты, не копия файла. Файл качается плеером
 * лениво, перед воспроизведением.
 */
@Immutable
data class MusicTrack(
    /** Стабильный ключ в очереди: eventId, а у локального эха — url медиа. */
    val id: String,
    val sessionId: SessionId,
    val roomId: RoomId,
    val eventId: EventId?,
    val mediaSource: MediaSource,
    val filename: String,
    val mimeType: String?,
    val fileSize: Long? = null,
    val durationMs: Long? = null,
) {
    /** Ключ файла для тегов и позиции: один файл, переотправленный дважды, делит их. */
    val mediaKey: String get() = mediaSource.safeUrl
}

/** Теги из самого файла (ID3 / Vorbis / MP4), заполняются, когда файл впервые скачан. */
@Immutable
data class MusicTrackTags(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    /** Путь к уменьшенной обложке на диске приложения. */
    val artworkPath: String? = null,
)

fun MusicTrack.displayTitle(tags: MusicTrackTags?): String =
    tags?.title?.takeIf { it.isNotBlank() } ?: MusicFilenames.parse(filename).title

fun MusicTrack.displayArtist(tags: MusicTrackTags?): String? =
    tags?.artist?.takeIf { it.isNotBlank() } ?: MusicFilenames.parse(filename).artist
