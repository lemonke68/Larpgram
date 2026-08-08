/*
 * Модуль форка: стикер-паки по MSC2545 (im.ponies image packs).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.imagepacks.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.imagepacks.api.ImagePackEventTypes
import io.element.android.libraries.imagepacks.api.ImagePackId
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import io.element.android.libraries.imagepacks.api.ImagePack
import io.element.android.libraries.imagepacks.api.ImagePackImage
import io.element.android.libraries.imagepacks.api.ImagePackUsage
import org.junit.Test

class DefaultImagePackSourceTest {
    private fun TestScope.createSource(
        matrixClient: FakeMatrixClient,
    ) = DefaultImagePackSource(
        matrixClient = matrixClient,
        okHttpClient = OkHttpClient(),
        coroutineDispatchers = testCoroutineDispatchers(useUnconfinedTestDispatcher = true),
    )

    @Test
    fun `getUserPacks - reads the pack from account data`() = runTest {
        val source = createSource(
            FakeMatrixClient(
                getAccountDataResult = { eventType ->
                    assertThat(eventType).isEqualTo(ImagePackEventTypes.USER_EMOTES)
                    Result.success(
                        """{ "images": { "cat": { "url": "mxc://mango-kokos.ru/cat" } }, "pack": { "display_name": "Коты" } }"""
                    )
                },
            ),
        )

        val packs = source.getUserPacks()

        assertThat(packs).hasSize(1)
        assertThat(packs.first().id).isEqualTo(ImagePackId.User)
        assertThat(packs.first().displayName).isEqualTo("Коты")
    }

    @Test
    fun `getUserPacks - no account data means no packs`() = runTest {
        val source = createSource(FakeMatrixClient(getAccountDataResult = { Result.success(null) }))

        assertThat(source.getUserPacks()).isEmpty()
    }

    @Test
    fun `getUserPacks - a failing request does not blow up the picker`() = runTest {
        val source = createSource(
            FakeMatrixClient(
                getAccountDataResult = { Result.failure(IllegalStateException("сеть отвалилась")) },
            ),
        )

        assertThat(source.getUserPacks()).isEmpty()
    }

    @Test
    fun `getEmoteRooms - reads the room list from account data`() = runTest {
        val source = createSource(
            FakeMatrixClient(
                getAccountDataResult = { eventType ->
                    assertThat(eventType).isEqualTo(ImagePackEventTypes.EMOTE_ROOMS)
                    Result.success("""{ "rooms": { "!room:mango-kokos.ru": { "": {} } } }""")
                },
            ),
        )

        assertThat(source.getEmoteRooms()).containsExactly("!room:mango-kokos.ru" to "")
    }

    @Test
    fun `getRoomPack - sends the token and asks the state event endpoint`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                """{ "images": { "cat": { "url": "mxc://mango-kokos.ru/cat" } }, "pack": { "display_name": "Комнатный" } }"""
            )
        )
        server.start()

        try {
            val source = createSource(
                FakeMatrixClient(
                    homeserverUrl = server.url("/").toString(),
                    getAccessTokenResult = { Result.success("aToken") },
                ),
            )

            val pack = source.getRoomPack("!room:mango-kokos.ru", "коты")

            val request = server.takeRequest()
            // Без заголовка сервер ответил бы 401, как выяснилось с getUrl из SDK.
            assertThat(request.getHeader("Authorization")).isEqualTo("Bearer aToken")
            assertThat(request.path).isEqualTo(
                "/_matrix/client/v3/rooms/%21room%3Amango-kokos.ru/state/im.ponies.room_emotes/%D0%BA%D0%BE%D1%82%D1%8B"
            )
            assertThat(pack!!.displayName).isEqualTo("Комнатный")
            assertThat(pack.id).isEqualTo(ImagePackId.Room("!room:mango-kokos.ru", "коты"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `getRoomPack - a room without a pack is not an error`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{ "errcode": "M_NOT_FOUND" }"""))
        server.start()

        try {
            val source = createSource(FakeMatrixClient(homeserverUrl = server.url("/").toString()))

            assertThat(source.getRoomPack("!room:mango-kokos.ru", "")).isNull()
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `getRoomPack - without a token we do not even try`() = runTest {
        val server = MockWebServer()
        server.start()

        try {
            val source = createSource(
                FakeMatrixClient(
                    homeserverUrl = server.url("/").toString(),
                    getAccessTokenResult = { Result.failure(IllegalStateException("нет сессии")) },
                ),
            )

            assertThat(source.getRoomPack("!room:mango-kokos.ru", "")).isNull()
            assertThat(server.requestCount).isEqualTo(0)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `savePack - writes the pack into our own account data`() = runTest {
        var written: Pair<String, String>? = null
        val source = createSource(
            FakeMatrixClient(
                getAccountDataResult = { Result.success(null) },
                setAccountDataResult = { type, content ->
                    written = type to content
                    Result.success(Unit)
                },
            ),
        )

        val ok = source.savePack(aPack(slug = "ryazan", displayName = "Рязань"))

        assertThat(ok).isTrue()
        assertThat(written!!.first).isEqualTo(ImagePackEventTypes.SAVED_PACKS)
        assertThat(written!!.second).contains("ryazan")
        assertThat(written!!.second).contains("Рязань")
    }

    @Test
    fun `savePack - replaces a pack with the same slug instead of duplicating it`() = runTest {
        var written: String? = null
        val source = createSource(
            FakeMatrixClient(
                getAccountDataResult = { type ->
                    if (type == ImagePackEventTypes.SAVED_PACKS) {
                        Result.success(
                            """{"packs":[{"slug":"ryazan","pack":{"display_name":"Старое"},"images":{"a":{"url":"mxc://mango-kokos.ru/a"}}}]}"""
                        )
                    } else {
                        Result.success(null)
                    }
                },
                setAccountDataResult = { _, content ->
                    written = content
                    Result.success(Unit)
                },
            ),
        )

        source.savePack(aPack(slug = "ryazan", displayName = "Новое"))

        assertThat(written).contains("Новое")
        assertThat(written).doesNotContain("Старое")
        // Пак с тем же slug должен остаться один.
        assertThat(written!!.split("\"slug\"").size - 1).isEqualTo(1)
    }

    @Test
    fun `removeSavedPack - removes only the requested pack`() = runTest {
        var written: String? = null
        val source = createSource(
            FakeMatrixClient(
                getAccountDataResult = { type ->
                    if (type == ImagePackEventTypes.SAVED_PACKS) {
                        Result.success(
                            """{"packs":[
                                {"slug":"one","images":{"a":{"url":"mxc://mango-kokos.ru/a"}}},
                                {"slug":"two","images":{"b":{"url":"mxc://mango-kokos.ru/b"}}}
                            ]}"""
                        )
                    } else {
                        Result.success(null)
                    }
                },
                setAccountDataResult = { _, content ->
                    written = content
                    Result.success(Unit)
                },
            ),
        )

        assertThat(source.removeSavedPack("one")).isTrue()
        assertThat(written).contains("two")
        assertThat(written).doesNotContain("one")
    }

    @Test
    fun `removeSavedPack - unknown slug changes nothing`() = runTest {
        var wrote = false
        val source = createSource(
            FakeMatrixClient(
                getAccountDataResult = { Result.success(null) },
                setAccountDataResult = { _, _ ->
                    wrote = true
                    Result.success(Unit)
                },
            ),
        )

        assertThat(source.removeSavedPack("нет такого")).isFalse()
        assertThat(wrote).isFalse()
    }

    @Test
    fun `getSavedPacks - survives a saved pack without slug`() = runTest {
        val source = createSource(
            FakeMatrixClient(
                getAccountDataResult = {
                    Result.success(
                        """{"packs":[
                            {"images":{"a":{"url":"mxc://mango-kokos.ru/a"}}},
                            {"slug":"ok","images":{"b":{"url":"mxc://mango-kokos.ru/b"}}}
                        ]}"""
                    )
                },
            ),
        )

        // Пак без slug нельзя ни обновить, ни удалить, поэтому он отбрасывается.
        assertThat(source.getSavedPacks().map { (it.id as ImagePackId.Saved).slug })
            .containsExactly("ok")
    }

    @Test
    fun `getAllPacks - user pack goes first, then room packs`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                """{ "images": { "b": { "url": "mxc://mango-kokos.ru/b" } }, "pack": { "display_name": "Комнатный" } }"""
            )
        )
        server.start()

        try {
            val source = createSource(
                FakeMatrixClient(
                    homeserverUrl = server.url("/").toString(),
                    getAccessTokenResult = { Result.success("aToken") },
                    getAccountDataResult = { eventType ->
                        when (eventType) {
                            ImagePackEventTypes.USER_EMOTES -> Result.success(
                                """{ "images": { "a": { "url": "mxc://mango-kokos.ru/a" } }, "pack": { "display_name": "Личный" } }"""
                            )
                            ImagePackEventTypes.EMOTE_ROOMS -> Result.success(
                                """{ "rooms": { "!room:mango-kokos.ru": { "": {} } } }"""
                            )
                            else -> Result.success(null)
                        }
                    },
                ),
            )

            assertThat(source.getAllPacks().map { it.displayName })
                .containsExactly("Личный", "Комнатный")
                .inOrder()
        } finally {
            server.shutdown()
        }
    }
}

private fun aPack(
    slug: String,
    displayName: String,
) = ImagePack(
    id = ImagePackId.Saved(slug),
    displayName = displayName,
    avatarUrl = null,
    usages = setOf(ImagePackUsage.STICKER),
    attribution = null,
    images = listOf(
        ImagePackImage(
            shortcode = "a",
            url = "mxc://mango-kokos.ru/a",
            body = "картинка",
            usages = setOf(ImagePackUsage.STICKER),
            mimeType = "image/png",
            width = 256,
            height = 256,
            size = 1024,
        )
    ),
)
