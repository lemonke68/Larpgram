/*
 * Copyright (c) 2026 Larpgram.
 * Модуль форка: регистрация и сброс пароля из приложения (сервис server/account).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.accountapi.api

/**
 * Действия с аккаунтом вошедшего пользователя — то, для чего раньше открывалась страница MAS в
 * браузере: привязка нового устройства по QR, почта, завершение чужих сеансов. Запросы идут с
 * токеном текущей сессии.
 */
interface AccountSessionApi {
    /** Одноразовый код для QR: новое устройство сканирует его и входит в этот аккаунт. */
    suspend fun createLoginOffer(): LoginOfferResult

    /** Вошло ли новое устройство по коду. `null` — узнать не удалось (сеть). */
    suspend fun loginOfferState(code: String): LoginOfferState?

    /** Шлёт код на [email], чтобы привязать его к аккаунту. */
    suspend fun startEmailChange(email: String): StartResult

    /** Подтверждает почту кодом. [EmailChangeResult.Done] — адрес привязан. */
    suspend fun confirmEmailChange(ticket: String, code: String): EmailChangeResult

    /** Устройства аккаунта (сеансы). `null` — узнать не удалось. */
    suspend fun listDevices(): List<AccountDevice>?

    /** Почта аккаунта. [AccountEmail.Unknown] — узнать не удалось. */
    suspend fun currentEmail(): AccountEmail

    /** Завершает сеанс другого устройства. `true` при успехе. */
    suspend fun endSession(deviceId: String): Boolean
}

/** Сеанс аккаунта на одном устройстве. */
data class AccountDevice(
    val deviceId: String,
    val displayName: String?,
    /** Когда устройство последний раз было в сети, мс с эпохи; `null` — сервер не знает. */
    val lastSeenTimestamp: Long?,
    val isCurrent: Boolean,
)

sealed interface AccountEmail {
    data class Address(val email: String) : AccountEmail
    data object None : AccountEmail
    data object Unknown : AccountEmail
}

sealed interface LoginOfferResult {
    data class Offer(val code: String, val expiresInSeconds: Int) : LoginOfferResult
    data class Failure(val error: AccountError) : LoginOfferResult
}

enum class LoginOfferState {
    Waiting,
    Redeemed,
    Expired,
}

sealed interface EmailChangeResult {
    data class Done(val email: String) : EmailChangeResult
    data class WrongCode(val attemptsLeft: Int?) : EmailChangeResult
    data class Failure(val error: AccountError) : EmailChangeResult
}

/** Что зашито в QR-код привязки: схема + одноразовый код. */
object LoginQrCode {
    private const val PREFIX = "larpgram-login:"

    fun encode(code: String): String = PREFIX + code

    /** Код из отсканированного текста или `null`, если это не наш QR-код. */
    fun decode(text: String): String? = text.trim()
        .takeIf { it.startsWith(PREFIX) }
        ?.removePrefix(PREFIX)
        ?.takeIf { it.isNotBlank() }
}
