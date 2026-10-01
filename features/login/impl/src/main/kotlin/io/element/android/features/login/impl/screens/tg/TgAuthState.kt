/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.tg

import androidx.annotation.DrawableRes

data class TgAuthState(
    val step: TgAuthStep,
    /** Есть ли уже сессии: тогда это «добавить аккаунт», приветствия нет, а «назад» закрывает. */
    val isAddingAccount: Boolean,
    val showDeveloperSettings: Boolean,
    val canReportBug: Boolean,
    val version: String,
    @DrawableRes val logoResId: Int?,
    val applicationName: String,
    val login: String,
    val password: String,
    val registerUsername: String,
    val registerEmail: String,
    val registerPassword: String,
    val registerPasswordRepeat: String,
    val forgotLogin: String,
    val code: String,
    /** Почта со скрытой серединой, куда ушёл код входа. */
    val loginEmailHint: String,
    /** Через сколько секунд можно просить код ещё раз, считая от [codeSentCount]-й отправки. */
    val resendAfterSeconds: Int,
    /** Растёт с каждой отправкой кода: экран по нему перезапускает обратный отсчёт. */
    val codeSentCount: Int,
    val newPassword: String,
    val newPasswordRepeat: String,
    val isBusy: Boolean,
    val error: TgAuthError?,
    val eventSink: (TgAuthEvent) -> Unit,
) {
    val canSubmitLogin get() = !isBusy && login.isNotBlank() && password.isNotEmpty()
    val canSubmitRegister
        get() = !isBusy && registerUsername.isNotBlank() && registerEmail.isNotBlank() &&
            registerPassword.isNotEmpty() && registerPasswordRepeat.isNotEmpty()
    val canSubmitForgot get() = !isBusy && forgotLogin.isNotBlank()
    val canSubmitNewPassword get() = !isBusy && newPassword.isNotEmpty() && newPasswordRepeat.isNotEmpty()
}

enum class TgAuthStep {
    /** Ещё не знаем, первый это аккаунт или добавочный: экран пуст, чтобы приветствие не мигало. */
    Loading,
    Welcome,
    Login,

    /** Пароль принят, ждём код с почты аккаунта. */
    LoginCode,
    Register,
    RegisterCode,
    Forgot,
    ForgotCode,
    NewPassword,

    /** Камера: сканируем QR-код с устройства, где уже вошли. */
    ScanQr,
}

/** Ошибка шага. [attemptsLeft] есть только у [Kind.WrongCode]. */
data class TgAuthError(
    val kind: Kind,
    val attemptsLeft: Int? = null,
) {
    enum class Kind {
        InvalidCredentials,
        AccountDeactivated,

        /** В этот аккаунт на устройстве уже вошли (второй аккаунт). */
        AlreadyLoggedIn,
        UsernameTooShort,
        UsernameTooLong,
        UsernameInvalid,
        UsernameTaken,
        EmailInvalid,
        EmailTaken,
        PasswordTooShort,
        PasswordTooLong,
        PasswordWeak,
        PasswordsDiffer,
        WrongCode,
        CodeExpired,
        TooManyAttempts,
        TooManyRequests,
        MailFailed,

        /** Отсканирован не наш QR-код. */
        QrInvalid,

        /** QR-код устарел или уже использован. */
        QrExpired,
        Network,
    }
}
