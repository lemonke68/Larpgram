/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.topbars

import androidx.compose.runtime.Immutable
import dev.zacsweers.metro.Inject
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.matrix.api.ClientUrlContentFetcher
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.UserId
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.net.URLEncoder

/**
 * Правка форка: присутствие собеседника ЛС для шапки чата («в сети», «был(а) в 14:20»).
 *
 * rust-SDK presence не отдаёт, поэтому спрашиваем Synapse напрямую:
 * `GET /_matrix/client/v3/presence/{userId}/status` с токеном сессии.
 *
 * @param isOnline собеседник сейчас активен.
 * @param lastActiveAtMillis когда был активен в последний раз (по часам телефона), если известно.
 */
@Immutable
data class DmPresence(
    val isOnline: Boolean,
    val lastActiveAtMillis: Long?,
)

@Inject
class DmPresenceFetcher(
    private val matrixClient: MatrixClient,
    private val clientUrlContentFetcher: ClientUrlContentFetcher,
    private val okHttpClient: OkHttpClient,
    private val dispatchers: CoroutineDispatchers,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** null — сервер не ответил или не делится присутствием; шапка тогда пишет «был(а) недавно». */
    suspend fun fetch(userId: UserId): DmPresence? {
        val token = matrixClient.getAccessToken().getOrNull() ?: return null
        val base = clientUrlContentFetcher.homeserverUrl.trimEnd('/')
        val encodedUserId = URLEncoder.encode(userId.value, Charsets.UTF_8.name())
        val request = Request.Builder()
            .url("$base/_matrix/client/v3/presence/$encodedUserId/status")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        return withContext(dispatchers.io) {
            runCatching {
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body.string()
                    val status = json.decodeFromString<PresenceStatusJson>(body)
                    val now = System.currentTimeMillis()
                    DmPresence(
                        isOnline = status.currentlyActive == true || status.presence == "online",
                        lastActiveAtMillis = status.lastActiveAgo?.let { now - it },
                    )
                }
            }.getOrElse {
                Timber.w(it, "Не получилось узнать presence $userId")
                null
            }
        }
    }
}

@Serializable
private data class PresenceStatusJson(
    @SerialName("presence") val presence: String? = null,
    @SerialName("last_active_ago") val lastActiveAgo: Long? = null,
    @SerialName("currently_active") val currentlyActive: Boolean? = null,
)
