/*
 * Модуль форка: стикер-паки по MSC2545 (im.ponies image packs).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.imagepacks.impl

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Содержимое события пака, как оно приходит с сервера.
 *
 * Формат один и тот же для account data `im.ponies.user_emotes` и для состояния комнаты
 * `im.ponies.room_emotes`.
 */
@Serializable
internal data class ImagePackContentJson(
    val images: Map<String, ImagePackImageJson> = emptyMap(),
    val pack: ImagePackInfoJson? = null,
)

@Serializable
internal data class ImagePackInfoJson(
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val usage: List<String>? = null,
    val attribution: String? = null,
)

@Serializable
internal data class ImagePackImageJson(
    val url: String? = null,
    val body: String? = null,
    val usage: List<String>? = null,
    val info: ImagePackImageInfoJson? = null,
)

@Serializable
internal data class ImagePackImageInfoJson(
    val mimetype: String? = null,
    @SerialName("w") val width: Long? = null,
    @SerialName("h") val height: Long? = null,
    val size: Long? = null,
)

/**
 * Список комнат, чьи паки пользователь подключил: account data `im.ponies.emote_rooms`.
 *
 * Внутренний объект описывает конкретные state key и нам сейчас не нужен, но структуру
 * держим, чтобы не потерять ключи при разборе.
 */
@Serializable
internal data class EmoteRoomsJson(
    val rooms: Map<String, Map<String, EmoteRoomEntryJson>> = emptyMap(),
)

@Serializable
internal data class EmoteRoomEntryJson(
    @SerialName("display_name") val displayName: String? = null,
)

/**
 * Наш список сохранённых паков: account data `ru.mangokokos.larpgram.packs`.
 *
 * Формат специально близок к MSC2545, чтобы один и тот же разбор годился и там, и тут:
 * каждый элемент это обычный пак плюс наш `slug` для опознания и удаления.
 */
@Serializable
internal data class SavedPacksJson(
    val packs: List<SavedPackJson> = emptyList(),
)

@Serializable
internal data class SavedPackJson(
    val slug: String = "",
    val images: Map<String, ImagePackImageJson> = emptyMap(),
    val pack: ImagePackInfoJson? = null,
)
