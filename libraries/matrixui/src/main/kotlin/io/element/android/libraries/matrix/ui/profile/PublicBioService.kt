/*
 * Правка форка: «О себе», видимое всем, как в Telegram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.matrix.ui.profile

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.user.getLarpgramBio
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber
import java.net.URLEncoder

/**
 * Био лежит в публичном поле профиля Matrix (кастомные поля профиля, MSC4133 — в Synapse 1.149
 * стабильный `/_matrix/client/v3/profile/{userId}/{field}`). rust SDK таких полей не знает,
 * поэтому ходим HTTP напрямую, как за presence.
 *
 * Раньше био хранилось в приватной account data (`getLarpgramBio`) и было видно только себе;
 * [ownBio] переносит его в профиль при первом чтении.
 */
interface PublicBio {
    /** Био любого пользователя; null — не задано или сервер не ответил. */
    suspend fun bio(userId: UserId): String?

    /** Своё био; старое из account data публикуется в профиль. */
    suspend fun ownBio(): String?

    /** Пустая строка стирает поле. */
    suspend fun setBio(about: String?): Result<Unit>
}

/** Для тестов и превью: био нет, запись всегда успешна. */
class NoOpPublicBio : PublicBio {
    override suspend fun bio(userId: UserId): String? = null
    override suspend fun ownBio(): String? = null
    override suspend fun setBio(about: String?): Result<Unit> = Result.success(Unit)
}

@ContributesBinding(SessionScope::class)
@Inject
class PublicBioService(
    private val matrixClient: MatrixClient,
    private val okHttpClient: OkHttpClient,
    private val dispatchers: CoroutineDispatchers,
) : PublicBio {
    override suspend fun bio(userId: UserId): String? {
        val request = requestBuilder(userId)?.get()?.build() ?: return null
        return withContext(dispatchers.io) {
            runCatching {
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    JSONObject(response.body.string()).optString(FIELD).trim().ifEmpty { null }
                }
            }.getOrElse {
                Timber.w(it, "Не получилось прочитать био $userId")
                null
            }
        }
    }

    override suspend fun ownBio(): String? {
        val public = bio(matrixClient.sessionId)
        if (public != null) return public
        val legacy = matrixClient.getLarpgramBio() ?: return null
        setBio(legacy)
        return legacy
    }

    override suspend fun setBio(about: String?): Result<Unit> {
        val value = about?.trim().orEmpty()
        val builder = requestBuilder(matrixClient.sessionId)
            ?: return Result.failure(IllegalStateException("Нет токена сессии"))
        val request = if (value.isEmpty()) {
            builder.delete().build()
        } else {
            val body = JSONObject().put(FIELD, value).toString().toRequestBody("application/json".toMediaType())
            builder.put(body).build()
        }
        return withContext(dispatchers.io) {
            runCatching {
                okHttpClient.newCall(request).execute().use { response ->
                    // Удаление несуществующего поля — тоже успех.
                    if (!response.isSuccessful && !(value.isEmpty() && response.code == 404)) {
                        error("HTTP ${response.code}")
                    }
                }
            }
        }
    }

    private suspend fun requestBuilder(userId: UserId): Request.Builder? {
        val token = matrixClient.getAccessToken().getOrNull() ?: return null
        val base = matrixClient.homeserverUrl.trimEnd('/')
        val encodedUserId = URLEncoder.encode(userId.value, Charsets.UTF_8.name())
        return Request.Builder()
            .url("$base/_matrix/client/v3/profile/$encodedUserId/$FIELD")
            .header("Authorization", "Bearer $token")
    }

    companion object {
        /** Имя поля по правилам MSC4133: обратная запись домена. */
        const val FIELD = "ru.mangokokos.about"
    }
}
