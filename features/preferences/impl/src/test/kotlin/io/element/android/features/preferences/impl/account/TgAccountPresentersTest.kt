/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.account

import app.cash.turbine.ReceiveTurbine
import com.google.common.truth.Truth.assertThat
import io.element.android.features.preferences.impl.account.TgAccountError.Kind
import io.element.android.libraries.accountapi.api.AccountEmail
import io.element.android.libraries.accountapi.api.AccountError
import io.element.android.libraries.accountapi.api.CheckResult
import io.element.android.libraries.accountapi.api.ConfirmResult
import io.element.android.libraries.accountapi.api.EmailChangeResult
import io.element.android.libraries.accountapi.api.StartResult
import io.element.android.libraries.accountapi.test.FakeAccountApi
import io.element.android.libraries.accountapi.test.FakeAccountSessionApi
import io.element.android.libraries.keyescrow.test.FakeRecoveryKeyAutoProvisioner
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.consumeItemsUntilPredicate
import io.element.android.tests.testutils.test
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class TgAccountPresentersTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    @Test
    fun `email - invalid address stops before the server`() = runTest {
        var calls = 0
        val api = FakeAccountSessionApi(startEmailChangeLambda = {
            calls++
            StartResult.Sent("T", 60)
        })
        TgAccountEmailPresenter(api, FakeAccountApi()).test {
            val sink = awaitItem().eventSink
            sink(TgAccountEmailEvent.SetEmail("nope"))
            sink(TgAccountEmailEvent.Submit)
            assertThat(awaitState { it.error != null }.error).isEqualTo(TgAccountError(Kind.EmailInvalid))
            assertThat(calls).isEqualTo(0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `email - code step, wrong code, then the address is bound`() = runTest {
        val started = mutableListOf<String>()
        val api = FakeAccountSessionApi(
            currentEmailLambda = { AccountEmail.None },
            startEmailChangeLambda = {
                started += it
                StartResult.Sent("T", 60)
            },
            confirmEmailChangeLambda = { ticket, code ->
                if (ticket == "T" && code == "123456") EmailChangeResult.Done("v@example.com") else EmailChangeResult.WrongCode(4)
            },
        )
        TgAccountEmailPresenter(api, FakeAccountApi()).test {
            val sink = awaitState { it.currentEmail == AccountEmail.None }.eventSink
            sink(TgAccountEmailEvent.SetEmail(" v@example.com "))
            sink(TgAccountEmailEvent.Submit)
            awaitState { it.step == TgAccountEmailState.Step.Code }
            assertThat(started).containsExactly("v@example.com")

            sink(TgAccountEmailEvent.SetCode("000000"))
            val wrong = awaitState { it.error != null && !it.isBusy }
            assertThat(wrong.error).isEqualTo(TgAccountError(Kind.WrongCode, 4))
            assertThat(wrong.code).isEmpty()

            sink(TgAccountEmailEvent.SetCode("123456"))
            val done = awaitState { it.step == TgAccountEmailState.Step.Done }
            assertThat(done.currentEmail).isEqualTo(AccountEmail.Address("v@example.com"))
        }
    }

    @Test
    fun `email - taken address stays on the form`() = runTest {
        val api = FakeAccountSessionApi(startEmailChangeLambda = { StartResult.Failure(AccountError.EmailTaken) })
        TgAccountEmailPresenter(api, FakeAccountApi()).test {
            val sink = awaitItem().eventSink
            sink(TgAccountEmailEvent.SetEmail("v@example.com"))
            sink(TgAccountEmailEvent.Submit)
            val failed = awaitState { it.error != null && !it.isBusy }
            assertThat(failed.step).isEqualTo(TgAccountEmailState.Step.Enter)
            assertThat(failed.error).isEqualTo(TgAccountError(Kind.EmailTaken))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `password - code goes to the account of this session, new password is saved`() = runTest {
        val forgotten = mutableListOf<String>()
        val reset = mutableListOf<Triple<String, String, String>>()
        val accountApi = FakeAccountApi(
            forgotPasswordLambda = {
                forgotten += it
                StartResult.Sent("T", 60)
            },
            checkCodeLambda = { _, code -> if (code == "123456") CheckResult.Ok else CheckResult.WrongCode(2) },
            resetPasswordLambda = { ticket, code, password ->
                reset += Triple(ticket, code, password)
                ConfirmResult.Done("vasya")
            },
        )
        val sessionApi = FakeAccountSessionApi(currentEmailLambda = { AccountEmail.Address("v@example.com") })
        val client = FakeMatrixClient(sessionId = SessionId("@vasya:example.org"))
        TgAccountPasswordPresenter(client, sessionApi, accountApi, FakeRecoveryKeyAutoProvisioner()).test {
            val sink = awaitState { it.email != null }.eventSink
            sink(TgAccountPasswordEvent.SendCode)
            awaitState { it.step == TgAccountPasswordState.Step.Code }
            assertThat(forgotten).containsExactly("vasya")

            sink(TgAccountPasswordEvent.SetCode("000000"))
            assertThat(awaitState { it.error != null && !it.isBusy }.error).isEqualTo(TgAccountError(Kind.WrongCode, 2))
            sink(TgAccountPasswordEvent.SetCode("123456"))
            awaitState { it.step == TgAccountPasswordState.Step.NewPassword }

            sink(TgAccountPasswordEvent.SetPassword("short"))
            sink(TgAccountPasswordEvent.SetPasswordRepeat("short"))
            sink(TgAccountPasswordEvent.SubmitPassword)
            assertThat(awaitState { it.error != null }.error).isEqualTo(TgAccountError(Kind.PasswordTooShort))
            sink(TgAccountPasswordEvent.SetPassword("new password"))
            sink(TgAccountPasswordEvent.SetPasswordRepeat("other password"))
            sink(TgAccountPasswordEvent.SubmitPassword)
            awaitState { it.error == TgAccountError(Kind.PasswordsDiffer) }
            assertThat(reset).isEmpty()

            sink(TgAccountPasswordEvent.SetPasswordRepeat("new password"))
            sink(TgAccountPasswordEvent.SubmitPassword)
            val done = awaitState { it.step == TgAccountPasswordState.Step.Done }
            assertThat(done.password).isEmpty()
            assertThat(reset).containsExactly(Triple("T", "123456", "new password"))
        }
    }

    @Test
    fun `password - weak password stays on the form, expired code returns to the start`() = runTest {
        var answer: ConfirmResult = ConfirmResult.Failure(AccountError.PasswordWeak)
        val accountApi = FakeAccountApi(
            forgotPasswordLambda = { StartResult.Sent("T", 60) },
            checkCodeLambda = { _, _ -> CheckResult.Ok },
            resetPasswordLambda = { _, _, _ -> answer },
        )
        TgAccountPasswordPresenter(FakeMatrixClient(), FakeAccountSessionApi(), accountApi, FakeRecoveryKeyAutoProvisioner()).test {
            val sink = awaitItem().eventSink
            sink(TgAccountPasswordEvent.SendCode)
            awaitState { it.step == TgAccountPasswordState.Step.Code }
            sink(TgAccountPasswordEvent.SetCode("123456"))
            awaitState { it.step == TgAccountPasswordState.Step.NewPassword }
            sink(TgAccountPasswordEvent.SetPassword("password"))
            sink(TgAccountPasswordEvent.SetPasswordRepeat("password"))
            sink(TgAccountPasswordEvent.SubmitPassword)
            val weak = awaitState { it.error != null && !it.isBusy }
            assertThat(weak.step).isEqualTo(TgAccountPasswordState.Step.NewPassword)
            assertThat(weak.error).isEqualTo(TgAccountError(Kind.PasswordWeak))

            answer = ConfirmResult.Failure(AccountError.CodeExpired)
            sink(TgAccountPasswordEvent.SubmitPassword)
            val expired = awaitState { it.error == TgAccountError(Kind.CodeExpired) && !it.isBusy }
            assertThat(expired.step).isEqualTo(TgAccountPasswordState.Step.Intro)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun <T : Any> ReceiveTurbine<T>.awaitState(predicate: (T) -> Boolean): T =
        consumeItemsUntilPredicate(predicate = predicate).last().also { assertThat(predicate(it)).isTrue() }
}
