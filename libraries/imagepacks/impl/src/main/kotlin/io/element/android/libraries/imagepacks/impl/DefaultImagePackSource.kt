/*
 * Модуль форка: стикер-паки по MSC2545 (im.ponies image packs).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.imagepacks.impl

import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.imagepacks.api.ImagePack
import io.element.android.libraries.imagepacks.api.ImagePackEventTypes
import io.element.android.libraries.imagepacks.api.ImagePackId
import io.element.android.libraries.imagepacks.api.ImagePackSource
import io.element.android.libraries.matrix.api.MatrixClient
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

/**
 * Достаёт стикер-паки пользователя.
 *
 * Личные паки и список комнат лежат в account data, их отдаёт SDK. А вот паки самих
 * комнат живут в состоянии комнаты, и **чтения состояния в Rust SDK нет**: там есть
 * только `send_state_event_raw`. Поэтому за ними ходим в CS API сами, с токеном сессии.
 *
 * `MatrixClient.getUrl` для этого не подходит: проверено, он ходит без авторизации и
 * получает 401.
 */
@ContributesBinding(SessionScope::class)
class DefaultImagePackSource(
    private val matrixClient: MatrixClient,
    private val okHttpClient: OkHttpClient,
    private val coroutineDispatchers: CoroutineDispatchers,
) : ImagePackSource {
    // Парсер без состояния и без зависимостей, поэтому держим его тут, а не в графе DI.
    private val parser = ImagePackParser()

    override suspend fun getUserPacks(): List<ImagePack> {
        val raw = matrixClient.getAccountData(ImagePackEventTypes.USER_EMOTES).getOrNull() ?: return emptyList()
        // Личный пак ровно один, но наружу отдаём списком: пикеру всё равно склеивать
        // его с паками комнат.
        return listOfNotNull(parser.parse(raw, ImagePackId.User))
    }

    override suspend fun getEmoteRooms(): List<Pair<String, String>> {
        val raw = matrixClient.getAccountData(ImagePackEventTypes.EMOTE_ROOMS).getOrNull() ?: return emptyList()
        return parser.parseEmoteRooms(raw)
    }

    override suspend fun getRoomPack(roomId: String, stateKey: String): ImagePack? {
        val token = matrixClient.getAccessToken().getOrNull() ?: return null
        val url = buildString {
            append(matrixClient.homeserverUrl.trimEnd('/'))
            append("/_matrix/client/v3/rooms/")
            append(roomId.encode())
            append("/state/")
            append(ImagePackEventTypes.ROOM_EMOTES)
            append('/')
            // Пустой state key это обычный случай, и в пути он выглядит как двойной слэш.
            append(stateKey.encode())
        }
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .build()
        val body = withContext(coroutineDispatchers.io) {
            runCatching {
                okHttpClient.newCall(request).execute().use { response ->
                    // 404 это норма: у комнаты может не быть пака.
                    if (response.isSuccessful) response.body?.string() else null
                }
            }.getOrNull()
        } ?: return null
        return parser.parse(body, ImagePackId.Room(roomId, stateKey))
    }

    override suspend fun getSavedPacks(): List<ImagePack> {
        val raw = matrixClient.getAccountData(ImagePackEventTypes.SAVED_PACKS).getOrNull() ?: return emptyList()
        return parser.parseSavedPacks(raw)
    }

    override suspend fun savePack(pack: ImagePack): Boolean {
        val slug = (pack.id as? ImagePackId.Saved)?.slug ?: return false
        // Читаем-меняем-пишем целиком: account data не умеет частичных обновлений.
        val current = getSavedPacks().filterNot { (it.id as? ImagePackId.Saved)?.slug == slug }
        val updated = current + pack
        return matrixClient
            .setAccountData(ImagePackEventTypes.SAVED_PACKS, parser.serializeSavedPacks(updated))
            .isSuccess
    }

    override suspend fun removeSavedPack(slug: String): Boolean {
        val current = getSavedPacks()
        val updated = current.filterNot { (it.id as? ImagePackId.Saved)?.slug == slug }
        if (updated.size == current.size) return false
        return matrixClient
            .setAccountData(ImagePackEventTypes.SAVED_PACKS, parser.serializeSavedPacks(updated))
            .isSuccess
    }

    override suspend fun getAllPacks(): List<ImagePack> {
        val roomPacks = getEmoteRooms().mapNotNull { (roomId, stateKey) ->
            getRoomPack(roomId, stateKey)
        }
        // Сохранённые первыми: это то, что человек добавил сам, к ним он и тянется.
        return getSavedPacks() + getUserPacks() + roomPacks
    }

    private fun String.encode(): String = URLEncoder.encode(this, Charsets.UTF_8.name())
        // URLEncoder делает форму для тела запроса, а не для пути: пробел там '+',
        // а в пути он должен быть %20.
        .replace("+", "%20")
}
