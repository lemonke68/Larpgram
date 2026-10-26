/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.mediaviewer.impl.music

import androidx.compose.runtime.Immutable
import io.element.android.libraries.core.mimetype.MimeTypes.isMimeTypeAudio
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.media.MediaSource
import io.element.android.libraries.mediaviewer.api.MediaInfo
import io.element.android.libraries.mediaviewer.impl.model.MediaItem
import io.element.android.libraries.musicplayer.api.MusicPlayer
import io.element.android.libraries.musicplayer.api.MusicTrack
import io.element.android.libraries.musicplayer.api.MusicTrackTags
import io.element.android.libraries.musicplayer.api.displayArtist
import io.element.android.libraries.musicplayer.api.displayTitle

/** Музыка — аудио-файл (в том числе отправленный документом), но не голосовое. */
internal fun MediaInfo.isMusic(): Boolean = mimeType.isMimeTypeAudio() && waveform == null

internal fun musicTrackId(eventId: EventId?, mediaSource: MediaSource): String = eventId?.value ?: mediaSource.safeUrl

internal fun musicTrackOf(
    sessionId: SessionId,
    roomId: RoomId,
    eventId: EventId?,
    mediaSource: MediaSource,
    info: MediaInfo,
) = MusicTrack(
    id = musicTrackId(eventId, mediaSource),
    sessionId = sessionId,
    roomId = roomId,
    eventId = eventId,
    mediaSource = mediaSource,
    filename = info.filename,
    mimeType = info.mimeType,
    fileSize = info.fileSize,
)

/** Очередь «Музыки» комнаты — один id на комнату, какой бы экран её ни собрал. */
internal fun musicQueueId(roomId: RoomId): String = roomId.value

/**
 * Аудио комнаты в порядке ленты (от старых к новым). Списки галереи идут от новых к старым.
 */
internal fun List<MediaItem>.toMusicTracks(sessionId: SessionId, roomId: RoomId): List<MusicTrack> =
    asReversed().mapNotNull { item ->
        when (item) {
            is MediaItem.Audio -> musicTrackOf(sessionId, roomId, item.eventId, item.mediaSource, item.mediaInfo)
            is MediaItem.File -> item.takeIf { it.mediaInfo.isMusic() }?.let {
                musicTrackOf(sessionId, roomId, it.eventId, it.mediaSource, it.mediaInfo)
            }
            else -> null
        }
    }

/** Поиск по названию, артисту, альбому и имени файла. */
internal fun List<MusicTrack>.filterMusic(query: String, tags: Map<String, MusicTrackTags>): List<MusicTrack> {
    val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return this
    return filter { track ->
        val trackTags = tags[track.mediaKey]
        val haystack = listOfNotNull(
            track.displayTitle(trackTags),
            track.displayArtist(trackTags),
            trackTags?.album,
            track.filename,
        ).joinToString(" ").lowercase()
        words.all { it in haystack }
    }
}

/** Страница просмотрщика с треком: клиент общего плеера, а не свой ExoPlayer. */
@Immutable
class MusicPagePlayback(
    val trackId: String,
    val player: MusicPlayer,
    /** Запустить очередь комнаты с этого трека. */
    val onPlay: () -> Unit,
    /** Страница открыта тапом по этому треку — играть сразу, как в Telegram. */
    val autoPlay: Boolean,
    /** Автозапуск отработал: при возврате на страницу свайпом повторно не включать. */
    val onAutoPlayHandled: () -> Unit = {},
)
