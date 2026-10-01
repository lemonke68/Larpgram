/*
 * Copyright (c) 2026 Larpgram.
 * Модуль форка: регистрация и сброс пароля из приложения (сервис server/account).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.accountapi.impl

import io.element.android.appconfig.LarpgramHosts
import io.element.android.libraries.accountapi.api.AccountError
import io.element.android.libraries.accountapi.api.StartResult
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.matrix.api.MatrixClient
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber

/**
 * Общая часть двух клиентов сервиса `server/account` (`push.mango-kokos.ru/account`): отправка
 * JSON и разбор ответа. Обычный OkHttp, а не SDK: это наш сервис, а не Matrix API. Пароль, код и
 * токен уходят только в теле и заголовке; в отладочном логе их вымарывает `FormattedJsonHttpLogger`.
 */
internal class AccountHttp(
    private val okHttpClient: OkHttpClient,
    private val coroutineDispatchers: CoroutineDispatchers,
) {
    val json = Json { ignoreUnknownKeys = true }

    /**
     * Шлёт [bodyJson] на IO-диспетчере, с [token] — как Bearer. `null` — запрос не дошёл (сеть,
     * таймаут). Тело ответа разбираем мягко: пустое или не-JSON даёт пустой [AnswerBody].
     */
    suspend fun post(path: String, bodyJson: String, token: String? = null): Answer? = withContext(coroutineDispatchers.io) {
        val request = Request.Builder()
            .url("$BASE_URL/$path")
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                val parsed = runCatching { json.decodeFromString<AnswerBody>(response.body.string()) }.getOrNull()
                Answer(response.code, parsed ?: AnswerBody())
            }
        }.getOrElse {
            Timber.w(it, "account: запрос $path не удался")
            null
        }
    }

    /** GET к Matrix-серверу сессии с её токеном. Тело ответа или `null` (нет токена, сеть, не 2xx). */
    suspend fun getFromHomeserver(matrixClient: MatrixClient, path: String): String? {
        val token = matrixClient.getAccessToken().getOrNull() ?: return null
        return withContext(coroutineDispatchers.io) {
            runCatching {
                val request = Request.Builder()
                    .url(matrixClient.homeserverUrl.trimEnd('/') + path)
                    .header("Authorization", "Bearer $token")
                    .build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) response.body.string() else null
                }
            }.getOrElse {
                Timber.w(it, "account: запрос $path не удался")
                null
            }
        }
    }

    suspend inline fun <reified T> post(path: String, body: T, token: String? = null): Answer? =
        post(path, json.encodeToString(body), token)

    private companion object {
        const val BASE_URL = LarpgramHosts.ACCOUNT_URL
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

internal class Answer(val code: Int, val body: AnswerBody) {
    /** Код ошибки сервиса → ошибка для экрана. Незнакомое и 5xx без тела — «нет связи». */
    fun error(): AccountError = when (body.error) {
        "username_too_short" -> AccountError.UsernameTooShort
        "username_too_long" -> AccountError.UsernameTooLong
        "username_invalid" -> AccountError.UsernameInvalid
        "username_taken" -> AccountError.UsernameTaken
        "email_invalid" -> AccountError.EmailInvalid
        "email_taken" -> AccountError.EmailTaken
        "password_too_short" -> AccountError.PasswordTooShort
        "password_too_long" -> AccountError.PasswordTooLong
        "password_weak" -> AccountError.PasswordWeak
        "code_expired" -> AccountError.CodeExpired
        "too_many_attempts" -> AccountError.TooManyAttempts
        "too_many_requests", ERROR_TOO_SOON -> AccountError.TooManyRequests
        "mail_failed" -> AccountError.MailFailed
        "invalid_credentials" -> AccountError.InvalidCredentials
        else -> AccountError.Network
    }
}

internal fun Answer?.toStartResult(): StartResult {
    this ?: return StartResult.Failure(AccountError.Network)
    val ticket = body.ticket
    return if (code == 200 && !ticket.isNullOrBlank()) {
        StartResult.Sent(ticket, body.resendAfter ?: DEFAULT_RESEND_SECONDS)
    } else {
        StartResult.Failure(error())
    }
}

internal const val DEFAULT_RESEND_SECONDS = 60
internal const val ERROR_WRONG_CODE = "wrong_code"
internal const val ERROR_TOO_SOON = "too_soon"

/** Все ответы сервиса в одном классе: полей мало, а набор зависит от запроса и кода ответа. */
@Serializable
internal data class AnswerBody(
    val error: String? = null,
    val ticket: String? = null,
    val username: String? = null,
    val email: String? = null,
    val code: String? = null,
    val state: String? = null,
    @SerialName("resend_after") val resendAfter: Int? = null,
    @SerialName("retry_after") val retryAfter: Int? = null,
    @SerialName("attempts_left") val attemptsLeft: Int? = null,
    @SerialName("expires_in") val expiresIn: Int? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("device_id") val deviceId: String? = null,
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("code_required") val codeRequired: Boolean? = null,
    @SerialName("email_hint") val emailHint: String? = null,
)

@Serializable
internal data class CodeRequest(val ticket: String, val code: String)
