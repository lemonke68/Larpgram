/*
 * Модуль форка: стикер-паки по MSC2545 (im.ponies image packs).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.imagepacks.api

/**
 * Как картинку разрешено использовать.
 *
 * В MSC2545 поле необязательное, и пустой список означает «и то, и другое».
 * Разворачиваем это при разборе, чтобы дальше по коду не приходилось помнить правило.
 */
enum class ImagePackUsage {
    /** Эмодзи: вставляется в текст сообщения. */
    EMOTICON,

    /** Стикер: уходит отдельным событием m.sticker. */
    STICKER,
}

/**
 * Одна картинка в паке.
 *
 * @param shortcode ключ в паке, он же то, что пользователь набирает для эмодзи.
 * @param url mxc-ссылка на файл.
 * @param body человекочитаемое описание, уезжает в body события и в подсказки доступности.
 * @param usages где эту картинку можно применять, никогда не пустой список.
 * @param mimeType из info, нужен чтобы отличать анимированные webp и gif.
 * @param width ширина из info, может отсутствовать.
 * @param height высота из info, может отсутствовать.
 * @param size размер файла в байтах из info, может отсутствовать.
 */
data class ImagePackImage(
    val shortcode: String,
    val url: String,
    val body: String?,
    val usages: Set<ImagePackUsage>,
    val mimeType: String?,
    val width: Long?,
    val height: Long?,
    val size: Long?,
) {
    /** Описание для показа и для body отправляемого события. */
    val bestDescription: String get() = body?.takeIf { it.isNotBlank() } ?: shortcode
}

/**
 * Пак картинок: либо из account data пользователя, либо из состояния комнаты.
 *
 * @param id откуда пак взялся, нужен чтобы различать паки с одинаковым названием.
 * @param displayName название для вкладки в пикере.
 * @param avatarUrl mxc-ссылка на аватар пака, может отсутствовать.
 * @param usages разрешённое применение для всего пака, никогда не пустой список.
 * @param attribution указание авторства, если задано.
 * @param images картинки в порядке из исходного JSON.
 */
data class ImagePack(
    val id: ImagePackId,
    val displayName: String?,
    val avatarUrl: String?,
    val usages: Set<ImagePackUsage>,
    val attribution: String?,
    val images: List<ImagePackImage>,
) {
    /** Картинки, которые можно отправить стикером. */
    val stickers: List<ImagePackImage> get() = images.filter { ImagePackUsage.STICKER in it.usages }

    /** Картинки, которые можно вставить как эмодзи. */
    val emoticons: List<ImagePackImage> get() = images.filter { ImagePackUsage.EMOTICON in it.usages }
}

/** Откуда взялся пак. */
sealed interface ImagePackId {
    /** Личный пак пользователя, account data `im.ponies.user_emotes`. */
    data object User : ImagePackId

    /**
     * Пак, сохранённый пользователем в Larpgram (например, импортированный из Telegram).
     *
     * @param slug устойчивый идентификатор пака, по нему же удаляем.
     */
    data class Saved(val slug: String) : ImagePackId

    /**
     * Пак комнаты, состояние `im.ponies.room_emotes`.
     *
     * У комнаты может быть несколько паков, они различаются state key.
     */
    data class Room(val roomId: String, val stateKey: String) : ImagePackId
}
