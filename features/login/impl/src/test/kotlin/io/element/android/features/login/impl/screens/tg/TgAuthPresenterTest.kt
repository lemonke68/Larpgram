/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.tg

import app.cash.turbine.ReceiveTurbine
import com.google.common.truth.Truth.assertThat
import io.element.android.features.login.impl.screens.tg.TgAuthError.Kind
import io.element.android.libraries.accountapi.api.AccountError
import io.element.android.libraries.accountapi.api.CheckResult
import io.element.android.libraries.accountapi.api.ConfirmResult
import io.element.android.libraries.accountapi.api.LoginStartResult
import io.element.android.libraries.accountapi.api.RedeemLoginResult
import io.element.android.libraries.accountapi.api.ResendResult
import io.element.android.libraries.accountapi.api.StartResult
import io.element.android.libraries.accountapi.test.FakeAccountApi
import io.element.android.libraries.keyescrow.test.FakeLoginPasswordHandoff
import io.element.android.libraries.matrix.api.auth.AuthenticationException
import io.element.android.libraries.matrix.test.auth.FakeMatrixAuthenticationService
import io.element.android.libraries.matrix.test.auth.aMatrixHomeServerDetails
import io.element.android.libraries.matrix.test.core.aBuildMeta
import io.element.android.libraries.sessionstorage.api.SessionStore
import io.element.android.libraries.sessionstorage.test.InMemorySessionStore
import io.element.android.libraries.sessionstorage.test.aSessionData
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.consumeItemsUntilPredicate
import io.element.android.tests.testutils.test
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class TgAuthPresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    @Test
    fun `first account starts at the welcome step, start opens login`() = runTest {
        createPresenter().test {
            val welcome = awaitState { it.step == TgAuthStep.Welcome }
            assertThat(welcome.isAddingAccount).isFalse()
            welcome.eventSink(TgAuthEvent.Start)
            assertThat(awaitState { it.step == TgAuthStep.Login }.canSubmitLogin).isFalse()
        }
    }

    @Test
    fun `adding an account skips the welcome step`() = runTest {
        val sessionStore = InMemorySessionStore(initialList = listOf(aSessionData()))
        createPresenter(sessionStore = sessionStore).test {
            val login = awaitState { it.step == TgAuthStep.Login }
            assertThat(login.isAddingAccount).isTrue()
            // Назад с первого шага презентер не уводит: закрывает экран узел.
            login.eventSink(TgAuthEvent.Back)
            expectNoEvents()
        }
    }

    @Test
    fun `login - wrong password shows an error, typing clears it`() = runTest {
        val auth = anAuthService().apply { givenLoginError(AuthenticationException.Generic("M_FORBIDDEN")) }
        createPresenter(authenticationService = auth).test {
            val sink = openLogin()
            sink(TgAuthEvent.SetLogin("@vasya"))
            sink(TgAuthEvent.SetPassword("password"))
            assertThat(awaitState { it.password == "password" }.canSubmitLogin).isTrue()
            sink(TgAuthEvent.SubmitLogin)
            val failed = awaitState { it.error != null && !it.isBusy }
            assertThat(failed.error).isEqualTo(TgAuthError(Kind.InvalidCredentials))
            assertThat(failed.isBusy).isFalse()
            sink(TgAuthEvent.SetPassword("password2"))
            awaitState { it.password == "password2" && it.error == null }
        }
    }

    @Test
    fun `login - success keeps the screen busy until the session takes over`() = runTest {
        createPresenter().test {
            val sink = openLogin()
            sink(TgAuthEvent.SetLogin("vasya"))
            sink(TgAuthEvent.SetPassword("password"))
            sink(TgAuthEvent.SubmitLogin)
            awaitState { it.isBusy }
            expectNoEvents()
        }
    }

    @Test
    fun `login - an email is resolved to the nick first, a nick is not`() = runTest {
        val resolved = mutableListOf<String>()
        val api = FakeAccountApi(usernameByEmailLambda = {
            resolved += it
            "vasya"
        })
        createPresenter(accountApi = api).test {
            val sink = openLogin()
            sink(TgAuthEvent.SetLogin(" Vasya@Example.com "))
            sink(TgAuthEvent.SetPassword("password"))
            sink(TgAuthEvent.SubmitLogin)
            awaitState { it.isBusy }
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(resolved).containsExactly("Vasya@Example.com")

        resolved.clear()
        createPresenter(accountApi = api).test {
            val sink = openLogin()
            sink(TgAuthEvent.SetLogin("@vasya:server"))
            sink(TgAuthEvent.SetPassword("password"))
            sink(TgAuthEvent.SubmitLogin)
            awaitState { it.isBusy }
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(resolved).isEmpty()
    }

    @Test
    fun `login - unreachable server is a network error`() = runTest {
        val auth = FakeMatrixAuthenticationService(setHomeserverResult = { Result.failure(IllegalStateException("offline")) })
        createPresenter(authenticationService = auth).test {
            val sink = openLogin()
            sink(TgAuthEvent.SetLogin("vasya"))
            sink(TgAuthEvent.SetPassword("password"))
            sink(TgAuthEvent.SubmitLogin)
            assertThat(awaitState { it.error != null && !it.isBusy }.error).isEqualTo(TgAuthError(Kind.Network))
        }
    }

    @Test
    fun `login - right password asks for the emailed code, the code signs in under the resolved nick`() = runTest {
        val started = mutableListOf<Pair<String, String>>()
        val confirmed = mutableListOf<Pair<String, String>>()
        val api = FakeAccountApi(
            startLoginLambda = { login, password ->
                started += login to password
                LoginStartResult.CodeSent(ticket = "T1", resendAfterSeconds = 60, username = "vasya", emailHint = "v***@example.com")
            },
            confirmLoginLambda = { ticket, code ->
                confirmed += ticket to code
                if (code == "123456") ConfirmResult.Done("vasya") else ConfirmResult.WrongCode(attemptsLeft = 4)
            },
        )
        createPresenter(accountApi = api).test {
            val sink = openLogin()
            sink(TgAuthEvent.SetLogin(" Vasya@Example.com "))
            sink(TgAuthEvent.SetPassword("password"))
            sink(TgAuthEvent.SubmitLogin)
            val codeStep = awaitState { it.step == TgAuthStep.LoginCode && !it.isBusy }
            assertThat(codeStep.loginEmailHint).isEqualTo("v***@example.com")
            assertThat(codeStep.resendAfterSeconds).isEqualTo(60)

            sink(TgAuthEvent.SetCode("000000"))
            val wrong = awaitState { it.error != null && !it.isBusy }
            assertThat(wrong.error).isEqualTo(TgAuthError(Kind.WrongCode, attemptsLeft = 4))
            assertThat(wrong.code).isEmpty()

            sink(TgAuthEvent.SetCode("123456"))
            // Код принят: дальше обычный вход, экран занят, пока сессия не заберёт управление.
            awaitState { it.isBusy && it.error == null }
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(started).containsExactly("Vasya@Example.com" to "password")
        assertThat(confirmed).containsExactly("T1" to "000000", "T1" to "123456").inOrder()
    }

    @Test
    fun `login - wrong password from the service is an error without a code step`() = runTest {
        val api = FakeAccountApi(startLoginLambda = { _, _ -> LoginStartResult.Failure(AccountError.InvalidCredentials) })
        createPresenter(accountApi = api).test {
            val sink = openLogin()
            sink(TgAuthEvent.SetLogin("vasya"))
            sink(TgAuthEvent.SetPassword("password"))
            sink(TgAuthEvent.SubmitLogin)
            val failed = awaitState { it.error != null && !it.isBusy }
            assertThat(failed.error).isEqualTo(TgAuthError(Kind.InvalidCredentials))
            assertThat(failed.step).isEqualTo(TgAuthStep.Login)
        }
    }

    @Test
    fun `login - an account without email signs in at once`() = runTest {
        val api = FakeAccountApi(startLoginLambda = { _, _ -> LoginStartResult.NoCodeNeeded("oldie") })
        createPresenter(accountApi = api).test {
            val sink = openLogin()
            sink(TgAuthEvent.SetLogin("oldie"))
            sink(TgAuthEvent.SetPassword("password"))
            sink(TgAuthEvent.SubmitLogin)
            awaitState { it.isBusy }
            expectNoEvents()
        }
    }

    @Test
    fun `login - an expired code returns to the login step, back from the code step too`() = runTest {
        val api = FakeAccountApi(
            startLoginLambda = { _, _ -> LoginStartResult.CodeSent("T1", 60, "vasya", "v***@example.com") },
            confirmLoginLambda = { _, _ -> ConfirmResult.Failure(AccountError.CodeExpired) },
        )
        createPresenter(accountApi = api).test {
            val sink = openLogin()
            sink(TgAuthEvent.SetLogin("vasya"))
            sink(TgAuthEvent.SetPassword("password"))
            sink(TgAuthEvent.SubmitLogin)
            awaitState { it.step == TgAuthStep.LoginCode && !it.isBusy }
            sink(TgAuthEvent.Back)
            awaitState { it.step == TgAuthStep.Login }

            sink(TgAuthEvent.SubmitLogin)
            awaitState { it.step == TgAuthStep.LoginCode && !it.isBusy }
            sink(TgAuthEvent.SetCode("123456"))
            val expired = awaitState { it.error != null && !it.isBusy }
            assertThat(expired.step).isEqualTo(TgAuthStep.Login)
            assertThat(expired.error).isEqualTo(TgAuthError(Kind.CodeExpired))
        }
    }

    @Test
    fun `register - local validation stops before the server`() = runTest {
        var calls = 0
        val api = FakeAccountApi(startRegistrationLambda = { _, _ ->
            calls++
            StartResult.Sent("T", 60)
        })
        createPresenter(accountApi = api).test {
            val sink = openRegister()
            suspend fun expectError(expected: Kind, username: String, email: String, password: String, repeat: String) {
                sink(TgAuthEvent.SetRegisterUsername(username))
                sink(TgAuthEvent.SetRegisterEmail(email))
                sink(TgAuthEvent.SetRegisterPassword(password))
                sink(TgAuthEvent.SetRegisterPasswordRepeat(repeat))
                sink(TgAuthEvent.SubmitRegister)
                awaitState { it.error?.kind == expected }
            }
            expectError(Kind.UsernameTooShort, "ab", "v@example.com", "password", "password")
            expectError(Kind.UsernameInvalid, "Вася", "v@example.com", "password", "password")
            expectError(Kind.EmailInvalid, "vasya", "nope", "password", "password")
            expectError(Kind.PasswordTooShort, "vasya", "v@example.com", "short", "short")
            expectError(Kind.PasswordsDiffer, "vasya", "v@example.com", "password", "passw0rd")
            assertThat(calls).isEqualTo(0)
        }
    }

    @Test
    fun `register - code step, sixth digit confirms and signs in`() = runTest {
        val started = mutableListOf<Pair<String, String>>()
        val confirmed = mutableListOf<Triple<String, String, String>>()
        val api = FakeAccountApi(
            startRegistrationLambda = { username, email ->
                started += username to email
                StartResult.Sent("T1", 60)
            },
            confirmRegistrationLambda = { ticket, code, password ->
                confirmed += Triple(ticket, code, password)
                ConfirmResult.Done("vasya")
            },
        )
        createPresenter(accountApi = api).test {
            val sink = openRegister()
            sink.fillRegister(username = " @Vasya ", email = " v@example.com ")
            sink(TgAuthEvent.SubmitRegister)
            val codeStep = awaitState { it.step == TgAuthStep.RegisterCode }
            assertThat(started).containsExactly("vasya" to "v@example.com")
            assertThat(codeStep.resendAfterSeconds).isEqualTo(60)
            assertThat(codeStep.codeSentCount).isEqualTo(1)

            // Лишнее отбрасывается: буквы и седьмая цифра.
            sink(TgAuthEvent.SetCode("12a34"))
            assertThat(awaitState { it.code.isNotEmpty() }.code).isEqualTo("1234")
            assertThat(confirmed).isEmpty()
            sink(TgAuthEvent.SetCode("1234567"))
            awaitState { it.isBusy }
            assertThat(confirmed).containsExactly(Triple("T1", "123456", "password"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `register - wrong code clears the field and keeps the step`() = runTest {
        val api = FakeAccountApi(
            startRegistrationLambda = { _, _ -> StartResult.Sent("T1", 60) },
            confirmRegistrationLambda = { _, _, _ -> ConfirmResult.WrongCode(attemptsLeft = 4) },
        )
        createPresenter(accountApi = api).test {
            val sink = openRegisterCode()
            sink(TgAuthEvent.SetCode("000000"))
            val failed = awaitState { it.error != null && !it.isBusy }
            assertThat(failed.step).isEqualTo(TgAuthStep.RegisterCode)
            assertThat(failed.error).isEqualTo(TgAuthError(Kind.WrongCode, attemptsLeft = 4))
            assertThat(failed.code).isEmpty()
        }
    }

    @Test
    fun `register - weak password returns to the form and is fixed without a new code`() = runTest {
        var starts = 0
        val confirmed = mutableListOf<Pair<String, String>>()
        val api = FakeAccountApi(
            startRegistrationLambda = { _, _ ->
                starts++
                StartResult.Sent("T1", 60)
            },
            confirmRegistrationLambda = { _, code, password ->
                confirmed += code to password
                if (password == "password") ConfirmResult.Failure(AccountError.PasswordWeak) else ConfirmResult.Done("vasya")
            },
        )
        createPresenter(accountApi = api).test {
            val sink = openRegisterCode()
            sink(TgAuthEvent.SetCode("123456"))
            val weak = awaitState { it.error != null && !it.isBusy }
            assertThat(weak.step).isEqualTo(TgAuthStep.Register)
            assertThat(weak.error).isEqualTo(TgAuthError(Kind.PasswordWeak))

            sink(TgAuthEvent.SetRegisterPassword("correct horse"))
            sink(TgAuthEvent.SetRegisterPasswordRepeat("correct horse"))
            sink(TgAuthEvent.SubmitRegister)
            awaitState { it.isBusy }
            assertThat(starts).isEqualTo(1)
            assertThat(confirmed).containsExactly("123456" to "password", "" to "correct horse").inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `register - taken nick from the server stays on the form`() = runTest {
        val api = FakeAccountApi(startRegistrationLambda = { _, _ -> StartResult.Failure(AccountError.UsernameTaken) })
        createPresenter(accountApi = api).test {
            val sink = openRegister()
            sink.fillRegister()
            sink(TgAuthEvent.SubmitRegister)
            val failed = awaitState { it.error != null && !it.isBusy }
            assertThat(failed.step).isEqualTo(TgAuthStep.Register)
            assertThat(failed.error).isEqualTo(TgAuthError(Kind.UsernameTaken))
        }
    }

    @Test
    fun `register - account created but sign in failed lands on login with the fields filled`() = runTest {
        val auth = anAuthService().apply { givenLoginError(IllegalStateException("offline")) }
        val api = FakeAccountApi(
            startRegistrationLambda = { _, _ -> StartResult.Sent("T1", 60) },
            confirmRegistrationLambda = { _, _, _ -> ConfirmResult.Done("vasya") },
        )
        createPresenter(authenticationService = auth, accountApi = api).test {
            val sink = openRegisterCode()
            sink(TgAuthEvent.SetCode("123456"))
            val login = awaitState { it.step == TgAuthStep.Login }
            assertThat(login.login).isEqualTo("vasya")
            assertThat(login.password).isEqualTo("password")
            assertThat(login.error).isEqualTo(TgAuthError(Kind.Network))
        }
    }

    @Test
    fun `resend - new countdown, too soon keeps waiting, expired returns to the form`() = runTest {
        var answer: ResendResult = ResendResult.Sent(60)
        val api = FakeAccountApi(
            startRegistrationLambda = { _, _ -> StartResult.Sent("T1", 60) },
            resendCodeLambda = { answer },
        )
        createPresenter(accountApi = api).test {
            val sink = openRegisterCode()
            sink(TgAuthEvent.ResendCode)
            assertThat(awaitState { it.codeSentCount == 2 }.resendAfterSeconds).isEqualTo(60)

            answer = ResendResult.TooSoon(42)
            sink(TgAuthEvent.ResendCode)
            assertThat(awaitState { it.codeSentCount == 3 }.resendAfterSeconds).isEqualTo(42)

            answer = ResendResult.Failure(AccountError.CodeExpired)
            sink(TgAuthEvent.ResendCode)
            val expired = awaitState { it.error != null && !it.isBusy }
            assertThat(expired.step).isEqualTo(TgAuthStep.Register)
            assertThat(expired.error).isEqualTo(TgAuthError(Kind.CodeExpired))
        }
    }

    @Test
    fun `forgot - login is prefilled, code is checked, new password signs in`() = runTest {
        val forgotten = mutableListOf<String>()
        val reset = mutableListOf<Triple<String, String, String>>()
        val api = FakeAccountApi(
            forgotPasswordLambda = { login ->
                forgotten += login
                StartResult.Sent("T2", 60)
            },
            checkCodeLambda = { _, code -> if (code == "123456") CheckResult.Ok else CheckResult.WrongCode(2) },
            resetPasswordLambda = { ticket, code, password ->
                reset += Triple(ticket, code, password)
                ConfirmResult.Done("vasya")
            },
        )
        createPresenter(accountApi = api).test {
            val sink = openLogin()
            sink(TgAuthEvent.SetLogin("vasya"))
            sink(TgAuthEvent.OpenForgot)
            val forgot = awaitState { it.step == TgAuthStep.Forgot }
            assertThat(forgot.forgotLogin).isEqualTo("vasya")
            sink(TgAuthEvent.SubmitForgot)
            awaitState { it.step == TgAuthStep.ForgotCode }
            assertThat(forgotten).containsExactly("vasya")

            sink(TgAuthEvent.SetCode("000000"))
            assertThat(awaitState { it.error != null && !it.isBusy }.error).isEqualTo(TgAuthError(Kind.WrongCode, attemptsLeft = 2))
            sink(TgAuthEvent.SetCode("123456"))
            awaitState { it.step == TgAuthStep.NewPassword }

            sink(TgAuthEvent.SetNewPassword("new password"))
            sink(TgAuthEvent.SetNewPasswordRepeat("other password"))
            sink(TgAuthEvent.SubmitNewPassword)
            assertThat(awaitState { it.error != null && !it.isBusy }.error).isEqualTo(TgAuthError(Kind.PasswordsDiffer))
            assertThat(reset).isEmpty()

            sink(TgAuthEvent.SetNewPasswordRepeat("new password"))
            sink(TgAuthEvent.SubmitNewPassword)
            awaitState { it.isBusy }
            assertThat(reset).containsExactly(Triple("T2", "123456", "new password"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `back walks the steps in reverse`() = runTest {
        val api = FakeAccountApi(forgotPasswordLambda = { StartResult.Sent("T2", 60) })
        createPresenter(accountApi = api).test {
            val sink = openLogin()
            sink(TgAuthEvent.SetLogin("vasya"))
            sink(TgAuthEvent.OpenForgot)
            awaitState { it.step == TgAuthStep.Forgot }
            sink(TgAuthEvent.SubmitForgot)
            awaitState { it.step == TgAuthStep.ForgotCode }
            sink(TgAuthEvent.Back)
            awaitState { it.step == TgAuthStep.Forgot }
            sink(TgAuthEvent.Back)
            awaitState { it.step == TgAuthStep.Login }
            sink(TgAuthEvent.OpenRegister)
            awaitState { it.step == TgAuthStep.Register }
            sink(TgAuthEvent.Back)
            awaitState { it.step == TgAuthStep.Login }
            sink(TgAuthEvent.Back)
            awaitState { it.step == TgAuthStep.Welcome }
        }
    }

    @Test
    fun `qr - scanned code is redeemed and the token signs in`() = runTest {
        val auth = anAuthService()
        val redeemed = mutableListOf<Pair<String, String>>()
        val api = FakeAccountApi(
            redeemLoginCodeLambda = { code, deviceName ->
                redeemed += code to deviceName
                RedeemLoginResult.Session("@vasya:server", "DEVICE", "mpt_token")
            },
        )
        createPresenter(authenticationService = auth, accountApi = api).test {
            val sink = openLogin()
            sink(TgAuthEvent.OpenQrScan)
            awaitState { it.step == TgAuthStep.ScanQr }
            sink(TgAuthEvent.QrScanned("larpgram-login:CODE", "Honor"))
            awaitState { it.isBusy }
            // Вход идёт после обмена кода: ждём, пока дойдёт до токена.
            while (auth.loginWithAccessTokenCalls.isEmpty()) delay(10)
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(redeemed).containsExactly("CODE" to "Honor")
        assertThat(auth.loginWithAccessTokenCalls).containsExactly(Triple("@vasya:server", "DEVICE", "mpt_token"))
    }

    @Test
    fun `qr - foreign and expired codes show an error, scanning waits for retry`() = runTest {
        var calls = 0
        val api = FakeAccountApi(
            redeemLoginCodeLambda = { _, _ ->
                calls++
                RedeemLoginResult.Failure(AccountError.CodeExpired)
            },
        )
        createPresenter(accountApi = api).test {
            val sink = openLogin()
            sink(TgAuthEvent.OpenQrScan)
            awaitState { it.step == TgAuthStep.ScanQr }
            sink(TgAuthEvent.QrScanned("https://example.com", "Honor"))
            assertThat(awaitState { it.error != null && !it.isBusy }.error).isEqualTo(TgAuthError(Kind.QrInvalid))
            // Пока ошибка на экране, камера может прочитать код ещё раз — его не обрабатываем.
            sink(TgAuthEvent.QrScanned("larpgram-login:CODE", "Honor"))
            expectNoEvents()
            assertThat(calls).isEqualTo(0)

            sink(TgAuthEvent.ClearError)
            awaitState { it.error == null }
            sink(TgAuthEvent.QrScanned("larpgram-login:CODE", "Honor"))
            assertThat(awaitState { it.error != null && !it.isBusy }.error).isEqualTo(TgAuthError(Kind.QrExpired))
            sink(TgAuthEvent.Back)
            awaitState { it.step == TgAuthStep.Login }
        }
    }

    private suspend fun ReceiveTurbine<TgAuthState>.awaitState(predicate: (TgAuthState) -> Boolean): TgAuthState =
        consumeItemsUntilPredicate(predicate = predicate).last().also { assertThat(predicate(it)).isTrue() }

    private suspend fun ReceiveTurbine<TgAuthState>.openLogin(): (TgAuthEvent) -> Unit {
        val sink = awaitState { it.step == TgAuthStep.Welcome }.eventSink
        sink(TgAuthEvent.Start)
        awaitState { it.step == TgAuthStep.Login }
        return sink
    }

    private suspend fun ReceiveTurbine<TgAuthState>.openRegister(): (TgAuthEvent) -> Unit {
        val sink = openLogin()
        sink(TgAuthEvent.OpenRegister)
        awaitState { it.step == TgAuthStep.Register }
        return sink
    }

    private suspend fun ReceiveTurbine<TgAuthState>.openRegisterCode(): (TgAuthEvent) -> Unit {
        val sink = openRegister()
        sink.fillRegister()
        sink(TgAuthEvent.SubmitRegister)
        awaitState { it.step == TgAuthStep.RegisterCode }
        return sink
    }

    private fun ((TgAuthEvent) -> Unit).fillRegister(
        username: String = "vasya",
        email: String = "v@example.com",
        password: String = "password",
    ) {
        this(TgAuthEvent.SetRegisterUsername(username))
        this(TgAuthEvent.SetRegisterEmail(email))
        this(TgAuthEvent.SetRegisterPassword(password))
        this(TgAuthEvent.SetRegisterPasswordRepeat(password))
    }

    private fun anAuthService() = FakeMatrixAuthenticationService(
        setHomeserverResult = { Result.success(aMatrixHomeServerDetails()) },
    )

    private fun createPresenter(
        authenticationService: FakeMatrixAuthenticationService = anAuthService(),
        accountApi: FakeAccountApi = FakeAccountApi(),
        sessionStore: SessionStore = InMemorySessionStore(),
    ) = TgAuthPresenter(
        authenticationService = authenticationService,
        accountApi = accountApi,
        sessionStore = sessionStore,
        buildMeta = aBuildMeta(),
        rageshakeFeatureAvailability = { flowOf(true) },
        onBoardingLogoResIdProvider = { null },
        loginPasswordHandoff = FakeLoginPasswordHandoff(),
    )
}
