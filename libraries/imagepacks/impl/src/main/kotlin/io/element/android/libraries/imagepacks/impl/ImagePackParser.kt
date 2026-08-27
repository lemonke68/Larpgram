/*
 * Модуль форка: стикер-паки по MSC2545 (im.ponies image packs).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.imagepacks.impl

import io.element.android.libraries.imagepacks.api.ImagePack
import io.element.android.libraries.imagepacks.api.ImagePackId
import io.element.android.libraries.imagepacks.api.ImagePackImage
import io.element.android.libraries.imagepacks.api.ImagePackUsage
import kotlinx.serialization.json.Json

/**
 * Разбирает содержимое события пака в модель.
 *
 * Паки приходят из сети и пишутся кем угодно, поэтому парсер намеренно терпимый:
 * битый JSON и картинки без url отбрасываются молча, вместо того чтобы ронять весь пак.
 */
class ImagePackParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    private val encodeJson = Json { encodeDefaults = true }

    /**
     * @param rawContent JSON события целиком.
     * @param id откуда пак взят.
     * @return разобранный пак или null, если JSON нечитаем либо в паке не осталось картинок.
     */
    fun parse(rawContent: String, id: ImagePackId): ImagePack? {
        val content = try {
            json.decodeFromString<ImagePackContentJson>(rawContent)
        } catch (_: Exception) {
            // Пак прислал другой клиент или другой человек, портить себе пикер не хотим.
            return null
        }
        return parseContent(content, id)
    }

    private fun parseContent(content: ImagePackContentJson, id: ImagePackId): ImagePack? {
        val packUsages = content.pack?.usage.toUsages()
        val images = content.images.mapNotNull { (shortcode, image) ->
            val url = image.url?.takeIf { it.startsWith(MXC_PREFIX) } ?: return@mapNotNull null
            ImagePackImage(
                shortcode = shortcode,
                url = url,
                body = image.body,
                // MSC2545: у картинки usage может отсутствовать, тогда действует usage пака.
                usages = image.usage.toUsages(fallback = packUsages),
                mimeType = image.info?.mimetype,
                width = image.info?.width,
                height = image.info?.height,
                size = image.info?.size,
            )
        }

        if (images.isEmpty()) return null

        return ImagePack(
            id = id,
            displayName = content.pack?.displayName,
            avatarUrl = content.pack?.avatarUrl?.takeIf { it.startsWith(MXC_PREFIX) },
            usages = packUsages,
            attribution = content.pack?.attribution,
            images = images,
        )
    }

    /**
     * Разбирает список подключённых комнат из account data `im.ponies.emote_rooms`.
     *
     * @return пары «id комнаты, state key», в которых лежат паки.
     */
    fun parseEmoteRooms(rawContent: String): List<Pair<String, String>> {
        val content = try {
            json.decodeFromString<EmoteRoomsJson>(rawContent)
        } catch (_: Exception) {
            return emptyList()
        }
        return content.rooms.flatMap { (roomId, stateKeys) ->
            stateKeys.keys.map { stateKey -> roomId to stateKey }
        }
    }

    /**
     * Разбирает наш список сохранённых паков.
     *
     * Паки без slug пропускаем: без него пак нельзя ни обновить, ни удалить.
     */
    fun parseSavedPacks(rawContent: String): List<ImagePack> {
        val content = try {
            json.decodeFromString<SavedPacksJson>(rawContent)
        } catch (_: Exception) {
            return emptyList()
        }
        return content.packs.mapNotNull { saved ->
            if (saved.slug.isBlank()) return@mapNotNull null
            val asPackJson = ImagePackContentJson(images = saved.images, pack = saved.pack)
            parseContent(asPackJson, ImagePackId.Saved(saved.slug))
        }
    }

    /** Сериализует паки обратно в наш формат для записи в account data. */
    fun serializeSavedPacks(packs: List<ImagePack>): String =
        encodeJson.encodeToString(SavedPacksJson(packs = packs.mapNotNull { it.toSavedPackJson() }))

    /**
     * Дескриптор одного пака для вложения в событие `m.sticker`: тот же формат, что у сохранённого
     * пака (slug + images + pack), поэтому [parsePackDescriptor] разбирает его тем же кодом.
     */
    fun serializePackDescriptor(pack: ImagePack): String {
        val saved = pack.toSavedPackJson() ?: return "{}"
        return encodeJson.encodeToString(saved)
    }

    /** Обратный разбор дескриптора пака из `m.sticker`, или null если нечитаем/без slug. */
    fun parsePackDescriptor(rawContent: String): ImagePack? {
        val saved = try {
            json.decodeFromString<SavedPackJson>(rawContent)
        } catch (_: Exception) {
            return null
        }
        if (saved.slug.isBlank()) return null
        return parseContent(
            ImagePackContentJson(images = saved.images, pack = saved.pack),
            ImagePackId.Saved(saved.slug),
        )
    }

    private fun ImagePack.toSavedPackJson(): SavedPackJson? {
        val slug = (id as? ImagePackId.Saved)?.slug ?: return null
        return SavedPackJson(
            slug = slug,
            pack = ImagePackInfoJson(
                displayName = displayName,
                avatarUrl = avatarUrl,
                usage = usages.map { it.toWire() },
                attribution = attribution,
            ),
            images = images.associate { image ->
                image.shortcode to ImagePackImageJson(
                    url = image.url,
                    body = image.body,
                    usage = image.usages.map { it.toWire() },
                    info = ImagePackImageInfoJson(
                        mimetype = image.mimeType,
                        width = image.width,
                        height = image.height,
                        size = image.size,
                    ),
                )
            },
        )
    }

    private fun ImagePackUsage.toWire(): String = when (this) {
        ImagePackUsage.EMOTICON -> USAGE_EMOTICON
        ImagePackUsage.STICKER -> USAGE_STICKER
    }

    private fun List<String>?.toUsages(fallback: Set<ImagePackUsage> = ALL_USAGES): Set<ImagePackUsage> {
        // Отсутствующий список означает «подходит везде», а не «нигде».
        if (this == null) return fallback
        val parsed = mapNotNullTo(mutableSetOf()) { raw ->
            when (raw) {
                USAGE_EMOTICON -> ImagePackUsage.EMOTICON
                USAGE_STICKER -> ImagePackUsage.STICKER
                // В MSC перечислены только эти два, остальное это чей-то будущий формат.
                else -> null
            }
        }
        // Список из одних неизвестных значений равносилен отсутствию списка.
        return parsed.ifEmpty { fallback }
    }

    private companion object {
        const val MXC_PREFIX = "mxc://"
        const val USAGE_EMOTICON = "emoticon"
        const val USAGE_STICKER = "sticker"
        val ALL_USAGES = setOf(ImagePackUsage.EMOTICON, ImagePackUsage.STICKER)
    }
}
