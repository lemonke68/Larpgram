/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.musicplayer.impl

import android.content.ComponentName
import android.content.Context
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.di.annotations.ApplicationContext
import timber.log.Timber

/**
 * Держит сервис воспроизведения живым, пока есть очередь: подключённый MediaController
 * привязывает MediaSessionService, а дальше media3 сам переводит его в foreground на play.
 */
interface MusicServiceConnector {
    fun connect()
    fun disconnect()
}

@ContributesBinding(AppScope::class)
class MediaControllerMusicServiceConnector(
    @ApplicationContext private val context: Context,
) : MusicServiceConnector {
    private var controllerFuture: ListenableFuture<MediaController>? = null

    override fun connect() {
        if (controllerFuture != null) return
        val token = SessionToken(context, ComponentName(context, MusicPlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, token).buildAsync().also { future ->
            future.addListener({
                runCatching { future.get() }.onFailure { Timber.e(it, "Music: could not connect to the playback service") }
            }, context.mainExecutor())
        }
    }

    override fun disconnect() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
    }
}

private fun Context.mainExecutor() = androidx.core.content.ContextCompat.getMainExecutor(this)
