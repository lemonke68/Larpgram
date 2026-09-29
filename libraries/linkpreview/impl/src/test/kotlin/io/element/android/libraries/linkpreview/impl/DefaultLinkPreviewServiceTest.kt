/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.linkpreview.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.linkpreview.api.LinkPreview
import io.element.android.libraries.matrix.test.FakeClientUrlContentFetcher
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
import org.junit.Test
import java.io.IOException

class DefaultLinkPreviewServiceTest {
    private val ogBody = """
        {"og:site_name":"GitHub","og:title":"Larpgram","og:description":"Telegram-like fork",
         "og:image":"mxc://mango-kokos.ru/abc","og:image:width":1200,"og:image:height":"600"}
    """.trimIndent()

    @Test
    fun `open graph fields become a preview and the request goes to our homeserver`() = runTest {
        val server = InterceptingServer { respond(it, 200, ogBody) }
        val preview = service(server).preview("https://github.com/lemonke68/Larpgram")

        assertThat(preview).isEqualTo(
            LinkPreview(
                url = "https://github.com/lemonke68/Larpgram",
                siteName = "GitHub",
                title = "Larpgram",
                description = "Telegram-like fork",
                imageMxc = "mxc://mango-kokos.ru/abc",
                imageWidth = 1200,
                imageHeight = 600,
            )
        )
        val request = server.requests.single()
        assertThat(request.url.host).isEqualTo("matrix.example.org")
        assertThat(request.url.encodedPath).isEqualTo("/_matrix/client/v1/media/preview_url")
        assertThat(request.url.queryParameter("url")).isEqualTo("https://github.com/lemonke68/Larpgram")
        assertThat(request.header("Authorization")).startsWith("Bearer ")
    }

    @Test
    fun `answers are cached, including no preview`() = runTest {
        val server = InterceptingServer { respond(it, 200, "{}") }
        val service = service(server)

        assertThat(service.preview("https://a.org")).isNull()
        assertThat(service.preview("https://a.org")).isNull()
        assertThat(server.requests).hasSize(1)
    }

    @Test
    fun `network failures and server errors are retried next time`() = runTest {
        var fail = true
        val server = InterceptingServer { if (fail) throw IOException("offline") else respond(it, 200, ogBody) }
        val service = service(server)

        assertThat(service.preview("https://a.org")).isNull()
        fail = false
        assertThat(service.preview("https://a.org")?.title).isEqualTo("Larpgram")

        val errors = InterceptingServer { respond(it, 502, "") }
        val erroring = service(errors)
        erroring.preview("https://a.org")
        erroring.preview("https://a.org")
        assertThat(errors.requests).hasSize(2)
    }

    @Test
    fun `a page the server refuses is not asked again`() = runTest {
        val server = InterceptingServer { respond(it, 404, """{"errcode":"M_UNKNOWN"}""") }
        val service = service(server)

        service.preview("https://a.org")
        service.preview("https://a.org")
        assertThat(server.requests).hasSize(1)
    }

    private fun TestScope.service(server: InterceptingServer) = DefaultLinkPreviewService(
        matrixClient = FakeMatrixClient(),
        clientUrlContentFetcher = FakeClientUrlContentFetcher(homeserverUrl = "https://matrix.example.org/"),
        okHttpClient = server.client,
        coroutineDispatchers = testCoroutineDispatchers(),
    )
}

private class InterceptingServer(private val answer: (Request) -> Response) {
    val requests = mutableListOf<Request>()
    val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            requests += chain.request()
            answer(chain.request())
        }
        .build()
}

private fun respond(request: Request, code: Int, body: String): Response = Response.Builder()
    .request(request)
    .protocol(Protocol.HTTP_1_1)
    .code(code)
    .message("")
    .body(body.toResponseBody("application/json".toMediaType()))
    .build()
