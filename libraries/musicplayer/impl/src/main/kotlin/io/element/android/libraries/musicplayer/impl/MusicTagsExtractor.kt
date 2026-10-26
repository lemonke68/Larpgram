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

    /** Обложку уменьшаем до ~512px: в списке и уведомлении больше не нужно. */
    private fun saveArtwork(bytes: ByteArray, mediaKey: String): String? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= ARTWORK_SIZE) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val dir = File(context.filesDir, "music_artwork").apply { mkdirs() }
        val file = File(dir, sha1(mediaKey) + ".jpg")
        return try {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            file.absolutePath
        } catch (e: Exception) {
            Timber.w(e, "Music: could not save artwork")
            null
        } finally {
            bitmap.recycle()
        }
    }

    private fun sha1(value: String): String =
        MessageDigest.getInstance("SHA-1").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val ARTWORK_SIZE = 512
    }
}
