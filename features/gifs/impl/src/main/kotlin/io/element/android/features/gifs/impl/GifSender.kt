/*
 * Copyright (c) 2026 Larpgram.
 * Модуль форка: гифки через свой прокси к Tenor.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.gifs.impl

import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.matrix.api.media.ImageInfo
import io.element.android.libraries.matrix.api.room.JoinedRoom
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Отправляет гифку в комнату.
 *
 * Ссылкой на Tenor отправить нельзя: Matrix ждёт файл в своём хранилище. Поэтому гифку
 * сначала скачиваем, а потом отдаём в `Timeline.sendImage`, который сам зальёт её и, если
 * комната шифрованная, зашифрует медиа. Своими руками через `uploadMedia` этого делать
 * нельзя: получилась бы незашифрованная картинка в шифрованном чате.
 *
 * Побочный плюс штатного пути: сообщение проходит через таймлайн, поэтому появляется в
 * чате сразу, не дожидаясь ответа сервера (в отличие от стикеров).
 */
class GifSender(
    private val okHttpClient: OkHttpClient,
    private val coroutineDispatchers: CoroutineDispatchers,
    private val cacheDir: File,
) {
    suspend fun send(room: JoinedRoom, gif: Gif): Result<Unit> = withContext(coroutineDispatchers.io) {
        runCatchingExceptions {
            val file = download(gif)
            try {
                val upload = room.liveTimeline.sendImage(
                    file = file,
                    thumbnailFile = null,
                    imageInfo = ImageInfo(
                        height = gif.height,
                        width = gif.width,
                        mimetype = MIME_TYPE_GIF,
                        size = file.length(),
                        thumbnailInfo = null,
                        thumbnailSource = null,
                        blurhash = null,
                    ),
                    caption = null,
                    formattedCaption = null,
                    inReplyToEventId = null,
                )
                    .getOrThrow()
                supervisorScope {
                    val completion = async { withContext(NonCancellable) { upload.await() } }
                    try {
                        // SDK reads the file until its upload handle completes.
                        completion.await().getOrThrow()
                    } catch (error: Exception) {
                        upload.cancel()
                        withContext(NonCancellable) { completion.join() }
                        throw error
                    }
                }
                Unit
            } finally {
                // Файл ушёл на сервер, держать копию в кеше незачем.
                file.delete()
            }
        }
    }

    private fun download(gif: Gif): File {
        val request = Request.Builder().url(gif.url).build()
        return okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("не удалось скачать гифку: ${response.code}")
            val body = response.body ?: error("пустой ответ при скачивании гифки")
            cacheDir.mkdirs()
            val target = File.createTempFile("larpgram-gif-", ".gif", cacheDir)
            try {
                target.outputStream().use { output ->
                    body.byteStream().use { input -> input.copyTo(output) }
                }
                target
            } catch (error: Exception) {
                target.delete()
                throw error
            }
        }
    }

    private companion object {
        const val MIME_TYPE_GIF = "image/gif"
    }
}
