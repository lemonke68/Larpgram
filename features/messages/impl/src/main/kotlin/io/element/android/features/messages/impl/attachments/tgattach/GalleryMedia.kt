/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.attachments.tgattach

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import io.element.android.libraries.core.extensions.runCatchingExceptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Правка форка: фото или видео из галереи устройства для меню вложений Telegram. */
@Immutable
data class GalleryMedia(
    val id: Long,
    val uri: Uri,
    val mimeType: String,
    val isVideo: Boolean,
    val durationMs: Long,
)

/** Какой доступ к галерее дал пользователь. [Partial] — «выбранные фото» Android 14. */
internal enum class GalleryAccess { Full, Partial, None }

internal object GalleryPermissions {
    val requested: Array<String>
        get() = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
            )
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    fun access(context: Context): GalleryAccess {
        fun granted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> when {
                granted(Manifest.permission.READ_MEDIA_IMAGES) || granted(Manifest.permission.READ_MEDIA_VIDEO) -> GalleryAccess.Full
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                    granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> GalleryAccess.Partial
                else -> GalleryAccess.None
            }
            granted(Manifest.permission.READ_EXTERNAL_STORAGE) -> GalleryAccess.Full
            else -> GalleryAccess.None
        }
    }

    /** Разрешение, по которому спрашиваем `shouldShowRequestPermissionRationale`. */
    val main: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
}

/** Фото и видео устройства, новые сверху, как «Недавние» в Telegram. */
internal object GalleryMediaStore {
    suspend fun load(context: Context): List<GalleryMedia> = withContext(Dispatchers.IO) {
        runCatchingExceptions { query(context) }
            .onFailure { Timber.w(it, "Failed to load gallery media") }
            .getOrDefault(emptyList())
    }

    private fun query(context: Context): List<GalleryMedia> {
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Video.VideoColumns.DURATION,
        )
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (" +
            "${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}, ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})"
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
        val result = ArrayList<GalleryMedia>()
        context.contentResolver.query(collection, projection, selection, null, sortOrder)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val typeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val durationColumn = cursor.getColumnIndex(MediaStore.Video.VideoColumns.DURATION)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val isVideo = cursor.getInt(typeColumn) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                val base = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                val mimeType = cursor.getString(mimeColumn) ?: if (isVideo) "video/mp4" else "image/jpeg"
                result += GalleryMedia(
                    id = id,
                    uri = ContentUris.withAppendedId(base, id),
                    mimeType = mimeType,
                    isVideo = isVideo,
                    durationMs = if (isVideo && durationColumn >= 0) cursor.getLong(durationColumn) else 0L,
                )
            }
        }
        return result
    }
}

/**
 * Миниатюры плиток галереи. Берём готовые из MediaStore (они есть и у видео, Coil без видеодекодера
 * их не умеет), держим в памяти, пока процесс жив.
 */
internal object GalleryThumbnails {
    private val cache = object : LruCache<Long, ImageBitmap>(cacheSizeBytes()) {
        override fun sizeOf(key: Long, value: ImageBitmap): Int = value.width * value.height * 4
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val dispatcher = Dispatchers.IO.limitedParallelism(4)

    fun cached(media: GalleryMedia): ImageBitmap? = cache.get(media.id)

    suspend fun load(context: Context, media: GalleryMedia, sizePx: Int): ImageBitmap? {
        cache.get(media.id)?.let { return it }
        return withContext(dispatcher) {
            runCatchingExceptions { loadBitmap(context, media, sizePx) }
                .onFailure { Timber.d(it, "No thumbnail for ${media.id}") }
                .getOrNull()
                ?.asImageBitmap()
                ?.also { cache.put(media.id, it) }
        }
    }

    @Suppress("DEPRECATION")
    private fun loadBitmap(context: Context, media: GalleryMedia, sizePx: Int): Bitmap? {
        val resolver = context.contentResolver
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.loadThumbnail(media.uri, Size(sizePx, sizePx), null)
        } else if (media.isVideo) {
            MediaStore.Video.Thumbnails.getThumbnail(resolver, media.id, MediaStore.Video.Thumbnails.MINI_KIND, null)
        } else {
            MediaStore.Images.Thumbnails.getThumbnail(resolver, media.id, MediaStore.Images.Thumbnails.MINI_KIND, null)
        }
    }

    private fun cacheSizeBytes(): Int = (Runtime.getRuntime().maxMemory() / 8).coerceAtMost(48L * 1024 * 1024).toInt()
}
