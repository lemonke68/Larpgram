/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.forward

import com.google.common.truth.Truth.assertThat
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class LarpgramSharingApiTest {
    @Test
    fun `absent preference permits a link but forbidden and invalid responses do not`() {
        assertThat(api(404).profileLink(BASE, TOKEN, USER)).isTrue()
        assertThat(api(200, """{"${LarpgramSharingApi.PROFILE_FIELD}":false}""").profileLink(BASE, TOKEN, USER)).isFalse()
        assertThat(api(200, """{"${LarpgramSharingApi.PROFILE_FIELD}":"false"}""").profileLink(BASE, TOKEN, USER)).isFalse()
        assertThrows(IOException::class.java) { api(403).profileLink(BASE, TOKEN, USER) }
        assertThrows(IOException::class.java) { api(200, "{}").profileLink(BASE, TOKEN, USER) }
        assertThrows(IOException::class.java) { api(200, """{"${LarpgramSharingApi.PROFILE_FIELD}":"perhaps"}""").profileLink(BASE, TOKEN, USER) }
    }

    @Test
    fun `policy read uses authenticated room state endpoint and distinguishes missing from unreadable`() {
        val requests = mutableListOf<Request>()
        assertThat(api(200, """{"enabled":true}""", requests).copyProtection(BASE, TOKEN, "!channel:hs.test")).isTrue()
        val request = requests.single()
        assertThat(request.url.pathSegments).containsExactly(
            "_matrix",
            "client",
            "v3",
            "rooms",
            "!channel:hs.test",
            "state",
            LarpgramSharingApi.PROTECTION_EVENT,
            "",
        ).inOrder()
        assertThat(request.header("Authorization")).isEqualTo("Bearer $TOKEN")
        assertThat(api(404).copyProtection(BASE, TOKEN, "!channel:hs.test")).isFalse()
        assertThrows(IOException::class.java) { api(500).copyProtection(BASE, TOKEN, "!channel:hs.test") }
    }

    @Test
    fun `privacy writes its own field only and reports failure`() {
        val requests = mutableListOf<Request>()
        api(200, "{}", requests).setProfileLink(BASE, TOKEN, USER, allowed = false)
        val request = requests.single()
        assertThat(request.method).isEqualTo("PUT")
        assertThat(request.url.pathSegments.last()).isEqualTo(LarpgramSharingApi.PROFILE_FIELD)
        assertThat(request.url.pathSegments[4]).isEqualTo(USER)
        assertThat(Buffer().also { request.body!!.writeTo(it) }.readUtf8())
            .isEqualTo("""{"${LarpgramSharingApi.PROFILE_FIELD}":"false"}""")
        assertThrows(IOException::class.java) { api(403).setProfileLink(BASE, TOKEN, USER, true) }
    }

    private fun api(code: Int, body: String = "{}", requests: MutableList<Request> = mutableListOf()) = LarpgramSharingApi(
        OkHttpClient.Builder().addInterceptor { chain ->
            requests.add(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("test")
                .body(body.toResponseBody()).build()
        }.build()
    )

    companion object {
        private const val BASE = "https://hs.test"
        private const val TOKEN = "testToken"
        private const val USER = "@me:hs.test"
    }
}
