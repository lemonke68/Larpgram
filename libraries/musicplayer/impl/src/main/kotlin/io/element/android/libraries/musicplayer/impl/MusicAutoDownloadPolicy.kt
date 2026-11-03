/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.musicplayer.impl

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.musicplayer.api.MusicAutoDownload
import io.element.android.libraries.musicplayer.api.MusicAutoDownloadStore
import io.element.android.libraries.musicplayer.api.MusicTrack

/** «Wi-Fi» в настройке означает любую безлимитную сеть: домашний Ethernet тоже подходит. */
fun interface UnmeteredNetworkCheck {
    fun isUnmetered(): Boolean
}

@ContributesBinding(AppScope::class)
class AndroidUnmeteredNetworkCheck(
    @ApplicationContext private val context: Context,
) : UnmeteredNetworkCheck {
    override fun isUnmetered(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
}

/** Можно ли качать трек заранее, не дожидаясь, пока его включат. */
@Inject
class MusicAutoDownloadPolicy(
    private val store: MusicAutoDownloadStore,
    private val network: UnmeteredNetworkCheck,
) {
    fun allows(track: MusicTrack): Boolean {
        val settings = store.settings.value
        val networkAllowed = when (settings.network) {
            MusicAutoDownload.Network.Never -> false
            MusicAutoDownload.Network.WifiOnly -> network.isUnmetered()
            MusicAutoDownload.Network.Always -> true
        }
        // При ограничении трек неизвестного размера не качаем: это может быть длинный lossless.
        val limit = settings.maxSizeBytes
        val sizeAllowed = limit == null || track.fileSize?.let { it <= limit } == true
        return networkAllowed && sizeAllowed
    }
}
