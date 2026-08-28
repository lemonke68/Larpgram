/*
 * Модуль форка: депонирование ключа восстановления (вход по коду с почты).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.keyescrow.impl

import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.keyescrow.api.KeyEscrowService
import io.element.android.libraries.keyescrow.api.RedeemResult
import io.element.android.libraries.keyescrow.api.RequestCodeResult
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import timber.log.Timber

/**
 * Ходит в escrow-сервис за депонированным ключом восстановления.
 *
 * Хост фиксированный (`push.mango-kokos.ru/escrow`, бэкенд-домен рядом с sygnal, а не
 * публичная витрина `larpgram.mango-kokos.ru`). Авторизация — Bearer с access-токеном
 * Matrix: он есть даже у ещё не верифицированной сессии, а сервер по нему через Synapse
 * `whoami` понимает, чей это аккаунт, и сам берёт почту из /account/3pid. Токен в теле или
 * URL не светим, только в заголовке.
 *
 * Своего клиента к SDK тут нет: escrow это наш сервис, а не Matrix API, поэтому обычный
 * OkHttp, как в appupdate и accountemail.
 */
@ContributesBinding(SessionScope::class)
class DefaultKeyEscrowService(
    private val matrixClient: MatrixClient,
    private val okHttpClient: OkHttpClient,
    private val coroutineDispatchers: CoroutineDispatchers,
) : KeyEscrowService {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun hasStoredKey(): Boolean? {
        val token = accessToken() ?: return null
        val request = Request.Builder()
            .url("$BASE_URL/key")
            .header(HEADER_AUTH, "Bearer $token")
            .get()
            .build()
        return execute(request) { response ->
            when (response.code) {
                200 -> true
                404 -> false
                // Любой другой ответ (401, 5xx) — не «ключа нет», а «непонятно»: возвращаем
                // null, чтобы бэкфилл не сбросил ключ на ровном месте.
                else -> null
            }
        }
    }

    override suspend fun store(recoveryKey: String): Result<Unit> {
        val token = accessToken() ?: return Result.failure(IllegalStateException("нет access-токена"))
        val body = json.encodeToString(StoreRequest(recoveryKey))
            .toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url("$BASE_URL/key")
            .header(HEADER_AUTH, "Bearer $token")
            .put(body)
            .build()
        val ok = execute(request) { it.isSuccessful } ?: false
        return if (ok) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("escrow не принял ключ"))
        }
    }

    override suspend fun requestCode(): RequestCodeResult {
        val token = accessToken() ?: return RequestCodeResult.NetworkError
        val request = Request.Builder()
            .url("$BASE_URL/code")
            .header(HEADER_AUTH, "Bearer $token")
            .post(EMPTY_BODY)
            .build()
        return execute(request) { response ->
            when (response.code) {
                200 -> {
                    val masked = response.body.string()
                        .let { runCatching { json.decodeFromString<CodeSentResponse>(it).maskedEmail }.getOrNull() }
                        .orEmpty()
                    RequestCodeResult.Sent(masked)
                }
                404 -> RequestCodeResult.NoEmail
                429 -> RequestCodeResult.RateLimited
                else -> RequestCodeResult.NetworkError
            }
        } ?: RequestCodeResult.NetworkError
    }

    override suspend fun redeemCode(code: String): RedeemResult {
        val token = accessToken() ?: return RedeemResult.NetworkError
        val body = json.encodeToString(RedeemRequest(code)).toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url("$BASE_URL/key/redeem")
            .header(HEADER_AUTH, "Bearer $token")
            .post(body)
            .build()
        return execute(request) { response ->
            when (response.code) {
                200 -> {
                    val key = response.body.string()
                        .let { runCatching { json.decodeFromString<RecoveryKeyResponse>(it).recoveryKey }.getOrNull() }
                    if (key.isNullOrBlank()) RedeemResult.NetworkError else RedeemResult.Success(key)
                }
                400 -> {
                    val left = response.body.string()
                        .let { runCatching { json.decodeFromString<InvalidCodeResponse>(it).attemptsLeft }.getOrNull() }
                    RedeemResult.InvalidCode(attemptsLeft = left)
                }
                404 -> RedeemResult.NoStoredKey
                410 -> RedeemResult.Expired
                429 -> RedeemResult.TooManyAttempts
                else -> RedeemResult.NetworkError
            }
        } ?: RedeemResult.NetworkError
    }

    override suspend fun deleteDmForBoth(roomId: RoomId): Boolean {
        val token = accessToken() ?: return false
        val body = json.encodeToString(DeleteRoomRequest(roomId.value)).toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url("$BASE_URL/room/delete")
            .header(HEADER_AUTH, "Bearer $token")
            .post(body)
            .build()
        // 202 — сервер принял задачу удаления. Любой другой код (401/403/409/5xx) = не вышло.
        return execute(request) { it.code == 202 } ?: false
    }

    private suspend fun accessToken(): String? = matrixClient.getAccessToken().getOrNull()

    /**
     * Выполняет запрос на IO-диспетчере и мапит ответ. Возвращает `null`, если запрос упал
     * (сеть, таймаут): вызывающий сам решает, что это значит для его результата.
     */
    private suspend fun <T> execute(request: Request, map: (Response) -> T): T? =
        withContext(coroutineDispatchers.io) {
            runCatching {
                okHttpClient.newCall(request).execute().use(map)
            }.getOrElse {
                Timber.w(it, "escrow: запрос ${request.url} не удался")
                null
            }
        }

    private companion object {
        const val BASE_URL = "https://push.mango-kokos.ru/escrow"
        const val HEADER_AUTH = "Authorization"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val EMPTY_BODY = ByteArray(0).toRequestBody(null)
    }
}

@Serializable
private data class StoreRequest(
    @SerialName("recovery_key") val recoveryKey: String,
)

@Serializable
private data class DeleteRoomRequest(
    @SerialName("room_id") val roomId: String,
)

@Serializable
private data class RedeemRequest(
    @SerialName("code") val code: String,
)

@Serializable
private data class CodeSentResponse(
    @SerialName("masked_email") val maskedEmail: String = "",
)

@Serializable
private data class RecoveryKeyResponse(
    @SerialName("recovery_key") val recoveryKey: String = "",
)

@Serializable
private data class InvalidCodeResponse(
    @SerialName("attempts_left") val attemptsLeft: Int? = null,
)
