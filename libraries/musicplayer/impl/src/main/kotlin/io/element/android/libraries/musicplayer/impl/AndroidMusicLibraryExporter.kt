/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.musicplayer.impl

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.musicplayer.api.MusicLibraryExporter
import io.element.android.libraries.musicplayer.api.MusicTrack
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

internal const val MUSIC_LIBRARY_FOLDER = "Larpgram"

@ContributesBinding(AppScope::class)
class AndroidMusicLibraryExporter(
    @ApplicationContext private val context: Context,
    private val resolver: MusicTrackResolver,
    private val dispatchers: CoroutineDispatchers,
    private val copyPolicy: MusicCopyPolicy,
) : MusicLibraryExporter {
    override suspend fun export(track: MusicTrack): Result<Unit> = withContext(dispatchers.io) {
        runCatchingExceptions {
            copyPolicy.requireAllowed(track)
            val name = musicExportName(track.filename)
            val temp = File(context.cacheDir, "temp/music/${UUID.randomUUID()}/$name")
            try {
                val file = resolver.copyForExport(track, temp)
                val mimeType = track.mimeType?.takeIf { it.startsWith("audio/") }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    insertIntoMediaStore(file, name, mimeType)
                } else {
                    copyToPublicMusic(file, name, mimeType)
                }
            } finally {
                temp.parentFile?.deleteRecursively()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun insertIntoMediaStore(file: File, name: String, mimeType: String?) {
        val contentResolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, name)
            mimeType?.let { put(MediaStore.Audio.Media.MIME_TYPE, it) }
            put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/$MUSIC_LIBRARY_FOLDER")
            put(MediaStore.Audio.Media.IS_MUSIC, 1)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        // MediaStore сам добавит « (1)», если такое имя в папке уже есть.
        val uri = contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("MediaStore refused the track")
        try {
            contentResolver.openOutputStream(uri).use { output ->
                requireNotNull(output) { "No output stream for $uri" }
                file.inputStream().use { it.copyTo(output) }
            }
            contentResolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            contentResolver.delete(uri, null, null)
            throw e
        }
    }

    /** До Android 10: прямой путь, нужно WRITE_EXTERNAL_STORAGE (его спрашивает шторка). */
    @Suppress("DEPRECATION")
    private fun copyToPublicMusic(file: File, name: String, mimeType: String?) {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), MUSIC_LIBRARY_FOLDER)
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Cannot create $dir")
        val target = uniqueFile(dir, name)
        file.copyTo(target)
        MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(mimeType), null)
    }
}

internal fun musicExportName(filename: String): String =
    filename.replace('/', '_').trim().takeUnless { it.isEmpty() || it == "." || it == ".." } ?: "track"

/** «demo.flac» → «demo (2).flac», если первое имя занято. */
internal fun uniqueFile(dir: File, name: String): File {
    val first = File(dir, name)
    if (!first.exists()) return first
    val base = name.substringBeforeLast('.', name)
    val ext = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
    return generateSequence(2) { it + 1 }
        .map { File(dir, "$base ($it)$ext") }
        .first { !it.exists() }
}
