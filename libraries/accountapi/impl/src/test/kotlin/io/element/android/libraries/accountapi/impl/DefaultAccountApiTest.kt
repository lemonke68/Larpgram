/*
 * Copyright (c) 2026 Larpgram.
 * Модуль форка: регистрация и сброс пароля из приложения (сервис server/account).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.accountapi.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.accountapi.api.AccountDevice
import io.element.android.libraries.accountapi.api.AccountEmail
import io.element.android.libraries.accountapi.api.AccountError
import io.element.android.libraries.accountapi.api.CheckResult
import io.element.android.libraries.accountapi.api.ConfirmResult
import io.element.android.libraries.accountapi.api.EmailChangeResult
import io.element.android.libraries.accountapi.api.LoginOfferResult
import io.element.android.libraries.accountapi.api.LoginOfferState
import io.element.android.libraries.accountapi.api.LoginStartResult
import io.element.android.libraries.accountapi.api.RedeemLoginResult
import io.element.android.libraries.accountapi.api.ResendResult
import io.element.android.libraries.accountapi.api.StartResult
import io.element.android.libraries.matrix.api.core.DeviceId
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Test
import java.io.IOException

/**
 * Ответы сервиса account → результаты для экранов. Сервер отвечает перехватчиком OkHttp, сети
 * нет. Главное: непонятный ответ и обрыв становятся «нет связи», а не чужой ошибкой поля.
 */
