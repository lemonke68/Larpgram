/*
 * Модуль форка: импорт стикер-паков из Telegram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.stickers.impl.import

import android.graphics.BitmapFactory
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.imagepacks.api.ImagePack
import io.element.android.libraries.imagepacks.api.ImagePackId
import io.element.android.libraries.imagepacks.api.ImagePackImage
import io.element.android.libraries.imagepacks.api.ImagePackSource
import io.element.android.libraries.imagepacks.api.ImagePackUsage
import io.element.android.libraries.matrix.api.MatrixClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

/** Чем закончился импорт. */
sealed interface ImportResult {
    data class Success(val pack: ImagePack, val skipped: Int) : ImportResult

    /** Пака с таким именем нет, либо бот его не видит. */
    data object NotFound : ImportResult

    /** Пак есть, но в нём нечего забирать. */
    data class EmptyPack(val total: Int) : ImportResult

    /** Сеть, сервер импорта или homeserver подвели. */
    data object Failed : ImportResult
}

@Serializable
private data class PackJson(
    val slug: String = "",
    val title: String = "",
    val total: Int = 0,
    val stickers: List<StickerJson> = emptyList(),
)

@Serializable
private data class StickerJson(
    @SerialName("fileId") val fileId: String = "",
    @SerialName("fileUniqueId") val fileUniqueId: String = "",
    val emoji: String = "",
    @SerialName("mimeType") val mimeType: String = "image/webp",
    /** Стикер был анимированным или видео и приедет сконвертированным, то есть тяжёлым. */
    val animated: Boolean = false,
    val width: Long? = null,
    val height: Long? = null,
    val size: Long? = null,
)

/**
 * Импортирует пак из Telegram: спрашивает состав у нашего сервиса, скачивает картинки и
 * заливает их в media нашего homeserver.
 *
 * Картинки заливает именно приложение, а не сервер импорта: иначе на сервере пришлось бы
 * держать постоянный токен служебного матричного аккаунта.
 */
class StickerPackImporter(
    private val okHttpClient: OkHttpClient,
    private val coroutineDispatchers: CoroutineDispatchers,
    private val matrixClient: MatrixClient,
    private val imagePackSource: ImagePackSource,
    private val baseUrl: String = DEFAULT_BASE_URL,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * @param packName короткое имя пака, то что после `t.me/addstickers/`.
     */
    suspend fun import(packName: String): ImportResult = withContext(coroutineDispatchers.io) {
        val name = packName.trim().substringAfterLast('/')
        if (name.isEmpty()) return@withContext ImportResult.NotFound

        val packJson = when (val fetched = fetchPack(name)) {
            is FetchResult.Ok -> fetched.pack
            FetchResult.NotFound -> return@withContext ImportResult.NotFound
            is FetchResult.Empty -> return@withContext ImportResult.EmptyPack(fetched.total)
            FetchResult.Error -> return@withContext ImportResult.Failed
        }

        // Пак качается и заливается в несколько потоков: анимированный стикер весит
        // сотни килобайт, и полсотни таких по очереди человек ждал бы минуту. Порядок
        // при этом сохраняется, его задаёт автор пака.
        val images = coroutineScope {
            val limit = Semaphore(MAX_PARALLEL_UPLOADS)
            packJson.stickers.map { sticker ->
                async {
                    limit.withPermit {
                        val bytes = downloadSticker(sticker.fileId, sticker.fileUniqueId)
                            ?: return@withPermit null
                        val mxcUrl = matrixClient.uploadMedia(sticker.mimeType, bytes).getOrNull()
                            ?: return@withPermit null
                        // Размеры берём из самого файла, а не из метаданных пака: Telegram
                        // сообщает у видео-стикеров квадрат (512x512), хотя кадр бывает
                        // 512x288, и стикер в чате выезжал в квадратную коробку с полями.
                        val realSize = measure(bytes)
                        ImagePackImage(
                            // file_unique_id не меняется и уникален внутри пака, годится как shortcode.
                            shortcode = sticker.fileUniqueId.ifBlank { sticker.fileId.take(16) },
                            url = mxcUrl,
                            body = sticker.emoji,
                            usages = setOf(ImagePackUsage.STICKER),
                            mimeType = sticker.mimeType,
                            width = realSize?.first ?: sticker.width,
                            height = realSize?.second ?: sticker.height,
                            size = sticker.size ?: bytes.size.toLong(),
                        )
                    }
                }
            }.awaitAll().filterNotNull()
        }

        // Один-два не долетевших стикера не повод терять весь пак, но пустой пак бесполезен.
        if (images.isEmpty()) return@withContext ImportResult.Failed

        val pack = ImagePack(
            id = ImagePackId.Saved(packJson.slug.ifBlank { "tg-$name" }),
            displayName = packJson.title.ifBlank { name },
            avatarUrl = null,
            usages = setOf(ImagePackUsage.STICKER),
            attribution = "импортировано из Telegram",
            images = images,
        )

        if (!imagePackSource.savePack(pack)) {
            return@withContext ImportResult.Failed
        }
        ImportResult.Success(pack = pack, skipped = packJson.total - images.size)
    }

    private sealed interface FetchResult {
        data class Ok(val pack: PackJson) : FetchResult
        data object NotFound : FetchResult
        data class Empty(val total: Int) : FetchResult
        data object Error : FetchResult
    }

    private fun fetchPack(name: String): FetchResult {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("pack")
            .addQueryParameter("name", name)
            .build()
        return runCatching {
            okHttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                when {
                    response.isSuccessful -> FetchResult.Ok(json.decodeFromString<PackJson>(body))
                    response.code == 404 -> FetchResult.NotFound
                    // 422: пак существует, но стикеров в нём нет.
                    response.code == 422 -> FetchResult.Empty(
                        json.decodeFromString<PackJson>(body).total
                    )
                    else -> FetchResult.Error
                }
            }
        }.getOrElse {
            Timber.e(it, "не удалось получить состав пака")
            FetchResult.Error
        }
    }

    /**
     * Настоящий размер картинки из её же байтов.
     *
     * `inJustDecodeBounds` читает только заголовок, не разворачивая пиксели, поэтому это
     * дёшево даже для анимированного webp: у него берётся первый кадр.
     */
    private fun measure(bytes: ByteArray): Pair<Long, Long>? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        val width = options.outWidth
        val height = options.outHeight
        return if (width > 0 && height > 0) width.toLong() to height.toLong() else null
    }

    private fun downloadSticker(fileId: String, uniqueId: String): ByteArray? {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("file")
            .addQueryParameter("id", fileId)
            // Подсказка серверу: по ней он найдёт стикер в своём кэше, не спрашивая
            // Telegram. На уже импортированном кем-то паке это втрое быстрее.
            .addQueryParameter("uid", uniqueId)
            .build()
        return runCatching {
            okHttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.bytes()
            }
        }.getOrElse {
            Timber.e(it, "не удалось скачать стикер")
            null
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://stickers.mango-kokos.ru/import"

        /**
         * Сколько стикеров качаем и заливаем разом. Больше упирается уже не в нас, а в
         * канал пользователя, и на слабой сети только вредит.
         */
        private const val MAX_PARALLEL_UPLOADS = 4
    }
}
