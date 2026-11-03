/*
 * Copyright (c) 2026 Larpgram.
 * Модуль форка: стикер-паки по MSC2545 (im.ponies image packs).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.imagepacks.impl

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.imagepacks.api.ImagePack
import io.element.android.libraries.imagepacks.api.ImagePackEventTypes
import io.element.android.libraries.imagepacks.api.ImagePackId
import io.element.android.libraries.imagepacks.api.ImagePackSource
import io.element.android.libraries.matrix.api.MatrixClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
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
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
class DefaultImagePackSource(
    private val matrixClient: MatrixClient,
    private val okHttpClient: OkHttpClient,
    private val coroutineDispatchers: CoroutineDispatchers,
) : ImagePackSource {
    // Парсер без состояния и без зависимостей, поэтому держим его тут, а не в графе DI.
    private val parser = ImagePackParser()
    private val savedMutex = Mutex()
    private val pendingSavedChanges = mutableMapOf<ImagePackId, ImagePack?>()

    override suspend fun getUserPacks(): List<ImagePack> {
        val raw = matrixClient.getAccountData(ImagePackEventTypes.USER_EMOTES).getOrNull() ?: return emptyList()
        // Личный пак ровно один, но наружу отдаём списком: пикеру всё равно склеивать
        // его с паками комнат.
        return withContext(coroutineDispatchers.computation) {
            listOfNotNull(parser.parse(raw, ImagePackId.User))
        }
    }

    override suspend fun getEmoteRooms(): List<Pair<String, String>> {
        val raw = matrixClient.getAccountData(ImagePackEventTypes.EMOTE_ROOMS).getOrNull() ?: return emptyList()
        return withContext(coroutineDispatchers.computation) { parser.parseEmoteRooms(raw) }
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
        return withContext(coroutineDispatchers.computation) { parser.parse(body, ImagePackId.Room(roomId, stateKey)) }
    }

    override suspend fun getSavedPacks(): List<ImagePack> = savedMutex.withLock {
        readSavedForMutation().orEmpty()
    }

    // Keep acknowledged local mutations until sync echoes them. A stale intermediate
    // echo must not undo a later successful save, and remote additions are retained.
    private suspend fun readSavedForMutation(): List<ImagePack>? {
        val result = matrixClient.getAccountData(ImagePackEventTypes.SAVED_PACKS)
        if (result.isFailure) return null
        val raw = result.getOrNull()
        val packs = withContext(coroutineDispatchers.computation) {
            runCatchingExceptions {
                raw?.let { parser.parseSavedPacks(it, strict = true) }.orEmpty()
            }.getOrNull()
        }
        if (packs == null) return null
        val merged = packs.associateBy { it.id }.toMutableMap()
        val iterator = pendingSavedChanges.iterator()
        while (iterator.hasNext()) {
            val (id, desired) = iterator.next()
            if (merged[id] == desired) {
                iterator.remove()
            } else if (desired == null) {
                merged.remove(id)
            } else {
                merged[id] = desired
            }
        }
        return merged.values.toList()
    }

    private suspend fun writeSaved(packs: List<ImagePack>): Boolean {
        val serialized = withContext(coroutineDispatchers.computation) { parser.serializeSavedPacks(packs) }
        val success = matrixClient.setAccountData(ImagePackEventTypes.SAVED_PACKS, serialized).isSuccess
        return success
    }

    override suspend fun savePack(pack: ImagePack): Boolean = savedMutex.withLock {
        val slug = (pack.id as? ImagePackId.Saved)?.slug ?: return@withLock false
        val current = readSavedForMutation() ?: return@withLock false
        writeSaved(current.filterNot { (it.id as? ImagePackId.Saved)?.slug == slug } + pack)
            .also { if (it) pendingSavedChanges[pack.id] = pack }
    }

    override suspend fun removeSavedPack(slug: String): Boolean = savedMutex.withLock {
        val current = readSavedForMutation() ?: return@withLock false
        val updated = current.filterNot { (it.id as? ImagePackId.Saved)?.slug == slug }
        if (updated.size == current.size) return@withLock false
        writeSaved(updated).also { if (it) pendingSavedChanges[ImagePackId.Saved(slug)] = null }
    }

    override suspend fun getAllPacks(): List<ImagePack> = coroutineScope {
        val own = async { getSavedPacks() + getUserPacks() }
        val permits = Semaphore(4)
        val rooms = getEmoteRooms().map { (roomId, stateKey) ->
            async { permits.withPermit { getRoomPack(roomId, stateKey) } }
        }
        own.await() + rooms.awaitAll().filterNotNull()
    }

    override fun serializePackDescriptor(pack: ImagePack): String = parser.serializePackDescriptor(pack)

    override fun parsePackDescriptor(json: String): ImagePack? = parser.parsePackDescriptor(json)

    private fun String.encode(): String = URLEncoder.encode(this, Charsets.UTF_8.name())
        // URLEncoder делает форму для тела запроса, а не для пути: пробел там '+',
        // а в пути он должен быть %20.
        .replace("+", "%20")
}
