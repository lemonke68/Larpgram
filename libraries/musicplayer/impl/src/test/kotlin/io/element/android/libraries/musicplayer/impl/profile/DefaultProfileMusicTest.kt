/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.musicplayer.impl.profile

import android.content.Context
import androidx.core.content.edit
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.FakeMatrixClientProvider
import io.element.android.libraries.musicplayer.api.toMusicTrack
import io.element.android.libraries.musicplayer.impl.MusicOfflineStore
import io.element.android.libraries.musicplayer.test.aMusicTrack
import io.element.android.libraries.musicplayer.test.aProfileMusicTrack
import io.element.android.tests.testutils.robolectric.RobolectricTest
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
import org.robolectric.RuntimeEnvironment
import java.io.File

class DefaultProfileMusicTest : RobolectricTest() {
    private val me = UserId("@me:hs.test")
    private val existing = aProfileMusicTrack(url = "mxc://hs.test/old", title = "Old")

    @Test
    fun `reads another user's tracks and treats a missing field as empty`() = runTest {
        val server = TestHomeserver { request ->
            if ("bob" in request.url.toString()) 200 to fieldBody(existing) else 404 to """{"errcode":"M_NOT_FOUND"}"""
        }
        val profileMusic = profileMusic(server)
        assertThat(profileMusic.tracks(UserId("@bob:hs.test")).getOrThrow()).containsExactly(existing)
        assertThat(profileMusic.tracks(UserId("@carol:hs.test")).getOrThrow()).isEmpty()
        assertThat(server.requests.first().url.encodedPath).isEqualTo("/_matrix/client/v3/profile/%40bob%3Ahs.test/ru.mangokokos.music")
        assertThat(server.requests.first().header("Authorization")).isEqualTo("Bearer aToken")
    }

    @Test
    fun `adding a profile track reads the field, puts it on top and writes it back`() = runTest {
        val server = TestHomeserver { request -> if (request.method == "GET") 200 to fieldBody(existing) else 200 to "{}" }
        val profileMusic = profileMusic(server)
        val someonesTrack = aProfileMusicTrack(url = "mxc://other.test/song", title = "Song").toMusicTrack(me)
        profileMusic.add(someonesTrack, title = "Song", artist = "Artist").getOrThrow()

        assertThat(server.requests.map { it.method }).containsExactly("GET", "PUT").inOrder()
        val written = ProfileMusicCodec.decodeResponse(server.requests.last().bodyText())
        assertThat(written.map { it.url }).containsExactly("mxc://other.test/song", "mxc://hs.test/old").inOrder()
        assertThat(profileMusic.ownTracks.value?.map { it.url }).containsExactly("mxc://other.test/song", "mxc://hs.test/old").inOrder()
        assertThat(profileMusic.isInOwnProfile(someonesTrack)).isTrue()
    }

    @Test
    fun `adding a chat track uploads an open copy and remembers its source`() = runTest {
        val server = TestHomeserver { request ->
            when (request.method) {
                "GET" -> 404 to "{}"
                "POST" -> 200 to """{"content_uri":"mxc://hs.test/copy"}"""
                else -> 200 to "{}"
            }
        }
        val chatTrack = aMusicTrack(id = "\$event", filename = "Band - Song.mp3")
        val offlineStore = offlineStoreWith(chatTrack.mediaKey, bytes = "music".toByteArray())
        val profileMusic = profileMusic(server, offlineStore = offlineStore)
        profileMusic.add(chatTrack, title = "Song", artist = "Band").getOrThrow()

        val upload = server.requests.single { it.method == "POST" }
        assertThat(upload.url.encodedPath).isEqualTo("/_matrix/media/v3/upload")
        assertThat(upload.url.queryParameter("filename")).isEqualTo("Band - Song.mp3")
        assertThat(upload.bodyText()).isEqualTo("music")
        val written = ProfileMusicCodec.decodeResponse(server.requests.last().bodyText()).single()
        assertThat(written.url).isEqualTo("mxc://hs.test/copy")
        assertThat(written.origin).isEqualTo(ProfileMusicCodec.originOf(chatTrack))
        // Тот же трек из чата теперь узнаётся как уже добавленный.
        assertThat(profileMusic.isInOwnProfile(chatTrack)).isTrue()
    }

