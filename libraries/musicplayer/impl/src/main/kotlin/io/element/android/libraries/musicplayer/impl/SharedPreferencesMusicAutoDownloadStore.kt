/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.musicplayer.impl

import android.content.Context
import androidx.core.content.edit
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.musicplayer.api.MusicAutoDownload
import io.element.android.libraries.musicplayer.api.MusicAutoDownloadStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class SharedPreferencesMusicAutoDownloadStore(
    @ApplicationContext context: Context,
) : MusicAutoDownloadStore {
    private val prefs = context.getSharedPreferences("larpgram_music_settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    override val settings: StateFlow<MusicAutoDownload> = _settings.asStateFlow()

    override fun update(settings: MusicAutoDownload) {
        prefs.edit {
            putString(KEY_NETWORK, settings.network.name)
            putLong(KEY_MAX_SIZE, settings.maxSizeBytes ?: NO_LIMIT)
        }
        _settings.value = settings
    }

    private fun read(): MusicAutoDownload {
        val default = MusicAutoDownload.Default
        val network = prefs.getString(KEY_NETWORK, null)
            ?.let { name -> MusicAutoDownload.Network.entries.firstOrNull { it.name == name } }
            ?: default.network
        val maxSize = if (prefs.contains(KEY_MAX_SIZE)) prefs.getLong(KEY_MAX_SIZE, NO_LIMIT).takeIf { it != NO_LIMIT } else default.maxSizeBytes
        return MusicAutoDownload(network, maxSize)
    }

    private companion object {
        const val KEY_NETWORK = "auto_download_network"
        const val KEY_MAX_SIZE = "auto_download_max_size"
        const val NO_LIMIT = -1L
    }
}
