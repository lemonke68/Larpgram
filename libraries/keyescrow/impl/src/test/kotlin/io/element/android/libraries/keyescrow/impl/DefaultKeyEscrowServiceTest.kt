/*
 * Copyright (c) 2026 Larpgram.
 * Модуль форка: депонирование ключа восстановления (вход по коду с почты).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.keyescrow.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.keyescrow.api.RedeemResult
import io.element.android.libraries.keyescrow.api.RequestCodeResult
import io.element.android.libraries.matrix.api.core.RoomId
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
 * Коды ответа escrow-сервиса → результаты. Сервер отвечает перехватчиком OkHttp, сети нет.
 * Важнее всего, чтобы «непонятно» (5xx, 401, обрыв) не превращалось в «ключа нет»: иначе
 * бэкфилл перезапишет ключ или покажет человеку неверную ошибку.
 */
class DefaultKeyEscrowServiceTest {
    @Test
    fun `hasStoredKey maps 200 and 404, anything else is unknown`() = runTest {
        assertThat(service(respond(200)).hasStoredKey()).isTrue()
        assertThat(service(respond(404)).hasStoredKey()).isFalse()
        assertThat(service(respond(401)).hasStoredKey()).isNull()
        assertThat(service(respond(500)).hasStoredKey()).isNull()
        assertThat(service(failing()).hasStoredKey()).isNull()
    }

    @Test
    fun `hasStoredKey is unknown without an access token and sends nothing`() = runTest {
        val server = respond(200)
        val service = service(server, FakeMatrixClient(getAccessTokenResult = { Result.failure(IllegalStateException()) }))
        assertThat(service.hasStoredKey()).isNull()
        assertThat(server.requests).isEmpty()
    }

    @Test
    fun `requests carry the token in the header only`() = runTest {
        val server = respond(200)
        service(server).hasStoredKey()
        val request = server.requests.single()
        assertThat(request.header("Authorization")).isEqualTo("Bearer aToken")
        assertThat(request.url.toString()).doesNotContain("aToken")
        assertThat(request.url.encodedPath).endsWith("/escrow/key")
    }

    @Test
    fun `store succeeds only on a 2xx and sends the key as json`() = runTest {
        val server = respond(204)
        assertThat(service(server).store("KEY").isSuccess).isTrue()
        val request = server.requests.single()
        assertThat(request.method).isEqualTo("PUT")
        assertThat(request.bodyText()).isEqualTo("""{"recovery_key":"KEY"}""")
        assertThat(service(respond(400)).store("KEY").isFailure).isTrue()
        assertThat(service(failing()).store("KEY").isFailure).isTrue()
    }

    @Test
    fun `deleteStoredKey sends DELETE and reports success`() = runTest {
        val server = respond(200)
        assertThat(service(server).deleteStoredKey()).isTrue()
        assertThat(server.requests.single().method).isEqualTo("DELETE")
        assertThat(service(respond(500)).deleteStoredKey()).isFalse()
    }

    @Test
    fun `requestCode maps status codes`() = runTest {
        assertThat(service(respond(200, """{"masked_email":"i***@gmail.com"}""")).requestCode())
            .isEqualTo(RequestCodeResult.Sent("i***@gmail.com"))
        // Тело не разобралось — код всё равно отправлен, просто без маски.
        assertThat(service(respond(200, "nope")).requestCode()).isEqualTo(RequestCodeResult.Sent(""))
        assertThat(service(respond(404)).requestCode()).isEqualTo(RequestCodeResult.NoEmail)
        assertThat(service(respond(429)).requestCode()).isEqualTo(RequestCodeResult.RateLimited)
        assertThat(service(respond(500)).requestCode()).isEqualTo(RequestCodeResult.NetworkError)
        assertThat(service(failing()).requestCode()).isEqualTo(RequestCodeResult.NetworkError)
    }

    @Test
    fun `redeemCode maps status codes`() = runTest {
        assertThat(service(respond(200, """{"recovery_key":"KEY"}""")).redeemCode("123456"))
            .isEqualTo(RedeemResult.Success("KEY"))
        // 200 без ключа — не успех: пустой ключ сломал бы восстановление.
        assertThat(service(respond(200, """{"recovery_key":""}""")).redeemCode("123456")).isEqualTo(RedeemResult.NetworkError)
        assertThat(service(respond(400, """{"attempts_left":2}""")).redeemCode("000000"))
            .isEqualTo(RedeemResult.InvalidCode(attemptsLeft = 2))
        assertThat(service(respond(400)).redeemCode("000000")).isEqualTo(RedeemResult.InvalidCode(attemptsLeft = null))
        assertThat(service(respond(404)).redeemCode("123456")).isEqualTo(RedeemResult.NoStoredKey)
        assertThat(service(respond(410)).redeemCode("123456")).isEqualTo(RedeemResult.Expired)
        assertThat(service(respond(429)).redeemCode("123456")).isEqualTo(RedeemResult.TooManyAttempts)
        assertThat(service(respond(502)).redeemCode("123456")).isEqualTo(RedeemResult.NetworkError)
    }

    @Test
    fun `fetchSessionKey returns the key only on 200 with a non blank key`() = runTest {
        assertThat(service(respond(200, """{"recovery_key":"KEY"}""")).fetchSessionKey()).isEqualTo("KEY")
        assertThat(service(respond(200, """{"recovery_key":" "}""")).fetchSessionKey()).isNull()
        assertThat(service(respond(403)).fetchSessionKey()).isNull()
    }

    @Test
    fun `deleteDmForBoth is accepted only on 202`() = runTest {
        val server = respond(202)
        assertThat(service(server).deleteDmForBoth(RoomId("!dm:domain"))).isTrue()
        assertThat(server.requests.single().bodyText()).isEqualTo("""{"room_id":"!dm:domain"}""")
        assertThat(service(respond(200)).deleteDmForBoth(RoomId("!dm:domain"))).isFalse()
        assertThat(service(respond(409)).deleteDmForBoth(RoomId("!dm:domain"))).isFalse()
    }

    private fun TestScope.service(
        server: FakeServer,
        matrixClient: FakeMatrixClient = FakeMatrixClient(),
    ) = DefaultKeyEscrowService(
        matrixClient = matrixClient,
        okHttpClient = server.client,
        coroutineDispatchers = testCoroutineDispatchers(),
    )
}

/** Отвечает на любой запрос одним и тем же кодом и телом, запоминая запросы. */
internal class FakeServer(private val answer: (Request) -> Response) {
    val requests = mutableListOf<Request>()
    val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            requests += chain.request()
            answer(chain.request())
        }
        .build()
}

internal fun respond(code: Int, body: String = "") = FakeServer { request ->
    Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("")
        .body(body.toResponseBody("application/json".toMediaType()))
        .build()
}

internal fun failing() = FakeServer { throw IOException("offline") }

internal fun Request.bodyText(): String = Buffer().also { body?.writeTo(it) }.readUtf8()
