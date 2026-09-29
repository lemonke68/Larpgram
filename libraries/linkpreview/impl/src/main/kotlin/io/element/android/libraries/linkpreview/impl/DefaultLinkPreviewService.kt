/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.linkpreview.impl

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.linkpreview.api.LinkPreview
import io.element.android.libraries.linkpreview.api.LinkPreviewService
import io.element.android.libraries.matrix.api.ClientUrlContentFetcher
import io.element.android.libraries.matrix.api.MatrixClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

/**
 * Превью берём у своего Synapse (`GET /_matrix/client/v1/media/preview_url`): сервер сам ходит
 * на страницу (с блоклистом внутренних адресов) и кладёт картинку в свои медиа. Клиент к сайту
 * не обращается, поэтому собеседник не узнаёт наш IP по ссылке.
 *
 * Кэш — на сессию: ответ «превью нет» тоже запоминается, а сетевой сбой — нет, чтобы повторить
 * при следующем показе сообщения.
 */
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
class DefaultLinkPreviewService(
    private val matrixClient: MatrixClient,
    // Адрес клиентского API из .well-known сессии (matrix.<домен>), не голый домен.
    private val clientUrlContentFetcher: ClientUrlContentFetcher,
    private val okHttpClient: OkHttpClient,
    private val coroutineDispatchers: CoroutineDispatchers,
) : LinkPreviewService {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val cache = object : LinkedHashMap<String, CachedPreview>(MAX_CACHED, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedPreview>?) = size > MAX_CACHED
    }

    override suspend fun preview(url: String): LinkPreview? {
        mutex.withLock { cache[url] }?.let { return it.preview }
        val result = fetch(url) ?: return null
        mutex.withLock { cache[url] = result }
        return result.preview
    }

    /** null — сбой сети или токена (не кэшируем), иначе ответ сервера, возможно «превью нет». */
    private suspend fun fetch(url: String): CachedPreview? {
        val token = matrixClient.getAccessToken().getOrNull() ?: return null
        val request = Request.Builder()
            .url(
                "${clientUrlContentFetcher.homeserverUrl.trimEnd('/')}/_matrix/client/v1/media/preview_url".toHttpUrl().newBuilder()
                    .addQueryParameter("url", url)
                    .build()
            )
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        return withContext(coroutineDispatchers.io) {
            runCatching {
                okHttpClient.newCall(request).execute().use { response ->
                    when {
                        response.isSuccessful -> CachedPreview(parse(url, response.body.string()))
                        // 4xx: сервер не смог или не захотел строить превью — это окончательно.
                        response.code in 400..499 && response.code != 429 && response.code != 401 -> CachedPreview(null)
                        else -> null
                    }
                }
            }.getOrElse {
                Timber.w(it, "linkpreview: запрос не удался")
                null
            }
        }
    }

    private fun parse(url: String, body: String): LinkPreview? {
        val og = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        return LinkPreview(
            url = url,
            siteName = og.text("og:site_name"),
            title = og.text("og:title"),
            description = og.text("og:description"),
            imageMxc = og.text("og:image")?.takeIf { it.startsWith("mxc://") },
            imageWidth = og.int("og:image:width"),
            imageHeight = og.int("og:image:height"),
        ).takeUnless { it.isEmpty }
    }

    private fun JsonObject.text(key: String): String? =
        (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.takeIf { it.isNotEmpty() }

    private fun JsonObject.int(key: String): Int? = (get(key) as? JsonPrimitive)?.let { it.intOrNull ?: it.content.toIntOrNull() }

    private class CachedPreview(val preview: LinkPreview?)

    private companion object {
        const val MAX_CACHED = 200
    }
}
