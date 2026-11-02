/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.stickers.impl

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.zacsweers.metro.Inject
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.matrix.api.MatrixClient
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.IOException

private val Context.recentStickersDataStore by preferencesDataStore(name = "larpgram_recent_stickers")

/** The list is kept on this device and isolated per account. */
@Inject
class RecentStickersStore(
    @ApplicationContext private val context: Context,
    client: MatrixClient,
) {
    private val key = stringPreferencesKey(client.sessionId.value)

    suspend fun getRecent(): List<String> = try {
        decode(context.recentStickersDataStore.data.first()[key])
    } catch (error: IOException) {
        Timber.w(error, "не удалось прочитать недавние стикеры")
        emptyList()
    }

    suspend fun remember(url: String) {
        context.recentStickersDataStore.edit { prefs ->
            val updated = (listOf(url) + decode(prefs[key]).filterNot { it == url }).take(MAX_RECENT)
            prefs[key] = Json.encodeToString(updated)
        }
    }

    private fun decode(raw: String?): List<String> =
        raw?.let { runCatching { Json.decodeFromString<List<String>>(it) }.getOrNull() }.orEmpty()

    private companion object {
        const val MAX_RECENT = 40
    }
}
