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
import io.element.android.libraries.musicplayer.api.MusicOrder

/** Скорость и порядок плеера переживают перезапуск приложения, как в Telegram. */
interface MusicSettingsStore {
    var speed: Float
    var order: MusicOrder
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class SharedPreferencesMusicSettingsStore(
    @ApplicationContext context: Context,
) : MusicSettingsStore {
    private val prefs = context.getSharedPreferences("larpgram_music_settings", Context.MODE_PRIVATE)

    override var speed: Float
        get() = prefs.getFloat(KEY_SPEED, 1f)
        set(value) = prefs.edit { putFloat(KEY_SPEED, value) }

    override var order: MusicOrder
        get() = prefs.getString(KEY_ORDER, null)?.let { name -> MusicOrder.entries.firstOrNull { it.name == name } } ?: MusicOrder.Default
        set(value) = prefs.edit { putString(KEY_ORDER, value.name) }

    private companion object {
        const val KEY_SPEED = "speed"
        const val KEY_ORDER = "order"
    }
}
