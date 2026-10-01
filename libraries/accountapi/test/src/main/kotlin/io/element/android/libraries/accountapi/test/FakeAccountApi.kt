/*
 * Copyright (c) 2026 Larpgram.
 * Модуль форка: регистрация и сброс пароля из приложения (сервис server/account).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.accountapi.test

import io.element.android.libraries.accountapi.api.AccountApi
import io.element.android.libraries.accountapi.api.AccountError
import io.element.android.libraries.accountapi.api.CheckResult
import io.element.android.libraries.accountapi.api.ConfirmResult
import io.element.android.libraries.accountapi.api.LoginStartResult
import io.element.android.libraries.accountapi.api.RedeemLoginResult
import io.element.android.libraries.accountapi.api.ResendResult
import io.element.android.libraries.accountapi.api.StartResult

class FakeAccountApi(
    private val startRegistrationLambda: (String, String) -> StartResult = { _, _ -> StartResult.Failure(AccountError.Network) },
    private val confirmRegistrationLambda: (String, String, String) -> ConfirmResult = { _, _, _ -> ConfirmResult.Failure(AccountError.Network) },
    private val forgotPasswordLambda: (String) -> StartResult = { StartResult.Failure(AccountError.Network) },
    private val checkCodeLambda: (String, String) -> CheckResult = { _, _ -> CheckResult.Failure(AccountError.Network) },
    private val resetPasswordLambda: (String, String, String) -> ConfirmResult = { _, _, _ -> ConfirmResult.Failure(AccountError.Network) },
    private val resendCodeLambda: (String) -> ResendResult = { ResendResult.Failure(AccountError.Network) },
    private val usernameByEmailLambda: (String) -> String? = { null },
    private val redeemLoginCodeLambda: (String, String) -> RedeemLoginResult = { _, _ -> RedeemLoginResult.Failure(AccountError.Network) },
    private val startLoginLambda: (String, String) -> LoginStartResult = { _, _ -> LoginStartResult.Failure(AccountError.Network) },
    private val confirmLoginLambda: (String, String) -> ConfirmResult = { _, _ -> ConfirmResult.Failure(AccountError.Network) },
) : AccountApi {
    override suspend fun startRegistration(username: String, email: String) = startRegistrationLambda(username, email)

    override suspend fun confirmRegistration(ticket: String, code: String, password: String) =
        confirmRegistrationLambda(ticket, code, password)

    override suspend fun forgotPassword(login: String) = forgotPasswordLambda(login)

    override suspend fun checkCode(ticket: String, code: String) = checkCodeLambda(ticket, code)

    override suspend fun resetPassword(ticket: String, code: String, password: String) = resetPasswordLambda(ticket, code, password)

    override suspend fun resendCode(ticket: String) = resendCodeLambda(ticket)

    override suspend fun usernameByEmail(email: String) = usernameByEmailLambda(email)

    override suspend fun redeemLoginCode(code: String, deviceName: String) = redeemLoginCodeLambda(code, deviceName)

    override suspend fun startLogin(login: String, password: String) = startLoginLambda(login, password)

    override suspend fun confirmLogin(ticket: String, code: String) = confirmLoginLambda(ticket, code)
}
