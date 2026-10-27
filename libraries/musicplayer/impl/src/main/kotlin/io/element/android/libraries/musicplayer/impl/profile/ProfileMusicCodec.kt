/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.musicplayer.impl.profile

import io.element.android.libraries.musicplayer.api.MusicTrack
import io.element.android.libraries.musicplayer.api.ProfileMusicTrack
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import java.security.MessageDigest

/**
 * Поле профиля «музыка» (MSC4133): `{"ru.mangokokos.music": [ {url, title, artist, duration_ms,
 * mimetype, size, filename, origin}, … ]}`, новые сверху. Весь профиль на сервере ограничен
 * 64 КиБ, поэтому список режется и по числу треков, и по размеру.
 */
internal object ProfileMusicCodec {
    /** Имя поля по правилам MSC4133: обратная запись домена, как у био (`ru.mangokokos.about`). */
    const val FIELD = "ru.mangokokos.music"

    const val MAX_TRACKS = 30

    /** Оставляем место остальному профилю (имя, аватар, био) в лимите 64 КиБ. */
    const val MAX_BYTES = 48 * 1024

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    /** Значение поля из ответа `GET /profile/{userId}/{field}`. Чужой мусор пропускается. */
    fun decodeResponse(body: String): List<ProfileMusicTrack> {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return emptyList()
        return decodeValue(root[FIELD])
    }

    /** Значение — массив; на случай сервера, принимающего только строки, понимаем и JSON в строке. */
    fun decodeValue(value: JsonElement?): List<ProfileMusicTrack> {
        val array = when (value) {
            is JsonArray -> value
            is JsonPrimitive -> value.contentOrNull?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() } as? JsonArray
            else -> null
        } ?: return emptyList()
        return array.mapNotNull { element ->
            runCatching { json.decodeFromJsonElement<StoredTrack>(element) }.getOrNull()
                ?.takeIf { it.url.startsWith("mxc://") && it.title.isNotBlank() }
                ?.toTrack()
        }.distinctBy { it.url }
    }

    fun encodeValue(tracks: List<ProfileMusicTrack>): JsonArray = JsonArray(tracks.map { json.encodeToJsonElement(StoredTrack.from(it)) })

    fun encodeBody(value: JsonElement): String = JsonObject(mapOf(FIELD to value)).toString()

    /** Не больше [MAX_TRACKS] и [MAX_BYTES]: лишние — самые старые, снизу. */
    fun limit(tracks: List<ProfileMusicTrack>): List<ProfileMusicTrack> {
        var result = tracks.take(MAX_TRACKS)
        while (result.isNotEmpty() && encodeBody(encodeValue(result)).toByteArray().size > MAX_BYTES) {
            result = result.dropLast(1)
        }
        return result
    }

    /** Новый трек наверх; тот же файл или копия того же исходника заменяется. */
    fun withAdded(current: List<ProfileMusicTrack>, track: ProfileMusicTrack): List<ProfileMusicTrack> =
        limit(listOf(track) + current.filterNot { it.isSameAs(track) })

    fun withRemoved(current: List<ProfileMusicTrack>, url: String?, origin: String?): List<ProfileMusicTrack> =
        current.filterNot { (url != null && it.url == url) || (origin != null && it.origin == origin) }

    private fun ProfileMusicTrack.isSameAs(other: ProfileMusicTrack) = url == other.url || (origin != null && origin == other.origin)

    /**
     * Отпечаток исходного файла трека: так трек из чата узнаётся в профиле, хотя там лежит
     * другой файл (открытая копия). Сам url зашифрованного файла в публичный профиль не кладём.
     */
    fun originOf(track: MusicTrack): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(track.mediaKey.toByteArray())
        return digest.take(ORIGIN_BYTES).joinToString("") { "%02x".format(it) }
    }

    private const val ORIGIN_BYTES = 16
}

@Serializable
private data class StoredTrack(
    val url: String,
    val title: String,
    val artist: String? = null,
    @SerialName("duration_ms") val durationMs: Long? = null,
    @SerialName("mimetype") val mimeType: String? = null,
    val size: Long? = null,
    val filename: String? = null,
    val origin: String? = null,
) {
    fun toTrack() = ProfileMusicTrack(
        url = url,
        title = title.trim(),
        artist = artist?.trim()?.takeIf { it.isNotEmpty() },
        durationMs = durationMs?.takeIf { it > 0 },
        mimeType = mimeType,
        size = size?.takeIf { it > 0 },
        filename = filename?.takeIf { it.isNotBlank() } ?: title,
        origin = origin,
    )

    companion object {
        fun from(track: ProfileMusicTrack) = StoredTrack(
            url = track.url,
            title = track.title,
            artist = track.artist,
            durationMs = track.durationMs,
            mimeType = track.mimeType,
            size = track.size,
            filename = track.filename,
            origin = track.origin,
        )
    }
}
