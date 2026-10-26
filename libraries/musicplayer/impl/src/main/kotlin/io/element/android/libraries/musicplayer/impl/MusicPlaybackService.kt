/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.musicplayer.impl

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import io.element.android.libraries.architecture.bindings
import io.element.android.libraries.designsystem.utils.CommonDrawables

@ContributesTo(AppScope::class)
interface MusicPlayerBindings {
    fun inject(service: MusicPlaybackService)
}

/**
 * Фоновое воспроизведение музыки: MediaSession над общим плеером [DefaultMusicPlayer].
 * media3 сам ведёт foreground-уведомление (MediaStyle, в том числе на Android 7), экран
 * блокировки и кнопки гарнитуры.
 */
class MusicPlaybackService : MediaSessionService() {
    @Inject lateinit var musicPlayer: DefaultMusicPlayer

    private var session: MediaSession? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        bindings<MusicPlayerBindings>().inject(this)
        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(CHANNEL_ID)
            .setChannelName(R.string.larpgram_music_notification_channel)
            .setNotificationId(NOTIFICATION_ID)
            .build()
        notificationProvider.setSmallIcon(CommonDrawables.ic_notification)
        setMediaNotificationProvider(notificationProvider)
        val builder = MediaSession.Builder(this, musicPlayer.sessionPlayer())
        packageManager.getLaunchIntentForPackage(packageName)?.let { launchIntent ->
            builder.setSessionActivity(
                PendingIntent.getActivity(this, 0, launchIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )
        }
        session = builder.build()
        musicPlayer.stopService = { stopSelf() }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!musicPlayer.onTaskRemoved()) stopSelf()
    }

    override fun onDestroy() {
        musicPlayer.stopService = null
        session?.release()
        session = null
        musicPlayer.onServiceDestroyed()
        super.onDestroy()
    }

    private companion object {
        const val CHANNEL_ID = "larpgram_music"

        // Вне диапазона id уведомлений сессий (NotificationIdProvider: до ~1 000 000).
        const val NOTIFICATION_ID = 2_000_001
    }
}
