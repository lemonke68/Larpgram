/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.musicplayer.impl.profile

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.musicplayer.api.MusicTrack
import io.element.android.libraries.musicplayer.api.ProfileMusic
import io.element.android.libraries.musicplayer.api.ProfileMusicTrack
import io.element.android.libraries.musicplayer.impl.MusicOfflineStore
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * «Музыка в профиле» поверх кастомного поля профиля Matrix (MSC4133, в Synapse стабильный
 * `/_matrix/client/v3/profile/{userId}/{field}`). rust SDK таких полей не знает, поэтому HTTP
 * напрямую, как у [io.element.android.libraries.matrix.ui.profile.PublicBio].
 *
 * Файлы в профиле — открытые копии (`POST /_matrix/media/v3/upload`): чужой клиент должен уметь их
 * скачать без ключей комнаты. Запись — read-modify-write под мьютексом.
 */
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
class DefaultProfileMusic(
    private val matrixClient: MatrixClient,
    okHttpClient: OkHttpClient,
    private val offlineStore: MusicOfflineStore,
    private val dispatchers: CoroutineDispatchers,
) : ProfileMusic {
    // Загрузка файла в десятки мегабайт не укладывается в обычные тайм-ауты.
    private val httpClient = okHttpClient.newBuilder()
        .writeTimeout(UPLOAD_TIMEOUT_MINUTES, TimeUnit.MINUTES)
        .readTimeout(UPLOAD_TIMEOUT_MINUTES, TimeUnit.MINUTES)
        .build()
    private val mutex = Mutex()
    private val _ownTracks = MutableStateFlow<ImmutableList<ProfileMusicTrack>?>(null)
    override val ownTracks: StateFlow<ImmutableList<ProfileMusicTrack>?> = _ownTracks.asStateFlow()

    override suspend fun tracks(userId: UserId): Result<List<ProfileMusicTrack>> = withContext(dispatchers.io) {
        runCatching { read(userId) }
            .onSuccess { if (userId == matrixClient.sessionId) _ownTracks.value = it.toImmutableList() }
            .onFailure { Timber.w(it, "Music: could not read the profile music of $userId") }
    }

    override suspend fun refreshOwn() {
        tracks(matrixClient.sessionId)
    }

    override fun isInOwnProfile(track: MusicTrack): Boolean {
        val own = _ownTracks.value ?: return false
        val origin = ProfileMusicCodec.originOf(track)
        return own.any { it.url == track.mediaKey || it.origin == origin }
    }

    override suspend fun add(track: MusicTrack, title: String, artist: String?): Result<Unit> = withContext(dispatchers.io) {
        runCatching {
            val entry = if (track.roomId == null && track.mediaSource.json == null) {
                // Трек чужого профиля: файл уже открытый, копировать нечего.
                ProfileMusicTrack(
                    url = track.mediaKey,
                    title = title,
                    artist = artist,
                    durationMs = track.durationMs,
                    mimeType = track.mimeType,
                    size = track.fileSize,
                    filename = track.filename,
                    origin = _ownTracks.value?.firstOrNull { it.url == track.mediaKey }?.origin,
                )
            } else {
                ProfileMusicTrack(
                    url = uploadOpenCopy(track),
                    title = title,
                    artist = artist,
                    durationMs = track.durationMs,
                    mimeType = track.mimeType,
                    size = track.fileSize,
                    filename = track.filename,
                    origin = ProfileMusicCodec.originOf(track),
                )
            }
            update { current -> ProfileMusicCodec.withAdded(current, entry) }
        }.onFailure { Timber.w(it, "Music: could not add a track to the profile") }
    }

    override suspend fun remove(track: MusicTrack): Result<Unit> = withContext(dispatchers.io) {
        runCatching {
            val origin = ProfileMusicCodec.originOf(track)
            update { current -> ProfileMusicCodec.withRemoved(current, url = track.mediaKey, origin = origin) }
        }.onFailure { Timber.w(it, "Music: could not remove a track from the profile") }
    }

    /** Свежий список с сервера → изменение → запись. Чужие правки с другого устройства не теряются. */
    private suspend fun update(transform: (List<ProfileMusicTrack>) -> List<ProfileMusicTrack>) = mutex.withLock {
        val current = read(matrixClient.sessionId)
        val updated = transform(current)
        write(updated)
        _ownTracks.value = updated.toImmutableList()
    }

    private suspend fun read(userId: UserId): List<ProfileMusicTrack> {
        val request = profileRequest(userId).get().build()
        return httpClient.newCall(request).execute().use { response ->
            when {
                response.isSuccessful -> ProfileMusicCodec.decodeResponse(response.body.string())
                // Поля нет (или профиль скрыт от нас) — музыки нет.
                response.code == HTTP_NOT_FOUND || response.code == HTTP_FORBIDDEN -> emptyList()
                else -> throw IOException("HTTP ${response.code}")
            }
        }
    }

    private suspend fun write(tracks: List<ProfileMusicTrack>) {
        if (tracks.isEmpty()) {
            httpClient.newCall(profileRequest(matrixClient.sessionId).delete().build()).execute().use { response ->
                if (!response.isSuccessful && response.code != HTTP_NOT_FOUND) throw IOException("HTTP ${response.code}")
            }
            return
        }
        val value = ProfileMusicCodec.encodeValue(tracks)
        if (put(value) == HTTP_BAD_REQUEST) {
            // Сервер принимает в полях профиля только строки — кладём тот же JSON строкой.
            val code = put(JsonPrimitive(value.toString()))
            if (code !in 200..299) throw IOException("HTTP $code")
        }
    }

    /** Код ответа; 2xx — успех, 400 — повод попробовать строкой, остальное — ошибка. */
    private suspend fun put(value: JsonElement): Int {
        val body = ProfileMusicCodec.encodeBody(value).toRequestBody(JSON)
        return httpClient.newCall(profileRequest(matrixClient.sessionId).put(body).build()).execute().use { response ->
            when {
                response.isSuccessful || response.code == HTTP_BAD_REQUEST -> response.code
                else -> throw IOException("HTTP ${response.code}")
            }
        }
    }

    /** Расшифровать трек (или взять сохранённую на устройстве копию) и загрузить открытым файлом. */
    private suspend fun uploadOpenCopy(track: MusicTrack): String {
        offlineStore.fileFor(track.mediaKey)?.let { return upload(it, track) }
        val mediaFile = matrixClient.matrixMediaLoader
            .downloadMediaFile(source = track.mediaSource, mimeType = track.mimeType, filename = track.filename)
            .getOrElse { throw IOException("Download failed", it) }
        return mediaFile.use { upload(File(it.path()), track) }
    }

    private suspend fun upload(file: File, track: MusicTrack): String {
        val maxSize = matrixClient.getMaxFileUploadSize().getOrNull()
        if (maxSize != null && maxSize > 0 && file.length() > maxSize) throw IOException("The file is too big for the server")
        val token = matrixClient.getAccessToken().getOrThrow()
        val base = matrixClient.homeserverUrl.trimEnd('/')
        val filename = URLEncoder.encode(track.filename, Charsets.UTF_8.name())
        val mediaType = track.mimeType?.toMediaTypeOrNull() ?: OCTET_STREAM
        val request = Request.Builder()
            .url("$base/_matrix/media/v3/upload?filename=$filename")
            .header("Authorization", "Bearer $token")
            .post(file.asRequestBody(mediaType))
            .build()
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Upload failed: HTTP ${response.code}")
            val root = kotlinx.serialization.json.Json.parseToJsonElement(response.body.string()) as JsonObject
            root["content_uri"]?.jsonPrimitive?.content?.takeIf { it.startsWith("mxc://") } ?: throw IOException("No content_uri")
        }
    }

    private suspend fun profileRequest(userId: UserId): Request.Builder {
        val token = matrixClient.getAccessToken().getOrThrow()
        val base = matrixClient.homeserverUrl.trimEnd('/')
        val encodedUserId = URLEncoder.encode(userId.value, Charsets.UTF_8.name())
        return Request.Builder()
            .url("$base/_matrix/client/v3/profile/$encodedUserId/${ProfileMusicCodec.FIELD}")
            .header("Authorization", "Bearer $token")
    }

    private companion object {
        const val UPLOAD_TIMEOUT_MINUTES = 5L
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_FORBIDDEN = 403
        const val HTTP_NOT_FOUND = 404
        val JSON = "application/json".toMediaType()
        val OCTET_STREAM = "application/octet-stream".toMediaType()
    }
}
