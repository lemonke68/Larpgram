/*
 * Правка форка: признаки медиа, по которым в списке чатов и в уведомлениях пишется тип
 * сообщения словом — «Кружочек», «GIF», «Стикер».
 *
 * Лежит в api, а не рядом с форматтером, потому что нужно сразу в двух модулях:
 * libraries/eventformatter (список чатов) и libraries/push (уведомления).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.matrix.api.timeline.item.event

import io.element.android.libraries.matrix.api.media.MediaSource

/**
 * Префикс имени файла, которым помечается кружочек.
 *
 * Своё поле в content добавить нечем: `Timeline.sendVideo` его не принимает, а отправлять
 * событие вручную нельзя, иначе в шифрованной комнате медиа останется незашифрованным.
 * Имя файла доезжает до клиента в целости, поэтому маркер живёт в нём.
 *
 * Чужие клиенты покажут такое сообщение обычным видео, и это нормально.
 */
const val LARPGRAM_CIRCLE_FILENAME_PREFIX = "larpgram-circle"

private const val MIME_TYPE_GIF = "image/gif"

/** Кружочек ли это видео. */
val VideoMessageType.isLarpgramCircle: Boolean
    get() = filename.startsWith(LARPGRAM_CIRCLE_FILENAME_PREFIX)

/**
 * Гифка ли это картинка.
 *
 * Свои гифки уходят с `image/gif` в info, но у чужих клиентов info может и не быть,
 * поэтому вторым признаком идёт расширение.
 */
val ImageMessageType.isGif: Boolean
    get() = info?.mimetype.equals(MIME_TYPE_GIF, ignoreCase = true) ||
        filename.endsWith(".gif", ignoreCase = true)

/**
 * Картинка для мини-превью в строке списка чатов, если она вообще есть.
 *
 * Берётся миниатюра, а не оригинал: строка рисует квадрат 18dp, тянуть ради него полную
 * картинку незачем. У картинок миниатюры может не быть вовсе, тогда сойдёт и оригинал, его
 * всё равно уже кэшировал таймлайн. У видео своей миниатюры нет — нет и превью, показывать
 * вместо него нечего.
 *
 * Живёт здесь, а не в списке чатов, потому что тем же признаком форматтер решает, оставлять
 * ли в тексте место под картинку. Разъедутся — в строке будет пустая дырка.
 */
val EventContent.larpgramPreviewThumbnail: MediaSource?
    get() = when (this) {
        is StickerContent -> info.thumbnailSource ?: source
        is MessageContent -> when (val type = type) {
            is ImageMessageType -> type.info?.thumbnailSource ?: type.source
            is VideoMessageType -> type.info?.thumbnailSource
            else -> null
        }
        else -> null
    }

/**
 * Эмодзи стикера, если в описании лежит именно оно.
 *
 * В Telegram превью стикера выглядит как «😂 Стикер», и у импортированных паков в body
 * действительно лежит эмодзи. Но body пишут посторонние люди: там может оказаться имя
 * файла, shortcode или целая фраза, и такое показывать перед словом «Стикер» незачем.
 */
fun String.larpgramStickerEmojiOrNull(): String? {
    val trimmed = trim()
    if (trimmed.isEmpty()) return null
    if (trimmed.codePointCount(0, trimmed.length) > MAX_STICKER_EMOJI_CODE_POINTS) return null
    // Буквы отсекают shortcode и фразу, точка — имя файла, пробел — перечисление эмодзи.
    if (trimmed.any { it.isLetter() || it.isWhitespace() || it == '.' }) return null
    return trimmed
}

/** Эмодзи бывает составным (флаги, семьи, модификаторы кожи), одной точкой не обойтись. */
private const val MAX_STICKER_EMOJI_CODE_POINTS = 8
