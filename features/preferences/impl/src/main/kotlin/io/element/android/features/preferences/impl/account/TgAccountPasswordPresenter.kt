/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.account

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.features.preferences.impl.account.TgAccountError.Kind
import io.element.android.features.preferences.impl.account.TgAccountPasswordState.Step
import io.element.android.libraries.accountapi.api.AccountApi
import io.element.android.libraries.accountapi.api.AccountEmail
import io.element.android.libraries.accountapi.api.AccountError
import io.element.android.libraries.accountapi.api.AccountSessionApi
import io.element.android.libraries.accountapi.api.CheckResult
import io.element.android.libraries.accountapi.api.ConfirmResult
import io.element.android.libraries.accountapi.api.ResendResult
import io.element.android.libraries.accountapi.api.StartResult
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.keyescrow.api.RecoveryKeyAutoProvisioner
import io.element.android.libraries.matrix.api.MatrixClient
import kotlinx.coroutines.launch

/**
 * Смена пароля из настроек. Старый пароль не спрашиваем: подтверждение — код на почту аккаунта,
 * те же ручки сервиса, что у «Забыли пароль?» на экране входа. Без почты сменить пароль нельзя —
 * экран отправляет её привязать.
 */
@Inject
class TgAccountPasswordPresenter(
    private val matrixClient: MatrixClient,
    private val accountSessionApi: AccountSessionApi,
    private val accountApi: AccountApi,
    private val recoveryKeyAutoProvisioner: RecoveryKeyAutoProvisioner,
) : Presenter<TgAccountPasswordState> {
    @Composable
    override fun present(): TgAccountPasswordState {
        val scope = rememberCoroutineScope()
        var step by rememberSaveable { mutableStateOf(Step.Intro) }
        var email by remember { mutableStateOf<AccountEmail?>(null) }
        var code by rememberSaveable { mutableStateOf("") }
        var ticket by rememberSaveable { mutableStateOf("") }
        var resendAfterSeconds by rememberSaveable { mutableIntStateOf(0) }
        var codeSentCount by rememberSaveable { mutableIntStateOf(0) }
        var password by rememberSaveable { mutableStateOf("") }
        var passwordRepeat by rememberSaveable { mutableStateOf("") }
        var isBusy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<TgAccountError?>(null) }

        LaunchedEffect(Unit) { email = accountSessionApi.currentEmail() }

        fun fail(kind: Kind, attemptsLeft: Int? = null) {
            error = TgAccountError(kind, attemptsLeft)
            isBusy = false
        }

        fun codeSent(resendAfter: Int) {
            code = ""
            resendAfterSeconds = resendAfter
            codeSentCount++
        }

        fun sendCode() = scope.launch {
            isBusy = true
            error = null
            // Ник из Matrix ID: @nick:server.
            val username = matrixClient.sessionId.value.removePrefix("@").substringBefore(':')
            when (val result = accountApi.forgotPassword(username)) {
                is StartResult.Sent -> {
                    ticket = result.ticket
                    codeSent(result.resendAfterSeconds)
                    step = Step.Code
                    isBusy = false
                }
                is StartResult.Failure -> fail(result.error.toAccountErrorKind())
            }
        }

        fun expiredOr(errorCode: AccountError) {
            if (errorCode == AccountError.CodeExpired || errorCode == AccountError.TooManyAttempts) step = Step.Intro
            fail(errorCode.toAccountErrorKind())
        }

        fun checkCode(entered: String) = scope.launch {
            isBusy = true
            error = null
            when (val result = accountApi.checkCode(ticket, entered)) {
                CheckResult.Ok -> {
                    step = Step.NewPassword
                    isBusy = false
                }
                is CheckResult.WrongCode -> {
                    code = ""
                    fail(Kind.WrongCode, result.attemptsLeft)
                }
                is CheckResult.Failure -> {
                    code = ""
                    expiredOr(result.error)
                }
            }
        }

        fun submitPassword() {
            val problem = when {
                password.length < PASSWORD_MIN -> Kind.PasswordTooShort
                password.length > PASSWORD_MAX -> Kind.PasswordTooLong
                password != passwordRepeat -> Kind.PasswordsDiffer
                else -> null
            }
            if (problem != null) return fail(problem)
            scope.launch {
                isBusy = true
                error = null
                when (val result = accountApi.resetPassword(ticket, code, password)) {
                    is ConfirmResult.Done -> {
                        // Ключ восстановления на сервере заперт старым паролем: перезапираем новым,
                        // иначе новые устройства не откроют историю (escrow B).
                        recoveryKeyAutoProvisioner.onPasswordChanged(password)
                        password = ""
                        passwordRepeat = ""
                        step = Step.Done
                        isBusy = false
                    }
                    is ConfirmResult.WrongCode -> {
                        step = Step.Code
                        code = ""
                        fail(Kind.WrongCode, result.attemptsLeft)
                    }
                    is ConfirmResult.Failure -> expiredOr(result.error)
                }
            }
        }

        fun resend() = scope.launch {
            isBusy = true
            error = null
            when (val result = accountApi.resendCode(ticket)) {
                is ResendResult.Sent -> {
                    codeSent(result.resendAfterSeconds)
                    isBusy = false
                }
                is ResendResult.TooSoon -> {
                    codeSent(result.retryAfterSeconds)
                    isBusy = false
                }
                is ResendResult.Failure -> expiredOr(result.error)
            }
        }

        fun handleEvent(event: TgAccountPasswordEvent) {
            when (event) {
                TgAccountPasswordEvent.SendCode -> if (!isBusy) sendCode()
                is TgAccountPasswordEvent.SetCode -> if (!isBusy) {
                    code = event.value.filter { it.isDigit() }.take(TgAccountEmailPresenter.CODE_LENGTH)
                    error = null
                    if (code.length == TgAccountEmailPresenter.CODE_LENGTH) checkCode(code)
                }
                TgAccountPasswordEvent.ResendCode -> if (!isBusy) resend()
                is TgAccountPasswordEvent.SetPassword -> {
                    password = event.value
                    error = null
                }
                is TgAccountPasswordEvent.SetPasswordRepeat -> {
                    passwordRepeat = event.value
                    error = null
                }
                TgAccountPasswordEvent.SubmitPassword -> if (!isBusy) submitPassword()
                TgAccountPasswordEvent.Back -> if (!isBusy && (step == Step.Code || step == Step.NewPassword)) {
                    error = null
                    step = Step.Intro
                }
            }
        }

        return TgAccountPasswordState(
            step = step,
            email = email,
            code = code,
            resendAfterSeconds = resendAfterSeconds,
            codeSentCount = codeSentCount,
            password = password,
            passwordRepeat = passwordRepeat,
            isBusy = isBusy,
            error = error,
            eventSink = ::handleEvent,
        )
    }

    private companion object {
        const val PASSWORD_MIN = 8
        const val PASSWORD_MAX = 128
    }
}
