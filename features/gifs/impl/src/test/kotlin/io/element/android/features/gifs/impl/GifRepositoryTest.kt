/*
 * Модуль форка: гифки через свой прокси к Tenor.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.gifs.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test

class GifRepositoryTest {
    private fun TestScope.createRepository(server: MockWebServer) = GifRepository(
        okHttpClient = OkHttpClient(),
        coroutineDispatchers = testCoroutineDispatchers(useUnconfinedTestDispatcher = true),
        baseUrl = server.url("/").toString().trimEnd('/'),
    )

    @Test
    fun `search - query goes to the search endpoint`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "next": "20",
                  "results": [
                    {
                      "id": "1",
                      "description": "кот печатает",
                      "url": "https://media.tenor.com/full.gif",
                      "previewUrl": "https://media.tenor.com/tiny.gif",
                      "width": 320, "height": 240, "size": 123456
                    }
                  ]
                }
                """.trimIndent()
            )
        )
        server.start()

        try {
            val page = createRepository(server).search("кот").getOrThrow()

            val request = server.takeRequest()
            assertThat(request.path).startsWith("/search")
            assertThat(request.requestUrl?.queryParameter("q")).isEqualTo("кот")
            assertThat(page.next).isEqualTo("20")
            assertThat(page.gifs).hasSize(1)
            with(page.gifs.first()) {
                assertThat(url).isEqualTo("https://media.tenor.com/full.gif")
                assertThat(previewUrl).isEqualTo("https://media.tenor.com/tiny.gif")
                assertThat(width).isEqualTo(320)
            }
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `search - empty query asks for the featured selection`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{ "results": [] }"""))
        server.start()

        try {
            createRepository(server).search("  ").getOrThrow()

            assertThat(server.takeRequest().path).startsWith("/featured")
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `search - passes the cursor for the next page`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{ "results": [] }"""))
        server.start()

        try {
            createRepository(server).search("кот", pos = "20").getOrThrow()

            assertThat(server.takeRequest().requestUrl?.queryParameter("pos")).isEqualTo("20")
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `search - entries without links are dropped`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "results": [
                    { "id": "good", "url": "https://a/full.gif", "previewUrl": "https://a/tiny.gif" },
                    { "id": "noUrl", "previewUrl": "https://a/tiny.gif" },
                    { "id": "noPreview", "url": "https://a/full.gif" }
                  ]
                }
                """.trimIndent()
            )
        )
        server.start()

        try {
            val page = createRepository(server).search("кот").getOrThrow()

            assertThat(page.gifs.map { it.id }).containsExactly("good")
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `search - proxy error becomes a failure, not a crash`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{ "error": "no_api_key" }"""))
        server.start()

        try {
            assertThat(createRepository(server).search("кот").isFailure).isTrue()
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `search - unknown fields in the answer do not break parsing`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                """{ "results": [ { "id": "1", "url": "https://a/f.gif", "previewUrl": "https://a/t.gif", "future": 1 } ], "extra": true }"""
            )
        )
        server.start()

        try {
            assertThat(createRepository(server).search("кот").getOrThrow().gifs).hasSize(1)
        } finally {
            server.shutdown()
        }
    }
}
