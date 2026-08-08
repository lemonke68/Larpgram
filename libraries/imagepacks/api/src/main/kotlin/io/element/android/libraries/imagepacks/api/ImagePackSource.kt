/*
 * Модуль форка: стикер-паки по MSC2545 (im.ponies image packs).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.imagepacks.api

/** Типы событий из MSC2545 плюс наш собственный. */
object ImagePackEventTypes {
    /** Личный пак пользователя по MSC2545. Он там ровно один. */
    const val USER_EMOTES = "im.ponies.user_emotes"

    /** Список комнат, чьи паки пользователь подключил, глобальные account data. */
    const val EMOTE_ROOMS = "im.ponies.emote_rooms"

    /** Паки комнаты, событие состояния комнаты. */
    const val ROOM_EMOTES = "im.ponies.room_emotes"

    /**
     * Наши сохранённые паки: список, как в Telegram.
     *
     * MSC2545 отводит под личные паки одно событие с одним паком, а нам нужно много
     * сохранённых на аккаунт. Протокол Matrix этому не мешает: в account data можно
     * держать событие любого типа, поэтому заводим своё со списком паков внутри.
     *
     * Плата: такие паки не увидят другие клиенты (Element, Cinny). Для форка это
     * приемлемо, кроссклиентность мы и так не поддерживаем.
     */
    const val SAVED_PACKS = "ru.mangokokos.larpgram.packs"
}

/** Отдаёт стикер-паки, доступные текущему пользователю. */
interface ImagePackSource {
    /**
     * Личные паки пользователя из account data.
     *
     * @return список паков, пустой если пользователь не завёл ни одного.
     */
    suspend fun getUserPacks(): List<ImagePack>

    /**
     * Комнаты, чьи паки пользователь подключил.
     *
     * @return пары «id комнаты, state key».
     */
    suspend fun getEmoteRooms(): List<Pair<String, String>>

    /**
     * Пак из состояния комнаты.
     *
     * @return пак или null, если события нет либо оно нечитаемо.
     */
    suspend fun getRoomPack(roomId: String, stateKey: String): ImagePack?

    /** Паки, сохранённые пользователем в Larpgram. */
    suspend fun getSavedPacks(): List<ImagePack>

    /**
     * Сохраняет пак пользователю. Пак с тем же slug заменяется.
     *
     * @return true если получилось записать.
     */
    suspend fun savePack(pack: ImagePack): Boolean

    /** Удаляет сохранённый пак. */
    suspend fun removeSavedPack(slug: String): Boolean

    /**
     * Все паки, доступные пользователю: сохранённые, личный по MSC2545 и паки комнат.
     *
     * Сохранённые идут первыми: это то, что человек добавил сам.
     */
    suspend fun getAllPacks(): List<ImagePack>
}
