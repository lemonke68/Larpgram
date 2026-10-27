/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.musicplayer.api

import androidx.compose.runtime.Immutable
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.media.MediaSource
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.StateFlow

/**
 * «Музыка в профиле», как в Telegram: треки, которые человек вынес в профиль. Хранятся в публичном
 * поле профиля Matrix (MSC4133), файлы — незашифрованные копии в медиа-репозитории сервера.
 * Обычные Matrix-клиенты поле просто не показывают.
 */
interface ProfileMusic {
    /** Свои треки (от новых к старым); null — ещё не загружены. */
    val ownTracks: StateFlow<ImmutableList<ProfileMusicTrack>?>

    /** Треки профиля [userId], от новых к старым. Пустой список — поля нет. */
    suspend fun tracks(userId: UserId): Result<List<ProfileMusicTrack>>

    /** Перечитать свои треки с сервера. */
    suspend fun refreshOwn()

    /** Уже в своём профиле: тот же файл или копия этого трека. */
    fun isInOwnProfile(track: MusicTrack): Boolean

    /**
     * Добавить трек наверх своего профиля: файл расшифровывается и загружается открытой копией
     * (у трека профиля — берётся как есть), затем поле профиля перезаписывается.
     */
    suspend fun add(track: MusicTrack, title: String, artist: String?): Result<Unit>

    /** Убрать трек из своего профиля (по файлу или по исходному треку). */
    suspend fun remove(track: MusicTrack): Result<Unit>
}

/** Трек «музыки профиля»: открытый mxc-файл и его подпись. */
@Immutable
data class ProfileMusicTrack(
    val url: String,
    val title: String,
    val artist: String? = null,
    val durationMs: Long? = null,
    val mimeType: String? = null,
    val size: Long? = null,
    val filename: String,
    /** Отпечаток исходного файла (хеш), чтобы узнать трек из чата, уже вынесенный в профиль. */
    val origin: String? = null,
)

/** Очередь «музыки профиля» [userId] — одна на человека. */
fun profileMusicQueueId(userId: UserId): String = "profile:${userId.value}"

/** Владелец очереди «музыки профиля» или null, если очередь не профильная. */
fun profileMusicOwner(queueId: String?): UserId? =
    queueId?.takeIf { it.startsWith("profile:") }?.removePrefix("profile:")?.let(::UserId)

fun ProfileMusicTrack.toMusicTrack(sessionId: SessionId) = MusicTrack(
    id = url,
    sessionId = sessionId,
    roomId = null,
    eventId = null,
    mediaSource = MediaSource(url),
    filename = filename,
    mimeType = mimeType,
    fileSize = size,
    durationMs = durationMs,
    title = title,
    artist = artist,
)
