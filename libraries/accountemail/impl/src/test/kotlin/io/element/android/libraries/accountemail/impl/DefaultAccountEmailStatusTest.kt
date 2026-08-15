/*
 * Модуль форка: почта аккаунта (проверка привязки и баннер-напоминание).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.accountemail.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test

class DefaultAccountEmailStatusTest {
    private fun TestScope.createStatus(
        matrixClient: FakeMatrixClient,
    ) = DefaultAccountEmailStatus(
        matrixClient = matrixClient,
        okHttpClient = OkHttpClient(),
        coroutineDispatchers = testCoroutineDispatchers(useUnconfinedTestDispatcher = true),
    )

    @Test
    fun `hasEmail - an email in the response means the account is covered`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                """{ "threepids": [ { "medium": "email", "address": "kot@mango-kokos.ru" } ] }"""
            )
        )
        server.start()

        try {
            val status = createStatus(FakeMatrixClient(homeserverUrl = server.url("/").toString()))

            assertThat(status.hasEmail()).isTrue()
            val request = server.takeRequest()
            assertThat(request.path).isEqualTo("/_matrix/client/v3/account/3pid")
            assertThat(request.getHeader("Authorization")).isEqualTo("Bearer aToken")
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `hasEmail - an empty list means no email`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{ "threepids": [] }"""))
        server.start()

        try {
            val status = createStatus(FakeMatrixClient(homeserverUrl = server.url("/").toString()))

            assertThat(status.hasEmail()).isFalse()
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `hasEmail - a phone number is not an email`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody("""{ "threepids": [ { "medium": "msisdn", "address": "79990000000" } ] }""")
        )
        server.start()

        try {
            val status = createStatus(FakeMatrixClient(homeserverUrl = server.url("/").toString()))

            assertThat(status.hasEmail()).isFalse()
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `hasEmail - a failing server is not the same as no email`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(500))
        server.start()

        try {
            val status = createStatus(FakeMatrixClient(homeserverUrl = server.url("/").toString()))

            assertThat(status.hasEmail()).isNull()
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `hasEmail - nonsense in the response is not the same as no email`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("это не json"))
        server.start()

        try {
            val status = createStatus(FakeMatrixClient(homeserverUrl = server.url("/").toString()))

            assertThat(status.hasEmail()).isNull()
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `hasEmail - without a token we do not even ask`() = runTest {
        val server = MockWebServer()
        server.start()

        try {
            val status = createStatus(
                FakeMatrixClient(
                    homeserverUrl = server.url("/").toString(),
                    getAccessTokenResult = { Result.failure(IllegalStateException("нет сессии")) },
                ),
            )

            assertThat(status.hasEmail()).isNull()
            assertThat(server.requestCount).isEqualTo(0)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `isBannerHidden - no account data means the banner is shown`() = runTest {
        val status = createStatus(FakeMatrixClient(getAccountDataResult = { Result.success(null) }))

        assertThat(status.isBannerHidden()).isFalse()
    }

    @Test
    fun `isBannerHidden - a future date hides the banner`() = runTest {
        val future = System.currentTimeMillis() + 60_000
        val status = createStatus(
            FakeMatrixClient(getAccountDataResult = { Result.success("""{ "hidden_until": $future }""") }),
        )

        assertThat(status.isBannerHidden()).isTrue()
    }

    @Test
    fun `isBannerHidden - the silence expires`() = runTest {
        val past = System.currentTimeMillis() - 60_000
        val status = createStatus(
            FakeMatrixClient(getAccountDataResult = { Result.success("""{ "hidden_until": $past }""") }),
        )

        assertThat(status.isBannerHidden()).isFalse()
    }

    @Test
    fun `isBannerHidden - garbage in account data does not hide the banner forever`() = runTest {
        val status = createStatus(
            FakeMatrixClient(getAccountDataResult = { Result.success("мусор") }),
        )

        assertThat(status.isBannerHidden()).isFalse()
    }

    @Test
    fun `hideBanner - writes a date in the future`() = runTest {
        var written: Pair<String, String>? = null
        val status = createStatus(
            FakeMatrixClient(
                setAccountDataResult = { eventType, content ->
                    written = eventType to content
                    Result.success(Unit)
                },
            ),
        )

        status.hideBanner()

        val (eventType, content) = written!!
        assertThat(eventType).isEqualTo("ru.mangokokos.larpgram.email_banner")
        val hiddenUntil = Regex("\"hidden_until\":(\\d+)").find(content)!!.groupValues[1].toLong()
        assertThat(hiddenUntil).isGreaterThan(System.currentTimeMillis())
    }
}
