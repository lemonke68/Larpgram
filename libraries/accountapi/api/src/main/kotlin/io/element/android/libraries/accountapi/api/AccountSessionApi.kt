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
    /**
     * Одноразовый код для QR: новое устройство сканирует его и входит в этот аккаунт.
     * [keyCiphertext] — зашифрованный ключ восстановления, сервер отдаст его новому устройству.
     */
    suspend fun createLoginOffer(keyCiphertext: String? = null): LoginOfferResult

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

    /**
     * Верен ли пароль аккаунта: перед тем как запереть им ключ восстановления (escrow вариант B).
     * `null` — узнать не удалось (сеть, лимит запросов).
     */
    suspend fun checkPassword(password: String): Boolean?
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

/** Содержимое QR-кода привязки: одноразовый код и, если есть, ключ K к ключу восстановления. */
data class LoginQrPayload(
    val code: String,
    val key: String? = null,
)

/**
 * Что зашито в QR-код привязки: `larpgram-login:<код>` или `larpgram-login:<код>#<K>`, K — в
 * base64url. Старый формат без K по-прежнему читается.
 */
object LoginQrCode {
    private const val PREFIX = "larpgram-login:"
    private const val KEY_SEPARATOR = '#'
    private val KEY_CHARS = Regex("[A-Za-z0-9_-]+")

    fun encode(code: String, key: String? = null): String =
        if (key.isNullOrEmpty()) PREFIX + code else PREFIX + code + KEY_SEPARATOR + key

    /** Код и K из отсканированного текста или `null`, если это не наш QR-код. Кривой K отбрасываем. */
    fun decode(text: String): LoginQrPayload? {
        val body = text.trim().takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX) ?: return null
        val code = body.substringBefore(KEY_SEPARATOR).takeIf { it.isNotBlank() } ?: return null
        val key = body.substringAfter(KEY_SEPARATOR, missingDelimiterValue = "").takeIf { KEY_CHARS.matches(it) }
        return LoginQrPayload(code, key)
    }
}
