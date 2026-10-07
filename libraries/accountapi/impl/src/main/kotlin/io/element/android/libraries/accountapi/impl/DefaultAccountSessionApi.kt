/*
 * Copyright (c) 2026 Larpgram.
 * Модуль форка: регистрация и сброс пароля из приложения (сервис server/account).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.accountapi.impl

import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.accountapi.api.AccountDevice
import io.element.android.libraries.accountapi.api.AccountEmail
import io.element.android.libraries.accountapi.api.AccountError
import io.element.android.libraries.accountapi.api.AccountSessionApi
import io.element.android.libraries.accountapi.api.EmailChangeResult
import io.element.android.libraries.accountapi.api.LoginOfferResult
import io.element.android.libraries.accountapi.api.LoginOfferState
import io.element.android.libraries.accountapi.api.StartResult
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.MatrixClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient

/**
 * Запросы вошедшего пользователя. Авторизация — Bearer с access-токеном Matrix, как у escrow:
 * сервис по нему через `whoami` понимает, чей это аккаунт.
 */
@ContributesBinding(SessionScope::class)
class DefaultAccountSessionApi(
    private val matrixClient: MatrixClient,
    okHttpClient: OkHttpClient,
    coroutineDispatchers: CoroutineDispatchers,
) : AccountSessionApi {
    private val http = AccountHttp(okHttpClient, coroutineDispatchers)

    override suspend fun createLoginOffer(): LoginOfferResult {
        val token = accessToken() ?: return LoginOfferResult.Failure(AccountError.Network)
        val answer = http.post("pair/offer", "{}", token) ?: return LoginOfferResult.Failure(AccountError.Network)
        val code = answer.body.code
        return if (answer.code == 200 && !code.isNullOrBlank()) {
            LoginOfferResult.Offer(code, answer.body.expiresIn ?: DEFAULT_OFFER_SECONDS)
        } else {
            LoginOfferResult.Failure(answer.error())
        }
    }

    override suspend fun loginOfferState(code: String): LoginOfferState? {
        val token = accessToken() ?: return null
        val answer = http.post("pair/status", OfferStatusRequest(code), token) ?: return null
        return when {
            answer.code == 404 -> LoginOfferState.Expired
            answer.code != 200 -> null
            answer.body.state == "redeemed" -> LoginOfferState.Redeemed
            answer.body.state == "expired" -> LoginOfferState.Expired
            answer.body.state == "waiting" -> LoginOfferState.Waiting
            else -> null
        }
    }

    override suspend fun startEmailChange(email: String): StartResult {
        val token = accessToken() ?: return StartResult.Failure(AccountError.Network)
        return http.post("email/start", EmailStartRequest(email), token).toStartResult()
    }

    override suspend fun confirmEmailChange(ticket: String, code: String): EmailChangeResult {
        val token = accessToken() ?: return EmailChangeResult.Failure(AccountError.Network)
        val answer = http.post("email/confirm", CodeRequest(ticket, code), token) ?: return EmailChangeResult.Failure(AccountError.Network)
        val email = answer.body.email
        return when {
            answer.code == 200 && !email.isNullOrBlank() -> EmailChangeResult.Done(email)
            answer.body.error == ERROR_WRONG_CODE -> EmailChangeResult.WrongCode(answer.body.attemptsLeft)
            else -> EmailChangeResult.Failure(answer.error())
        }
    }

    // Список устройств и почту отдаёт сам Matrix-сервер (CS API): в Rust SDK этих запросов нет,
    // поэтому обычный GET, как в accountemail.
    override suspend fun listDevices(): List<AccountDevice>? {
        val body = http.getFromHomeserver(matrixClient, "/_matrix/client/v3/devices") ?: return null
        val current = matrixClient.deviceId.value
        return runCatching {
            http.json.decodeFromString<DevicesResponse>(body).devices.map {
                AccountDevice(
                    deviceId = it.deviceId,
                    displayName = it.displayName?.takeIf { name -> name.isNotBlank() },
                    lastSeenTimestamp = it.lastSeenTs?.takeIf { ts -> ts > 0 },
                    isCurrent = it.deviceId == current,
                )
            }
        }.getOrNull()
    }

    override suspend fun currentEmail(): AccountEmail {
        val body = http.getFromHomeserver(matrixClient, "/_matrix/client/v3/account/3pid") ?: return AccountEmail.Unknown
        return runCatching {
            val address = http.json.decodeFromString<ThreePidsResponse>(body).threepids.firstOrNull { it.medium == "email" }?.address
            if (address.isNullOrBlank()) AccountEmail.None else AccountEmail.Address(address)
        }.getOrDefault(AccountEmail.Unknown)
    }

    override suspend fun endSession(deviceId: String): Boolean {
        val token = accessToken() ?: return false
        return http.post("sessions/end", EndSessionRequest(deviceId), token)?.code == 204
    }

    override suspend fun checkPassword(password: String): Boolean? {
        val token = accessToken() ?: return null
        return when (http.post("password/check", PasswordCheckRequest(password), token)?.code) {
            204 -> true
            403 -> false
            else -> null
        }
    }

    private suspend fun accessToken(): String? = matrixClient.getAccessToken().getOrNull()

    private companion object {
        const val DEFAULT_OFFER_SECONDS = 120
    }
}

@Serializable
private data class OfferStatusRequest(val code: String)

@Serializable
private data class EmailStartRequest(val email: String)

@Serializable
private data class PasswordCheckRequest(val password: String)

@Serializable
private data class EndSessionRequest(@SerialName("device_id") val deviceId: String)

@Serializable
private data class DevicesResponse(val devices: List<DeviceJson> = emptyList())

@Serializable
private data class DeviceJson(
    @SerialName("device_id") val deviceId: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("last_seen_ts") val lastSeenTs: Long? = null,
)

@Serializable
private data class ThreePidsResponse(val threepids: List<ThreePidJson> = emptyList())

@Serializable
private data class ThreePidJson(val medium: String = "", val address: String = "")
