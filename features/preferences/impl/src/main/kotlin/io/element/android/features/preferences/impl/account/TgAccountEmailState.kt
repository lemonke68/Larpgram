/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.account

import io.element.android.libraries.accountapi.api.AccountEmail
import io.element.android.libraries.accountapi.api.AccountError

data class TgAccountEmailState(
    val step: Step,
    /** Почта аккаунта сейчас; `null` — ещё узнаём. */
    val currentEmail: AccountEmail?,
    val email: String,
    val code: String,
    val resendAfterSeconds: Int,
    val codeSentCount: Int,
    val isBusy: Boolean,
    val error: TgAccountError?,
    val eventSink: (TgAccountEmailEvent) -> Unit,
) {
    enum class Step { Enter, Code, Done }

    val canSubmit get() = !isBusy && email.isNotBlank()
}

/** Ошибка шага. [attemptsLeft] есть только у неверного кода. */
data class TgAccountError(
    val kind: Kind,
    val attemptsLeft: Int? = null,
) {
    enum class Kind {
        EmailInvalid,
        EmailTaken,
        MailFailed,
        WrongCode,
        CodeExpired,
        TooManyAttempts,
        TooManyRequests,
        PasswordTooShort,
        PasswordTooLong,
        PasswordWeak,
        PasswordsDiffer,
        Network,
    }
}

internal fun AccountError.toAccountErrorKind(): TgAccountError.Kind = when (this) {
    AccountError.EmailInvalid -> TgAccountError.Kind.EmailInvalid
    AccountError.EmailTaken -> TgAccountError.Kind.EmailTaken
    AccountError.MailFailed -> TgAccountError.Kind.MailFailed
    AccountError.CodeExpired -> TgAccountError.Kind.CodeExpired
    AccountError.TooManyAttempts -> TgAccountError.Kind.TooManyAttempts
    AccountError.TooManyRequests -> TgAccountError.Kind.TooManyRequests
    AccountError.PasswordTooShort -> TgAccountError.Kind.PasswordTooShort
    AccountError.PasswordTooLong -> TgAccountError.Kind.PasswordTooLong
    AccountError.PasswordWeak -> TgAccountError.Kind.PasswordWeak
    AccountError.UsernameTooShort,
    AccountError.UsernameTooLong,
    AccountError.UsernameInvalid,
    AccountError.UsernameTaken,
    AccountError.InvalidCredentials,
    AccountError.Network -> TgAccountError.Kind.Network
}

sealed interface TgAccountEmailEvent {
    data class SetEmail(val value: String) : TgAccountEmailEvent
    data object Submit : TgAccountEmailEvent
    data class SetCode(val value: String) : TgAccountEmailEvent
    data object ResendCode : TgAccountEmailEvent

    /** Шаг назад внутри экрана (с кода — к вводу адреса). */
    data object Back : TgAccountEmailEvent
}
