/*
 * Модуль форка: стикер-паки по MSC2545 (im.ponies image packs).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.imagepacks.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.imagepacks.api.ImagePackId
import io.element.android.libraries.imagepacks.api.ImagePackUsage
import org.junit.Test

class ImagePackParserTest {
    private val parser = ImagePackParser()

    @Test
    fun `parse - full pack`() {
        val pack = parser.parse(
            rawContent = """
                {
                  "images": {
                    "cat": {
                      "url": "mxc://mango-kokos.ru/cat",
                      "body": "Кот",
                      "usage": ["sticker"],
                      "info": { "mimetype": "image/webp", "w": 512, "h": 512, "size": 12345 }
                    }
                  },
                  "pack": {
                    "display_name": "Коты",
                    "avatar_url": "mxc://mango-kokos.ru/avatar",
                    "usage": ["sticker"],
                    "attribution": "нарисовал друг"
                  }
                }
            """.trimIndent(),
            id = ImagePackId.User,
        )

        assertThat(pack).isNotNull()
        assertThat(pack!!.displayName).isEqualTo("Коты")
        assertThat(pack.avatarUrl).isEqualTo("mxc://mango-kokos.ru/avatar")
        assertThat(pack.attribution).isEqualTo("нарисовал друг")
        assertThat(pack.images).hasSize(1)
        with(pack.images.first()) {
            assertThat(shortcode).isEqualTo("cat")
            assertThat(url).isEqualTo("mxc://mango-kokos.ru/cat")
            assertThat(bestDescription).isEqualTo("Кот")
            assertThat(usages).containsExactly(ImagePackUsage.STICKER)
            assertThat(mimeType).isEqualTo("image/webp")
            assertThat(width).isEqualTo(512)
            assertThat(height).isEqualTo(512)
            assertThat(size).isEqualTo(12345)
        }
        assertThat(pack.stickers).hasSize(1)
        assertThat(pack.emoticons).isEmpty()
    }

    @Test
    fun `parse - image without usage inherits it from the pack`() {
        val pack = parser.parse(
            rawContent = """
                {
                  "images": { "cat": { "url": "mxc://mango-kokos.ru/cat" } },
                  "pack": { "usage": ["sticker"] }
                }
            """.trimIndent(),
            id = ImagePackId.User,
        )

        assertThat(pack!!.images.first().usages).containsExactly(ImagePackUsage.STICKER)
    }

    @Test
    fun `parse - no usage anywhere means both`() {
        val pack = parser.parse(
            rawContent = """{ "images": { "cat": { "url": "mxc://mango-kokos.ru/cat" } } }""",
            id = ImagePackId.User,
        )

        assertThat(pack!!.images.first().usages)
            .containsExactly(ImagePackUsage.EMOTICON, ImagePackUsage.STICKER)
        assertThat(pack.stickers).hasSize(1)
        assertThat(pack.emoticons).hasSize(1)
    }

    @Test
    fun `parse - unknown usage falls back instead of hiding the image`() {
        val pack = parser.parse(
            rawContent = """
                {
                  "images": { "cat": { "url": "mxc://mango-kokos.ru/cat", "usage": ["hologram"] } },
                  "pack": { "usage": ["sticker"] }
                }
            """.trimIndent(),
            id = ImagePackId.User,
        )

        // Картинка не должна пропасть из-за значения, которого мы ещё не знаем.
        assertThat(pack!!.images.first().usages).containsExactly(ImagePackUsage.STICKER)
    }

    @Test
    fun `parse - images without a valid mxc url are dropped`() {
        val pack = parser.parse(
            rawContent = """
                {
                  "images": {
                    "good": { "url": "mxc://mango-kokos.ru/good" },
                    "noUrl": { "body": "нет ссылки" },
                    "httpUrl": { "url": "https://mango-kokos.ru/evil.png" }
                  }
                }
            """.trimIndent(),
            id = ImagePackId.User,
        )

        assertThat(pack!!.images.map { it.shortcode }).containsExactly("good")
    }

    @Test
    fun `parse - broken json returns null instead of throwing`() {
        assertThat(parser.parse("не json вовсе", ImagePackId.User)).isNull()
    }

    @Test
    fun `parse - pack without usable images returns null`() {
        assertThat(parser.parse("""{ "images": {} }""", ImagePackId.User)).isNull()
        assertThat(parser.parse("""{ "pack": { "display_name": "Пусто" } }""", ImagePackId.User)).isNull()
    }

    @Test
    fun `parse - unknown fields do not break parsing`() {
        val pack = parser.parse(
            rawContent = """
                {
                  "images": { "cat": { "url": "mxc://mango-kokos.ru/cat", "чужое_поле": 1 } },
                  "pack": { "display_name": "Коты", "future_field": { "a": "b" } }
                }
            """.trimIndent(),
            id = ImagePackId.User,
        )

        assertThat(pack!!.displayName).isEqualTo("Коты")
        assertThat(pack.images).hasSize(1)
    }

    @Test
    fun `parse - keeps image order from json`() {
        val pack = parser.parse(
            rawContent = """
                {
                  "images": {
                    "b": { "url": "mxc://mango-kokos.ru/b" },
                    "a": { "url": "mxc://mango-kokos.ru/a" },
                    "c": { "url": "mxc://mango-kokos.ru/c" }
                  }
                }
            """.trimIndent(),
            id = ImagePackId.User,
        )

        // Порядок задаёт автор пака, алфавитная сортировка сломала бы его задумку.
        assertThat(pack!!.images.map { it.shortcode }).containsExactly("b", "a", "c").inOrder()
    }

    @Test
    fun `parse - room pack keeps its id`() {
        val id = ImagePackId.Room(roomId = "!room:mango-kokos.ru", stateKey = "коты")
        val pack = parser.parse(
            rawContent = """{ "images": { "cat": { "url": "mxc://mango-kokos.ru/cat" } } }""",
            id = id,
        )

        assertThat(pack!!.id).isEqualTo(id)
    }

    @Test
    fun `parseEmoteRooms - returns every room and state key`() {
        val rooms = parser.parseEmoteRooms(
            """
            {
              "rooms": {
                "!one:mango-kokos.ru": { "": {}, "коты": {} },
                "!two:mango-kokos.ru": { "мемы": {} }
              }
            }
            """.trimIndent()
        )

        assertThat(rooms).containsExactly(
            "!one:mango-kokos.ru" to "",
            "!one:mango-kokos.ru" to "коты",
            "!two:mango-kokos.ru" to "мемы",
        )
    }

    @Test
    fun `parseEmoteRooms - broken json returns empty list`() {
        assertThat(parser.parseEmoteRooms("{")).isEmpty()
        assertThat(parser.parseEmoteRooms("""{ "rooms": "не объект" }""")).isEmpty()
    }
}
