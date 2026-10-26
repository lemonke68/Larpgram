/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.musicplayer.impl

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.element.android.libraries.di.annotations.ApplicationContext
import kotlinx.coroutines.runBlocking
import java.io.IOException

/** Создаёт плеер музыки; в тестах подменяется. */
fun interface MusicPlayerFactory {
    fun create(): Player
}

@ContributesBinding(AppScope::class)
class DefaultMusicPlayerFactory(
    @ApplicationContext private val context: Context,
    private val dataSourceResolver: MusicDataSourceResolver,
) : MusicPlayerFactory {
    @OptIn(UnstableApi::class)
    override fun create(): Player {
        val dataSourceFactory = ResolvingDataSource.Factory(DefaultDataSource.Factory(context), dataSourceResolver)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            // Системный фокус ведёт сам ExoPlayer: звонок / чужой плеер — пауза, транзиентная
            // потеря (голосовое, запись) — пауза и продолжение.
            .setAudioAttributes(audioAttributes, true)
            // Наушники или USB-ЦАП выдернули — пауза.
            .setHandleAudioBecomingNoisy(true)
            // Трек может качаться с сервера при выключенном экране.
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
    }
}

/**
 * larpgram-music://track/<id> → локальный файл. Вызывается на потоке загрузки ExoPlayer, поэтому
 * блокирующее ожидание здесь допустимо; прерывание потока отменяет ожидание (не саму загрузку).
 */
@OptIn(UnstableApi::class)
@Inject
class MusicDataSourceResolver(
    private val registry: MusicTrackRegistry,
    private val resolver: MusicTrackResolver,
) : ResolvingDataSource.Resolver {
    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val id = MusicTrackRegistry.idFrom(dataSpec.uri) ?: return dataSpec
        val track = registry.get(id) ?: throw IOException("Unknown music track")
        val file = try {
            runBlocking { resolver.resolve(track) }
        } catch (e: InterruptedException) {
            throw java.io.InterruptedIOException().apply { initCause(e) }
        }
        return dataSpec.withUri(Uri.fromFile(file))
    }
}
