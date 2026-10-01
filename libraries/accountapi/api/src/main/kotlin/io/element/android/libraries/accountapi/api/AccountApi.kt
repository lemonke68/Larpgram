/*
 * Copyright (c) 2026 Larpgram.
 * Модуль форка: регистрация и сброс пароля из приложения (сервис server/account).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.accountapi.api

/**
 * Регистрация и сброс пароля без браузера. Аккаунта у человека в этот момент ещё нет (или пароль
 * забыт), поэтому запросы идут без токена; личность подтверждает код с почты.
 *
 * Сам вход после регистрации или сброса делает обычный Matrix-логин по нику и паролю.
 */
interface AccountApi {
    /** Проверяет ник и почту и шлёт код на почту. */
    suspend fun startRegistration(username: String, email: String): StartResult

    /** Создаёт аккаунт. После [ConfirmResult.Done] можно входить по нику и паролю. */
    suspend fun confirmRegistration(ticket: String, code: String, password: String): ConfirmResult

    /**
     * Начинает сброс пароля. [login] — ник или почта. Сервер отвечает [StartResult.Sent] и для
     * аккаунта без почты, и для несуществующего: письмо в этих случаях просто не уходит.
     */
    suspend fun forgotPassword(login: String): StartResult

    /** Проверяет код, ничего не меняя: экран нового пароля показываем после верного кода. */
    suspend fun checkCode(ticket: String, code: String): CheckResult

    /** Ставит новый пароль. */
    suspend fun resetPassword(ticket: String, code: String, password: String): ConfirmResult

    /** Присылает код ещё раз по той же заявке. */
    suspend fun resendCode(ticket: String): ResendResult

    /**
     * Ник аккаунта по его почте — для входа по почте: сервер принимает только ник.
     * `null` — такой почты нет или узнать не удалось (для входа это одно и то же: «неверные данные»
     * либо «нет связи» покажет сам вход).
     */
    suspend fun usernameByEmail(email: String): String?

    /**
     * Меняет код из QR-кода вошедшего устройства на сессию того же аккаунта.
     * [deviceName] попадёт в список сеансов.
     */
    suspend fun redeemLoginCode(code: String, deviceName: String): RedeemLoginResult
}

sealed interface RedeemLoginResult {
    data class Session(val userId: String, val deviceId: String, val accessToken: String) : RedeemLoginResult
    data class Failure(val error: AccountError) : RedeemLoginResult
}

sealed interface StartResult {
    /** Код отправлен. [ticket] нужен для следующих шагов. */
    data class Sent(val ticket: String, val resendAfterSeconds: Int) : StartResult
    data class Failure(val error: AccountError) : StartResult
}

sealed interface ConfirmResult {
    data class Done(val username: String) : ConfirmResult
    data class WrongCode(val attemptsLeft: Int?) : ConfirmResult
    data class Failure(val error: AccountError) : ConfirmResult
}

sealed interface CheckResult {
    data object Ok : CheckResult
    data class WrongCode(val attemptsLeft: Int?) : CheckResult
    data class Failure(val error: AccountError) : CheckResult
}

sealed interface ResendResult {
    data class Sent(val resendAfterSeconds: Int) : ResendResult
    data class TooSoon(val retryAfterSeconds: Int) : ResendResult
    data class Failure(val error: AccountError) : ResendResult
}

enum class AccountError {
    UsernameTooShort,
    UsernameTooLong,
    UsernameInvalid,
    UsernameTaken,
    EmailInvalid,
    EmailTaken,
    PasswordTooShort,
    PasswordTooLong,
    PasswordWeak,

    /** Код или заявка устарели: начинать заново. */
    CodeExpired,

    /** Код введён неверно слишком много раз: заявка сгорела. */
    TooManyAttempts,

    /** Слишком много запросов с этого адреса или на эту почту. */
    TooManyRequests,

    /** Сервер не смог отправить письмо. */
    MailFailed,

    /** Нет сети, сервер недоступен или ответил непонятно. */
    Network,
}
