/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.tg

import androidx.compose.ui.tooling.preview.PreviewParameterProvider

open class TgAuthStatePreviewParam : PreviewParameterProvider<TgAuthState> {
    override val values: Sequence<TgAuthState>
        get() = sequenceOf(
            aTgAuthState(step = TgAuthStep.Welcome),
            aTgAuthState(step = TgAuthStep.Login),
            aTgAuthState(
                step = TgAuthStep.Login,
                login = "vasya",
                password = "password",
                error = TgAuthError(TgAuthError.Kind.InvalidCredentials),
            ),
            aTgAuthState(step = TgAuthStep.Login, login = "vasya", password = "password", isBusy = true, isAddingAccount = true),
            aTgAuthState(step = TgAuthStep.Register),
            aTgAuthState(
                step = TgAuthStep.Register,
                registerUsername = "vasya",
                registerEmail = "vasya@example.com",
                registerPassword = "password",
                registerPasswordRepeat = "password",
                error = TgAuthError(TgAuthError.Kind.UsernameTaken),
            ),
            aTgAuthState(step = TgAuthStep.RegisterCode, registerEmail = "vasya@example.com", code = "123", resendAfterSeconds = 42),
            aTgAuthState(step = TgAuthStep.RegisterCode, registerEmail = "vasya@example.com", error = TgAuthError(TgAuthError.Kind.WrongCode, 3)),
            aTgAuthState(step = TgAuthStep.Forgot, forgotLogin = "vasya"),
            aTgAuthState(step = TgAuthStep.ForgotCode),
            aTgAuthState(step = TgAuthStep.NewPassword, error = TgAuthError(TgAuthError.Kind.PasswordsDiffer)),
        )
}

fun aTgAuthState(
    step: TgAuthStep = TgAuthStep.Welcome,
    isAddingAccount: Boolean = false,
    showDeveloperSettings: Boolean = false,
    canReportBug: Boolean = false,
    version: String = "0.3.8",
    login: String = "",
    password: String = "",
    registerUsername: String = "",
    registerEmail: String = "",
    registerPassword: String = "",
    registerPasswordRepeat: String = "",
    forgotLogin: String = "",
    code: String = "",
    loginEmailHint: String = "",
    resendAfterSeconds: Int = 0,
    codeSentCount: Int = 0,
    newPassword: String = "",
    newPasswordRepeat: String = "",
    isBusy: Boolean = false,
    error: TgAuthError? = null,
    eventSink: (TgAuthEvent) -> Unit = {},
) = TgAuthState(
    step = step,
    isAddingAccount = isAddingAccount,
    showDeveloperSettings = showDeveloperSettings,
    canReportBug = canReportBug,
    version = version,
    logoResId = null,
    applicationName = "Larpgram",
    login = login,
    password = password,
    registerUsername = registerUsername,
    registerEmail = registerEmail,
    registerPassword = registerPassword,
    registerPasswordRepeat = registerPasswordRepeat,
    forgotLogin = forgotLogin,
    code = code,
    loginEmailHint = loginEmailHint,
    resendAfterSeconds = resendAfterSeconds,
    codeSentCount = codeSentCount,
    newPassword = newPassword,
    newPasswordRepeat = newPasswordRepeat,
    isBusy = isBusy,
    error = error,
    eventSink = eventSink,
)
