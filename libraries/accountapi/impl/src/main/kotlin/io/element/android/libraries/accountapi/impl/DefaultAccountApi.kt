/*
 * Copyright (c) 2026 Larpgram.
 * Модуль форка: регистрация и сброс пароля из приложения (сервис server/account).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.accountapi.impl

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.accountapi.api.AccountApi
import io.element.android.libraries.accountapi.api.AccountError
import io.element.android.libraries.accountapi.api.CheckResult
import io.element.android.libraries.accountapi.api.ConfirmResult
import io.element.android.libraries.accountapi.api.LoginStartResult
import io.element.android.libraries.accountapi.api.RedeemLoginResult
import io.element.android.libraries.accountapi.api.ResendResult
import io.element.android.libraries.accountapi.api.StartResult
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient

/**
 * Запросы без токена: сессии ещё нет, поэтому клиент живёт в AppScope, а не в SessionScope, как
 * escrow.
 */
@ContributesBinding(AppScope::class)
class DefaultAccountApi(
    okHttpClient: OkHttpClient,
    coroutineDispatchers: CoroutineDispatchers,
) : AccountApi {
    private val http = AccountHttp(okHttpClient, coroutineDispatchers)

    override suspend fun startRegistration(username: String, email: String): StartResult =
        http.post("register/start", RegisterStartRequest(username, email)).toStartResult()

    override suspend fun confirmRegistration(ticket: String, code: String, password: String): ConfirmResult =
        http.post("register/confirm", ConfirmRequest(ticket, code, password)).toConfirmResult()

    override suspend fun forgotPassword(login: String): StartResult =
        http.post("password/forgot", ForgotRequest(login)).toStartResult()

    override suspend fun checkCode(ticket: String, code: String): CheckResult {
        val answer = http.post("code/check", CodeRequest(ticket, code)) ?: return CheckResult.Failure(AccountError.Network)
        return when {
            answer.code in 200..299 -> CheckResult.Ok
            answer.body.error == ERROR_WRONG_CODE -> CheckResult.WrongCode(answer.body.attemptsLeft)
            else -> CheckResult.Failure(answer.error())
        }
    }

    override suspend fun resetPassword(ticket: String, code: String, password: String): ConfirmResult =
        http.post("password/reset", ConfirmRequest(ticket, code, password)).toConfirmResult()

    override suspend fun resendCode(ticket: String): ResendResult {
        val answer = http.post("code/resend", ResendRequest(ticket)) ?: return ResendResult.Failure(AccountError.Network)
        return when {
            answer.code == 200 -> ResendResult.Sent(answer.body.resendAfter ?: DEFAULT_RESEND_SECONDS)
            answer.body.error == ERROR_TOO_SOON -> ResendResult.TooSoon(answer.body.retryAfter ?: DEFAULT_RESEND_SECONDS)
            else -> ResendResult.Failure(answer.error())
        }
    }

    override suspend fun usernameByEmail(email: String): String? {
        val answer = http.post("login/resolve", ResolveLoginRequest(email)) ?: return null
        return answer.body.username?.takeIf { answer.code == 200 && it.isNotBlank() }
    }

    override suspend fun startLogin(login: String, password: String): LoginStartResult {
        val answer = http.post("login/start", LoginStartRequest(login, password)) ?: return LoginStartResult.Failure(AccountError.Network)
        val body = answer.body
        val username = body.username
        return when {
            answer.code != 200 || username.isNullOrBlank() -> LoginStartResult.Failure(answer.error())
            body.codeRequired == false -> LoginStartResult.NoCodeNeeded(username)
            !body.ticket.isNullOrBlank() -> LoginStartResult.CodeSent(
                ticket = body.ticket,
                resendAfterSeconds = body.resendAfter ?: DEFAULT_RESEND_SECONDS,
                username = username,
                emailHint = body.emailHint.orEmpty(),
            )
            else -> LoginStartResult.Failure(AccountError.Network)
        }
    }

    override suspend fun confirmLogin(ticket: String, code: String): ConfirmResult =
        http.post("login/confirm", CodeRequest(ticket, code)).toConfirmResult()

    override suspend fun redeemLoginCode(code: String, deviceName: String): RedeemLoginResult {
        val answer = http.post("pair/redeem", RedeemLoginRequest(code, deviceName))
            ?: return RedeemLoginResult.Failure(AccountError.Network)
        val body = answer.body
        return if (answer.code == 200 && !body.userId.isNullOrBlank() && !body.deviceId.isNullOrBlank() && !body.accessToken.isNullOrBlank()) {
            RedeemLoginResult.Session(body.userId, body.deviceId, body.accessToken)
        } else {
            RedeemLoginResult.Failure(answer.error())
        }
    }

    private fun Answer?.toConfirmResult(): ConfirmResult {
        this ?: return ConfirmResult.Failure(AccountError.Network)
        val username = body.username
        return when {
            code == 200 && !username.isNullOrBlank() -> ConfirmResult.Done(username)
            body.error == ERROR_WRONG_CODE -> ConfirmResult.WrongCode(body.attemptsLeft)
            else -> ConfirmResult.Failure(error())
        }
    }
}

@Serializable
private data class RegisterStartRequest(val username: String, val email: String)

@Serializable
private data class ConfirmRequest(val ticket: String, val code: String, val password: String)

@Serializable
private data class LoginStartRequest(val login: String, val password: String)

@Serializable
private data class ForgotRequest(val login: String)

@Serializable
private data class ResendRequest(val ticket: String)

@Serializable
private data class RedeemLoginRequest(val code: String, @SerialName("device_name") val deviceName: String)

@Serializable
private data class ResolveLoginRequest(val email: String)