    @Test
    fun `removing the last track deletes the field`() = runTest {
        val server = TestHomeserver { request -> if (request.method == "GET") 200 to fieldBody(existing) else 200 to "{}" }
        val profileMusic = profileMusic(server)
        profileMusic.remove(existing.toMusicTrack(me)).getOrThrow()
        assertThat(server.requests.map { it.method }).containsExactly("GET", "DELETE").inOrder()
        assertThat(profileMusic.ownTracks.value).isEmpty()
    }

    @Test
    fun `a server that rejects a list gets the same JSON as a string`() = runTest {
        var puts = 0
        val server = TestHomeserver { request ->
            when (request.method) {
                "GET" -> 404 to "{}"
                else -> if (puts++ == 0) 400 to """{"errcode":"M_INVALID_PARAM"}""" else 200 to "{}"
            }
        }
        val profileMusic = profileMusic(server)
        profileMusic.add(existing.toMusicTrack(me), title = "Old", artist = null).getOrThrow()
        val retried = server.requests.last().bodyText()
        assertThat(retried).startsWith("""{"ru.mangokokos.music":"[""")
        assertThat(ProfileMusicCodec.decodeResponse(retried).map { it.url }).containsExactly("mxc://hs.test/old")
    }

    @Test
    fun `a server error fails the change and keeps the cached list`() = runTest {
        val server = TestHomeserver { 500 to "{}" }
        val profileMusic = profileMusic(server)
        assertThat(profileMusic.add(existing.toMusicTrack(me), title = "Old", artist = null).isFailure).isTrue()
        assertThat(profileMusic.ownTracks.value).isNull()
    }

    private fun TestScope.profileMusic(
        server: TestHomeserver,
        offlineStore: MusicOfflineStore = offlineStoreWith(null, ByteArray(0)),
    ) = DefaultProfileMusic(
        matrixClient = FakeMatrixClient(
            sessionId = me,
            homeserverUrl = "https://hs.test",
            getMaxUploadSizeResult = { Result.success(100_000_000L) },
        ),
        okHttpClient = server.client,
        offlineStore = offlineStore,
        dispatchers = testCoroutineDispatchers(),
    )

    /** Трек «сохранён на устройстве» — так загрузка обходится без скачивания. */
    private fun TestScope.offlineStoreWith(mediaKey: String?, bytes: ByteArray): MusicOfflineStore {
        val context = RuntimeEnvironment.getApplication()
        if (mediaKey != null) {
            val dir = File(context.filesDir, "music_offline").apply { mkdirs() }
            File(dir, "saved.mp3").writeBytes(bytes)
            context.getSharedPreferences("larpgram_music_offline", Context.MODE_PRIVATE).edit { putString(mediaKey, "saved.mp3") }
        }
        return MusicOfflineStore(context, FakeMatrixClientProvider(), testCoroutineDispatchers(), backgroundScope)
    }

    private fun fieldBody(vararg tracks: io.element.android.libraries.musicplayer.api.ProfileMusicTrack) =
        ProfileMusicCodec.encodeBody(ProfileMusicCodec.encodeValue(tracks.toList()))
}

/** Сервер в перехватчике OkHttp: отвечает кодом и телом от [answer], запоминает запросы. */
private class TestHomeserver(private val answer: (Request) -> Pair<Int, String>) {
    val requests = mutableListOf<Request>()
    val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
            requests += request
            val (code, body) = answer(request)
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }
        .build()
}

private fun Request.bodyText(): String = Buffer().also { body?.writeTo(it) }.readUtf8()
