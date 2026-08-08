/*
 * Модуль форка: гифки через свой прокси к Tenor.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.gifs.impl

import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** Одна гифка из выдачи прокси. */
data class Gif(
    val id: String,
    val description: String,
    /** Файл, который уйдёт в чат. */
    val url: String,
    /** Уменьшенная копия для сетки, чтобы не тянуть мегабайты ради превью. */
    val previewUrl: String,
    val width: Long?,
    val height: Long?,
    val size: Long?,
)

/** Страница выдачи: гифки плюс курсор для подгрузки следующей. */
data class GifPage(
    val gifs: List<Gif>,
    val next: String?,
)

@Serializable
private data class GifResponseJson(
    val next: String? = null,
    val results: List<GifJson> = emptyList(),
)

@Serializable
private data class GifJson(
    val id: String = "",
    val description: String = "",
    val url: String = "",
    @SerialName("previewUrl") val previewUrl: String = "",
    val width: Long? = null,
    val height: Long? = null,
    val size: Long? = null,
)

/**
 * Ходит за гифками в наш прокси.
 *
 * Напрямую в Tenor не ходим: ключ нельзя класть в APK, его оттуда достанут. Прокси
 * заодно отдаёт уже нормализованный ответ, поэтому смена провайдера не потребует
 * обновления приложения у всех.
 */
class GifRepository(
    private val okHttpClient: OkHttpClient,
    private val coroutineDispatchers: CoroutineDispatchers,
    private val baseUrl: String = DEFAULT_BASE_URL,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * @param query пустой запрос означает подборку популярного.
     * @param pos курсор предыдущей страницы, null для первой.
     */
    suspend fun search(query: String, pos: String? = null): Result<GifPage> = withContext(coroutineDispatchers.io) {
        runCatching {
            val url = baseUrl.toHttpUrl().newBuilder()
                .addPathSegment(if (query.isBlank()) "featured" else "search")
                .apply {
                    if (query.isNotBlank()) addQueryParameter("q", query)
                    if (!pos.isNullOrBlank()) addQueryParameter("pos", pos)
                }
                .build()
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("прокси ответил ${response.code}")
                val body = response.body?.string().orEmpty()
                val parsed = json.decodeFromString<GifResponseJson>(body)
                GifPage(
                    gifs = parsed.results
                        // Без ссылок гифка бесполезна, а прокси могло подсунуть пустое поле.
                        .filter { it.url.isNotBlank() && it.previewUrl.isNotBlank() }
                        .map {
                            Gif(
                                id = it.id,
                                description = it.description,
                                url = it.url,
                                previewUrl = it.previewUrl,
                                width = it.width,
                                height = it.height,
                                size = it.size,
                            )
                        },
                    next = parsed.next?.takeIf { it.isNotBlank() },
                )
            }
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://gifs.mango-kokos.ru"
    }
}