class DefaultAccountApiTest {
    @Test
    fun `startRegistration sends nick and email as json and returns the ticket`() = runTest {
        val server = respond(200, """{"ticket":"T1","resend_after":60}""")
        assertThat(api(server).startRegistration("vasya", "v@example.com")).isEqualTo(StartResult.Sent("T1", 60))
        val request = server.requests.single()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.url.encodedPath).endsWith("/account/register/start")
        assertThat(request.bodyText()).isEqualTo("""{"username":"vasya","email":"v@example.com"}""")
        assertThat(request.header("Authorization")).isNull()
    }

    @Test
    fun `startRegistration maps field errors`() = runTest {
        suspend fun errorOf(code: Int, error: String) =
            (api(respond(code, """{"error":"$error"}""")).startRegistration("v", "e") as StartResult.Failure).error
        assertThat(errorOf(400, "username_too_short")).isEqualTo(AccountError.UsernameTooShort)
        assertThat(errorOf(400, "username_too_long")).isEqualTo(AccountError.UsernameTooLong)
        assertThat(errorOf(400, "username_invalid")).isEqualTo(AccountError.UsernameInvalid)
        assertThat(errorOf(409, "username_taken")).isEqualTo(AccountError.UsernameTaken)
        assertThat(errorOf(400, "email_invalid")).isEqualTo(AccountError.EmailInvalid)
        assertThat(errorOf(409, "email_taken")).isEqualTo(AccountError.EmailTaken)
        assertThat(errorOf(429, "too_many_requests")).isEqualTo(AccountError.TooManyRequests)
        assertThat(errorOf(502, "mail_failed")).isEqualTo(AccountError.MailFailed)
        assertThat(errorOf(502, "upstream_failed")).isEqualTo(AccountError.Network)
    }

    @Test
    fun `anything unreadable is a network error`() = runTest {
        val network = StartResult.Failure(AccountError.Network)
        assertThat(api(failing()).startRegistration("v", "e")).isEqualTo(network)
        assertThat(api(respond(502, "<html>Bad Gateway</html>")).startRegistration("v", "e")).isEqualTo(network)
        assertThat(api(respond(200, "")).startRegistration("v", "e")).isEqualTo(network)
        assertThat(api(respond(200, """{"ticket":""}""")).forgotPassword("v")).isEqualTo(network)
    }

    @Test
    fun `confirmRegistration returns the nick, wrong code keeps attempts`() = runTest {
        val server = respond(200, """{"username":"vasya"}""")
        assertThat(api(server).confirmRegistration("T1", "123456", "secret pass")).isEqualTo(ConfirmResult.Done("vasya"))
        assertThat(server.requests.single().bodyText()).isEqualTo("""{"ticket":"T1","code":"123456","password":"secret pass"}""")
        assertThat(server.requests.single().url.toString()).doesNotContain("secret")

        assertThat(api(respond(400, """{"error":"wrong_code","attempts_left":3}""")).confirmRegistration("T1", "0", "p"))
            .isEqualTo(ConfirmResult.WrongCode(3))
        assertThat(api(respond(400, """{"error":"password_weak"}""")).confirmRegistration("T1", "0", "p"))
            .isEqualTo(ConfirmResult.Failure(AccountError.PasswordWeak))
        assertThat(api(respond(410, """{"error":"code_expired"}""")).confirmRegistration("T1", "0", "p"))
            .isEqualTo(ConfirmResult.Failure(AccountError.CodeExpired))
        assertThat(api(respond(429, """{"error":"too_many_attempts"}""")).confirmRegistration("T1", "0", "p"))
            .isEqualTo(ConfirmResult.Failure(AccountError.TooManyAttempts))
        assertThat(api(failing()).confirmRegistration("T1", "0", "p")).isEqualTo(ConfirmResult.Failure(AccountError.Network))
    }

    @Test
    fun `forgotPassword and resetPassword use their own paths`() = runTest {
        val forgot = respond(200, """{"ticket":"T2","resend_after":60}""")
        assertThat(api(forgot).forgotPassword("vasya")).isEqualTo(StartResult.Sent("T2", 60))
        assertThat(forgot.requests.single().url.encodedPath).endsWith("/account/password/forgot")
        assertThat(forgot.requests.single().bodyText()).isEqualTo("""{"login":"vasya"}""")

        val reset = respond(200, """{"username":"vasya"}""")
        assertThat(api(reset).resetPassword("T2", "123456", "new pass")).isEqualTo(ConfirmResult.Done("vasya"))
        assertThat(reset.requests.single().url.encodedPath).endsWith("/account/password/reset")
    }

    @Test
    fun `checkCode maps 204, wrong code and expiry`() = runTest {
        val server = respond(204)
        assertThat(api(server).checkCode("T1", "123456")).isEqualTo(CheckResult.Ok)
        assertThat(server.requests.single().bodyText()).isEqualTo("""{"ticket":"T1","code":"123456"}""")
        assertThat(api(respond(400, """{"error":"wrong_code","attempts_left":1}""")).checkCode("T1", "0"))
            .isEqualTo(CheckResult.WrongCode(1))
        assertThat(api(respond(410, """{"error":"code_expired"}""")).checkCode("T1", "0"))
            .isEqualTo(CheckResult.Failure(AccountError.CodeExpired))
        assertThat(api(failing()).checkCode("T1", "0")).isEqualTo(CheckResult.Failure(AccountError.Network))
    }

    @Test
    fun `resendCode maps success, too soon and limits`() = runTest {
        assertThat(api(respond(200, """{"resend_after":60}""")).resendCode("T1")).isEqualTo(ResendResult.Sent(60))
        assertThat(api(respond(429, """{"error":"too_soon","retry_after":42}""")).resendCode("T1")).isEqualTo(ResendResult.TooSoon(42))
        assertThat(api(respond(429, """{"error":"too_many_requests"}""")).resendCode("T1"))
            .isEqualTo(ResendResult.Failure(AccountError.TooManyRequests))
        assertThat(api(respond(410, """{"error":"code_expired"}""")).resendCode("T1"))
            .isEqualTo(ResendResult.Failure(AccountError.CodeExpired))
    }

    @Test
    fun `usernameByEmail returns the nick only on a clean answer`() = runTest {
        val server = respond(200, """{"username":"vasya"}""")
        assertThat(api(server).usernameByEmail("v@example.com")).isEqualTo("vasya")
        assertThat(server.requests.single().url.encodedPath).endsWith("/account/login/resolve")
        assertThat(server.requests.single().bodyText()).isEqualTo("""{"email":"v@example.com"}""")
        assertThat(api(respond(404, """{"error":"not_found"}""")).usernameByEmail("v@example.com")).isNull()
        assertThat(api(failing()).usernameByEmail("v@example.com")).isNull()
    }

    @Test
    fun `startLogin tells a sent code from an account without email and from a refusal`() = runTest {
        val server = respond(200, """{"code_required":true,"ticket":"T1","resend_after":60,"username":"vasya","email_hint":"v***@example.com"}""")
        assertThat(api(server).startLogin("Vasya@Example.com", "password"))
            .isEqualTo(LoginStartResult.CodeSent("T1", 60, "vasya", "v***@example.com"))
        assertThat(server.requests.single().url.encodedPath).endsWith("/account/login/start")
        assertThat(server.requests.single().bodyText()).isEqualTo("""{"login":"Vasya@Example.com","password":"password"}""")

        assertThat(api(respond(200, """{"code_required":false,"username":"oldie"}""")).startLogin("oldie", "password"))
            .isEqualTo(LoginStartResult.NoCodeNeeded("oldie"))
        assertThat(api(respond(403, """{"error":"invalid_credentials"}""")).startLogin("v", "p"))
            .isEqualTo(LoginStartResult.Failure(AccountError.InvalidCredentials))
        assertThat(api(respond(502, """{"error":"mail_failed"}""")).startLogin("v", "p"))
            .isEqualTo(LoginStartResult.Failure(AccountError.MailFailed))
        // Код нужен, а заявки нет, или сервис недоступен — «нет связи»: приложение войдёт напрямую.
        assertThat(api(respond(200, """{"code_required":true,"username":"vasya"}""")).startLogin("v", "p"))
            .isEqualTo(LoginStartResult.Failure(AccountError.Network))
        assertThat(api(failing()).startLogin("v", "p")).isEqualTo(LoginStartResult.Failure(AccountError.Network))
    }

    @Test
    fun `confirmLogin sends the ticket and the code`() = runTest {
        val server = respond(200, """{"username":"vasya"}""")
        assertThat(api(server).confirmLogin("T1", "123456")).isEqualTo(ConfirmResult.Done("vasya"))
        assertThat(server.requests.single().url.encodedPath).endsWith("/account/login/confirm")
        assertThat(server.requests.single().bodyText()).isEqualTo("""{"ticket":"T1","code":"123456"}""")
        assertThat(api(respond(400, """{"error":"wrong_code","attempts_left":3}""")).confirmLogin("T1", "000000"))
            .isEqualTo(ConfirmResult.WrongCode(3))
    }

    @Test
    fun `redeemLoginCode returns the session only when it is complete`() = runTest {
        val server = respond(200, """{"user_id":"@v:s","device_id":"DEV","access_token":"mpt_x","homeserver_url":"https://m"}""")
        assertThat(api(server).redeemLoginCode("CODE", "Honor")).isEqualTo(RedeemLoginResult.Session("@v:s", "DEV", "mpt_x"))
        assertThat(server.requests.single().url.encodedPath).endsWith("/account/pair/redeem")
        assertThat(server.requests.single().bodyText()).isEqualTo("""{"code":"CODE","device_name":"Honor"}""")
        assertThat(api(respond(200, """{"user_id":"@v:s","device_id":"DEV"}""")).redeemLoginCode("CODE", "Honor"))
            .isEqualTo(RedeemLoginResult.Failure(AccountError.Network))
        assertThat(api(respond(410, """{"error":"code_expired"}""")).redeemLoginCode("CODE", "Honor"))
            .isEqualTo(RedeemLoginResult.Failure(AccountError.CodeExpired))
    }

    @Test
    fun `session api sends the token in the header and maps the offer`() = runTest {
        val server = respond(200, """{"code":"CODE","expires_in":120}""")
        assertThat(sessionApi(server).createLoginOffer()).isEqualTo(LoginOfferResult.Offer("CODE", 120))
        val request = server.requests.single()
        assertThat(request.header("Authorization")).isEqualTo("Bearer aToken")
        assertThat(request.url.toString()).doesNotContain("aToken")
        assertThat(request.url.encodedPath).endsWith("/account/pair/offer")
        assertThat(sessionApi(respond(429, """{"error":"too_many_requests"}""")).createLoginOffer())
            .isEqualTo(LoginOfferResult.Failure(AccountError.TooManyRequests))
    }

    @Test
    fun `session api sends nothing without a token`() = runTest {
        val server = respond(200, """{"code":"CODE","expires_in":120}""")
        val api = sessionApi(server, FakeMatrixClient(getAccessTokenResult = { Result.failure(IllegalStateException()) }))
        assertThat(api.createLoginOffer()).isEqualTo(LoginOfferResult.Failure(AccountError.Network))
        assertThat(api.loginOfferState("CODE")).isNull()
        assertThat(api.endSession("DEV")).isFalse()
        assertThat(server.requests).isEmpty()
    }

    @Test
    fun `loginOfferState maps states, unknown answers stay unknown`() = runTest {
        assertThat(sessionApi(respond(200, """{"state":"waiting"}""")).loginOfferState("C")).isEqualTo(LoginOfferState.Waiting)
        assertThat(sessionApi(respond(200, """{"state":"redeemed"}""")).loginOfferState("C")).isEqualTo(LoginOfferState.Redeemed)
        assertThat(sessionApi(respond(200, """{"state":"expired"}""")).loginOfferState("C")).isEqualTo(LoginOfferState.Expired)
        assertThat(sessionApi(respond(404, """{"error":"not_found"}""")).loginOfferState("C")).isEqualTo(LoginOfferState.Expired)
        assertThat(sessionApi(respond(502)).loginOfferState("C")).isNull()
        assertThat(sessionApi(failing()).loginOfferState("C")).isNull()
    }

    @Test
    fun `email change maps start and confirm`() = runTest {
        val start = respond(200, """{"ticket":"T3","resend_after":60}""")
        assertThat(sessionApi(start).startEmailChange("v@example.com")).isEqualTo(StartResult.Sent("T3", 60))
        assertThat(start.requests.single().bodyText()).isEqualTo("""{"email":"v@example.com"}""")
        assertThat(sessionApi(respond(409, """{"error":"email_taken"}""")).startEmailChange("v@example.com"))
            .isEqualTo(StartResult.Failure(AccountError.EmailTaken))

        assertThat(sessionApi(respond(200, """{"email":"v@example.com"}""")).confirmEmailChange("T3", "123456"))
            .isEqualTo(EmailChangeResult.Done("v@example.com"))
        assertThat(sessionApi(respond(400, """{"error":"wrong_code","attempts_left":2}""")).confirmEmailChange("T3", "0"))
            .isEqualTo(EmailChangeResult.WrongCode(2))
        assertThat(sessionApi(respond(410, """{"error":"code_expired"}""")).confirmEmailChange("T3", "0"))
            .isEqualTo(EmailChangeResult.Failure(AccountError.CodeExpired))
    }

    @Test
    fun `endSession succeeds only on 204`() = runTest {
        val server = respond(204)
        assertThat(sessionApi(server).endSession("DEV")).isTrue()
        assertThat(server.requests.single().bodyText()).isEqualTo("""{"device_id":"DEV"}""")
        assertThat(sessionApi(respond(404, """{"error":"not_found"}""")).endSession("DEV")).isFalse()
        assertThat(sessionApi(failing()).endSession("DEV")).isFalse()
    }

    @Test
    fun `listDevices marks the current device and reads the homeserver`() = runTest {
        val server = respond(
            200,
            """{"devices":[{"device_id":"A_DEVICE_ID","display_name":"Larpgram Android","last_seen_ts":1700000000000},{"device_id":"OTHER","display_name":"","last_seen_ts":0}]}""",
        )
        val client = FakeMatrixClient(deviceId = DeviceId("A_DEVICE_ID"), homeserverUrl = "https://matrix.example.org")
        assertThat(sessionApi(server, client).listDevices()).containsExactly(
            AccountDevice("A_DEVICE_ID", "Larpgram Android", 1700000000000, isCurrent = true),
            AccountDevice("OTHER", null, null, isCurrent = false),
        ).inOrder()
        assertThat(server.requests.single().url.encodedPath).isEqualTo("/_matrix/client/v3/devices")
        assertThat(server.requests.single().header("Authorization")).isEqualTo("Bearer aToken")
        assertThat(sessionApi(respond(500)).listDevices()).isNull()
        assertThat(sessionApi(respond(200, "nope")).listDevices()).isNull()
    }

    @Test
    fun `currentEmail tells apart no email and unknown`() = runTest {
        assertThat(
            sessionApi(respond(200, """{"threepids":[{"medium":"msisdn","address":"1"},{"medium":"email","address":"v@example.com"}]}""")).currentEmail()
        )
            .isEqualTo(AccountEmail.Address("v@example.com"))
        assertThat(sessionApi(respond(200, """{"threepids":[]}""")).currentEmail()).isEqualTo(AccountEmail.None)
        assertThat(sessionApi(respond(502)).currentEmail()).isEqualTo(AccountEmail.Unknown)
        assertThat(sessionApi(failing()).currentEmail()).isEqualTo(AccountEmail.Unknown)
    }

    private fun TestScope.sessionApi(
        server: InterceptingServer,
        matrixClient: FakeMatrixClient = FakeMatrixClient(homeserverUrl = "https://matrix.example.org"),
    ) = DefaultAccountSessionApi(
        matrixClient = matrixClient,
        okHttpClient = server.client,
        coroutineDispatchers = testCoroutineDispatchers(),
    )

    private fun TestScope.api(server: InterceptingServer) = DefaultAccountApi(
        okHttpClient = server.client,
        coroutineDispatchers = testCoroutineDispatchers(),
    )
}

/** Отвечает на любой запрос одним и тем же кодом и телом, запоминая запросы. */
private class InterceptingServer(private val answer: (Request) -> Response) {
    val requests = mutableListOf<Request>()
    val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            requests += chain.request()
            answer(chain.request())
        }
        .build()
}

private fun respond(code: Int, body: String = "") = InterceptingServer { request ->
    Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("")
        .body(body.toResponseBody("application/json".toMediaType()))
        .build()
}

private fun failing() = InterceptingServer { throw IOException("offline") }

private fun Request.bodyText(): String = Buffer().also { body?.writeTo(it) }.readUtf8()
