/*
 * Модуль форка: импорт стикер-паков из Telegram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.stickers.impl.import

import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.imagepacks.api.ImagePack
import io.element.android.libraries.imagepacks.api.ImagePackId
import io.element.android.libraries.imagepacks.api.ImagePackImage
import io.element.android.libraries.imagepacks.api.ImagePackSource
import io.element.android.libraries.imagepacks.api.ImagePackUsage
import io.element.android.libraries.matrix.api.MatrixClient
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

    /** Пак есть, но в нём одни анимированные стикеры. */
    data class NoStaticStickers(val total: Int) : ImportResult

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
            is FetchResult.NoStatic -> return@withContext ImportResult.NoStaticStickers(fetched.total)
            FetchResult.Error -> return@withContext ImportResult.Failed
        }

        val images = packJson.stickers.mapNotNull { sticker ->
            val bytes = downloadSticker(sticker.fileId) ?: return@mapNotNull null
            val mxcUrl = matrixClient.uploadMedia(sticker.mimeType, bytes).getOrNull() ?: return@mapNotNull null
            ImagePackImage(
                // file_unique_id не меняется и уникален внутри пака, годится как shortcode.
                shortcode = sticker.fileUniqueId.ifBlank { sticker.fileId.take(16) },
                url = mxcUrl,
                body = sticker.emoji,
                usages = setOf(ImagePackUsage.STICKER),
                mimeType = sticker.mimeType,
                width = sticker.width,
                height = sticker.height,
                size = sticker.size ?: bytes.size.toLong(),
            )
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
        data class NoStatic(val total: Int) : FetchResult
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
                    // 422: пак есть, но целиком анимированный.
                    response.code == 422 -> FetchResult.NoStatic(
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

    private fun downloadSticker(fileId: String): ByteArray? {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("file")
            .addQueryParameter("id", fileId)
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
    }
}
