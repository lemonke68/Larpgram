/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.musicplayer.impl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import dev.zacsweers.metro.Inject
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.musicplayer.api.MusicTrackTags
import timber.log.Timber
import java.io.File
import java.security.MessageDigest

/** Теги и обложка из скачанного файла через MediaMetadataRetriever (есть с API 10). */
@Inject
class MusicTagsExtractor(
    @ApplicationContext private val context: Context,
) {
    fun extract(file: File, mediaKey: String): MusicTrackTags? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            fun read(key: Int) = retriever.extractMetadata(key)?.trim()?.takeIf { it.isNotEmpty() }
            val artist = read(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: read(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
            MusicTrackTags(
                title = read(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = artist,
                album = read(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                artworkPath = retriever.embeddedPicture?.let { saveArtwork(it, mediaKey) },
            )
        } catch (e: Exception) {
            Timber.w(e, "Music: could not read tags")
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    /**
     * Обложка в размер экрана: на весь экран её показывают по короткой стороне, больше не нужно.
     * Если исходник не больше, он сохраняется как есть, без пережатия.
     */
    private fun saveArtwork(bytes: ByteArray, mediaKey: String): String? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val target = artworkTargetSize()
        val dir = File(context.filesDir, "music_artwork").apply { mkdirs() }
        val name = sha1(mediaKey)
        // Обложка прошлого формата (512px) больше не нужна.
        File(dir, "$name.jpg").delete()
        val longSide = maxOf(bounds.outWidth, bounds.outHeight)
        val isPlainImage = bounds.outMimeType == "image/jpeg" || bounds.outMimeType == "image/png"
        if (longSide <= target && isPlainImage) {
            val extension = if (bounds.outMimeType == "image/png") "png" else "jpg"
            val file = File(dir, "${name}$ARTWORK_VERSION.$extension")
            return try {
                file.writeBytes(bytes)
                file.absolutePath
            } catch (e: Exception) {
                Timber.w(e, "Music: could not save artwork")
                null
            }
        }
        var sample = 1
        while (longSide / (sample * 2) >= target) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val scale = target.toFloat() / maxOf(decoded.width, decoded.height)
        val bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
        } else {
            decoded
        }
        val file = File(dir, "${name}$ARTWORK_VERSION.jpg")
        return try {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            file.absolutePath
        } catch (e: Exception) {
            Timber.w(e, "Music: could not save artwork")
            null
        } finally {
            if (bitmap !== decoded) bitmap.recycle()
            decoded.recycle()
        }
    }

    private fun artworkTargetSize(): Int {
        val metrics = context.resources.displayMetrics
        return minOf(metrics.widthPixels, metrics.heightPixels).coerceIn(ARTWORK_MIN_SIZE, ARTWORK_MAX_SIZE)
    }

    private fun sha1(value: String): String =
        MessageDigest.getInstance("SHA-1").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        /** Метка в имени файла: обложки без неё сохранены старым способом и читаются заново. */
        const val ARTWORK_VERSION = "_v2"
        private const val ARTWORK_MIN_SIZE = 512
        private const val ARTWORK_MAX_SIZE = 2048
    }
}
