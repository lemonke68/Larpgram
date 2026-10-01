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
import io.element.android.features.preferences.impl.account.TgAccountEmailState.Step
import io.element.android.features.preferences.impl.account.TgAccountError.Kind
import io.element.android.libraries.accountapi.api.AccountApi
import io.element.android.libraries.accountapi.api.AccountEmail
import io.element.android.libraries.accountapi.api.AccountError
import io.element.android.libraries.accountapi.api.AccountSessionApi
import io.element.android.libraries.accountapi.api.EmailChangeResult
import io.element.android.libraries.accountapi.api.ResendResult
import io.element.android.libraries.accountapi.api.StartResult
import io.element.android.libraries.architecture.Presenter
import kotlinx.coroutines.launch

/**
 * Почта аккаунта: привязать или сменить, подтвердив новый адрес кодом. Пароль не спрашиваем —
 * человек уже вошёл (на странице MAS без пароля почту было не добавить, и забывшие пароль
 * оставались без восстановления).
 */
@Inject
class TgAccountEmailPresenter(
    private val accountSessionApi: AccountSessionApi,
    private val accountApi: AccountApi,
) : Presenter<TgAccountEmailState> {
    @Composable
    override fun present(): TgAccountEmailState {
        val scope = rememberCoroutineScope()
        var step by rememberSaveable { mutableStateOf(Step.Enter) }
        var currentEmail by remember { mutableStateOf<AccountEmail?>(null) }
        var email by rememberSaveable { mutableStateOf("") }
        var code by rememberSaveable { mutableStateOf("") }
        var ticket by rememberSaveable { mutableStateOf("") }
        var resendAfterSeconds by rememberSaveable { mutableIntStateOf(0) }
        var codeSentCount by rememberSaveable { mutableIntStateOf(0) }
        var isBusy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<TgAccountError?>(null) }

        LaunchedEffect(Unit) { currentEmail = accountSessionApi.currentEmail() }

        fun fail(kind: Kind, attemptsLeft: Int? = null) {
            error = TgAccountError(kind, attemptsLeft)
            isBusy = false
        }

        fun codeSent(resendAfter: Int) {
            code = ""
            resendAfterSeconds = resendAfter
            codeSentCount++
        }

        fun submit() = scope.launch {
            isBusy = true
            error = null
            when (val result = accountSessionApi.startEmailChange(email.trim())) {
                is StartResult.Sent -> {
                    ticket = result.ticket
                    codeSent(result.resendAfterSeconds)
                    step = Step.Code
                    isBusy = false
                }
                is StartResult.Failure -> fail(result.error.toAccountErrorKind())
            }
        }

        fun confirm(entered: String) = scope.launch {
            isBusy = true
            error = null
            when (val result = accountSessionApi.confirmEmailChange(ticket, entered)) {
                is EmailChangeResult.Done -> {
                    currentEmail = AccountEmail.Address(result.email)
                    step = Step.Done
                    isBusy = false
                }
                is EmailChangeResult.WrongCode -> {
                    code = ""
                    fail(Kind.WrongCode, result.attemptsLeft)
                }
                is EmailChangeResult.Failure -> {
                    code = ""
                    if (result.error == AccountError.CodeExpired || result.error == AccountError.TooManyAttempts ||
                        result.error == AccountError.EmailTaken
                    ) {
                        step = Step.Enter
                    }
                    fail(result.error.toAccountErrorKind())
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
                is ResendResult.Failure -> {
                    if (result.error == AccountError.CodeExpired) step = Step.Enter
                    fail(result.error.toAccountErrorKind())
                }
            }
        }

        fun handleEvent(event: TgAccountEmailEvent) {
            when (event) {
                is TgAccountEmailEvent.SetEmail -> {
                    email = event.value
                    error = null
                }
                TgAccountEmailEvent.Submit -> if (!isBusy) {
                    if (EMAIL.matches(email.trim())) submit() else fail(Kind.EmailInvalid)
                }
                is TgAccountEmailEvent.SetCode -> if (!isBusy) {
                    code = event.value.filter { it.isDigit() }.take(CODE_LENGTH)
                    error = null
                    if (code.length == CODE_LENGTH) confirm(code)
                }
                TgAccountEmailEvent.ResendCode -> if (!isBusy) resend()
                TgAccountEmailEvent.Back -> if (!isBusy && step == Step.Code) {
                    error = null
                    step = Step.Enter
                }
            }
        }

        return TgAccountEmailState(
            step = step,
            currentEmail = currentEmail,
            email = email,
            code = code,
            resendAfterSeconds = resendAfterSeconds,
            codeSentCount = codeSentCount,
            isBusy = isBusy,
            error = error,
            eventSink = ::handleEvent,
        )
    }

    internal companion object {
        const val CODE_LENGTH = 6
        val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$")
    }
}
