/*
 * Copyright (c) 2026 Larpgram.
 * Правка форка: присутствие пользователя («в сети», «был(а) в 14:20») для шапки чата и профилей.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.ui.presence

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.UserId
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
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone

// Robolectric: fetch разбирает ответ через org.json, у которого в android.jar только заглушки.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UserPresenceTest {
    private lateinit var defaultTimeZone: TimeZone

    @Before
    fun setUp() {
        defaultTimeZone = TimeZone.getDefault()
        // Берлин: есть перевод часов, на нём проверяем сутки длиной 23 и 25 часов.
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(defaultTimeZone)
    }

    @Test
    fun `days between counts calendar days, not 24 hour blocks`() {
        assertThat(daysBetween(at(2026, Calendar.MARCH, 10, 0, 5), at(2026, Calendar.MARCH, 10, 23, 55))).isEqualTo(0)
        // Две минуты, но уже другой день — «вчера».
        assertThat(daysBetween(at(2026, Calendar.MARCH, 10, 23, 59), at(2026, Calendar.MARCH, 11, 0, 1))).isEqualTo(1)
        assertThat(daysBetween(at(2026, Calendar.MARCH, 1, 12, 0), at(2026, Calendar.MARCH, 10, 12, 0))).isEqualTo(9)
    }

    @Test
    fun `days between survives daylight saving changes`() {
        // 29 марта 2026 в Европе переводят часы вперёд, 25 октября — назад.
        assertThat(daysBetween(at(2026, Calendar.MARCH, 28, 12, 0), at(2026, Calendar.MARCH, 29, 12, 0))).isEqualTo(1)
        assertThat(daysBetween(at(2026, Calendar.MARCH, 28, 12, 0), at(2026, Calendar.MARCH, 30, 0, 30))).isEqualTo(2)
        assertThat(daysBetween(at(2026, Calendar.OCTOBER, 24, 23, 0), at(2026, Calendar.OCTOBER, 26, 0, 30))).isEqualTo(2)
    }

    @Test
    fun `fetch reads currently_active, presence and last_active_ago`() = runTest {
        val online = fetcher(200, """{"presence":"online","last_active_ago":1000}""").fetch(A_USER)!!
        assertThat(online.isOnline).isTrue()
        val active = fetcher(200, """{"presence":"unavailable","currently_active":true}""").fetch(A_USER)!!
        assertThat(active.isOnline).isTrue()
        assertThat(active.lastActiveAtMillis).isNull()
        val before = System.currentTimeMillis()
        val away = fetcher(200, """{"presence":"offline","last_active_ago":60000}""").fetch(A_USER)!!
        assertThat(away.isOnline).isFalse()
        assertThat(away.lastActiveAtMillis!!).isAtMost(before - 60_000 + 1_000)
        assertThat(away.lastActiveAtMillis!!).isAtLeast(before - 60_000 - 5_000)
    }

    @Test
    fun `fetch is null on errors`() = runTest {
        assertThat(fetcher(403, "{}").fetch(A_USER)).isNull()
        assertThat(fetcher(200, "not json").fetch(A_USER)).isNull()
        val badServer = UserPresenceFetcher(
            matrixClient = FakeMatrixClient(homeserverUrl = "matrix.domain"),
            okHttpClient = OkHttpClient(),
            dispatchers = testCoroutineDispatchers(),
        )
        assertThat(badServer.fetch(A_USER)).isNull()
    }

    @Test
    fun `fetch escapes the user id in the path and sends the token in the header`() = runTest {
        val requests = mutableListOf<Request>()
        fetcher(200, "{}", requests).fetch(A_USER)
        val request = requests.single()
        assertThat(request.url.encodedPath).endsWith("/_matrix/client/v3/presence/%40alice%3Adomain/status")
        assertThat(request.header("Authorization")).isEqualTo("Bearer aToken")
    }

    private fun TestScope.fetcher(code: Int, body: String, requests: MutableList<Request> = mutableListOf()) = UserPresenceFetcher(
        matrixClient = FakeMatrixClient(homeserverUrl = "https://matrix.domain/"),
        okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                requests += chain.request()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build(),
        dispatchers = testCoroutineDispatchers(),
    )

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, hour, minute)
        }.timeInMillis

    private companion object {
        val A_USER = UserId("@alice:domain")
    }
}
