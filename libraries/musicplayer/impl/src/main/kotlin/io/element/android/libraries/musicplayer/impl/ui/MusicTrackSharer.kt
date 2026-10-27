/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.musicplayer.impl.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.musicplayer.api.MusicTrack
import io.element.android.libraries.musicplayer.impl.MusicTrackResolver
import kotlinx.coroutines.withContext
import java.io.File

/** Кнопка «поделиться» шторки: системное меню с расшифрованным файлом трека. */
fun interface MusicTrackSharer {
    suspend fun share(track: MusicTrack): Result<Unit>
}

@ContributesBinding(AppScope::class)
class AndroidMusicTrackSharer(
    @ApplicationContext private val context: Context,
    private val resolver: MusicTrackResolver,
    private val dispatchers: CoroutineDispatchers,
) : MusicTrackSharer {
    override suspend fun share(track: MusicTrack): Result<Unit> = withContext(dispatchers.io) {
        runCatching {
            // Копия в кэше с настоящим именем: файл плеера может закрыться, пока выбирают, куда слать.
            val source = resolver.resolve(track)
            val name = track.filename.replace('/', '_').ifBlank { "track" }
            val copy = source.copyTo(File(context.cacheDir, "temp/music/$name"), overwrite = true)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", copy)
            val send = Intent(Intent.ACTION_SEND)
                .putExtra(Intent.EXTRA_STREAM, uri)
                .setTypeAndNormalize(track.mimeType?.takeIf { it.startsWith("audio/") } ?: "audio/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val chooser = Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            withContext(dispatchers.main) { context.startActivity(chooser) }
        }
    }
}
