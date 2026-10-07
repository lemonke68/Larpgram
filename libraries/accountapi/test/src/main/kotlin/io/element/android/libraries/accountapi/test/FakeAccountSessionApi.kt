/*
 * Copyright (c) 2026 Larpgram.
 * Модуль форка: регистрация и сброс пароля из приложения (сервис server/account).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.accountapi.test

import io.element.android.libraries.accountapi.api.AccountDevice
import io.element.android.libraries.accountapi.api.AccountEmail
import io.element.android.libraries.accountapi.api.AccountError
import io.element.android.libraries.accountapi.api.AccountSessionApi
import io.element.android.libraries.accountapi.api.EmailChangeResult
import io.element.android.libraries.accountapi.api.LoginOfferResult
import io.element.android.libraries.accountapi.api.LoginOfferState
import io.element.android.libraries.accountapi.api.StartResult

class FakeAccountSessionApi(
    private val createLoginOfferLambda: () -> LoginOfferResult = { LoginOfferResult.Failure(AccountError.Network) },
    private val loginOfferStateLambda: (String) -> LoginOfferState? = { null },
    private val startEmailChangeLambda: (String) -> StartResult = { StartResult.Failure(AccountError.Network) },
    private val confirmEmailChangeLambda: (String, String) -> EmailChangeResult = { _, _ -> EmailChangeResult.Failure(AccountError.Network) },
    private val endSessionLambda: (String) -> Boolean = { false },
    private val listDevicesLambda: () -> List<AccountDevice>? = { null },
    private val currentEmailLambda: () -> AccountEmail = { AccountEmail.Unknown },
    private val checkPasswordLambda: (String) -> Boolean? = { null },
) : AccountSessionApi {
    override suspend fun createLoginOffer() = createLoginOfferLambda()

    override suspend fun loginOfferState(code: String) = loginOfferStateLambda(code)

    override suspend fun startEmailChange(email: String) = startEmailChangeLambda(email)

    override suspend fun confirmEmailChange(ticket: String, code: String) = confirmEmailChangeLambda(ticket, code)

    override suspend fun endSession(deviceId: String) = endSessionLambda(deviceId)

    override suspend fun checkPassword(password: String) = checkPasswordLambda(password)

    override suspend fun listDevices() = listDevicesLambda()

    override suspend fun currentEmail() = currentEmailLambda()
}
