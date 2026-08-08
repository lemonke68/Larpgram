/*
 * Модуль форка: гифки через свой прокси к Giphy.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.gifs.impl

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber

private val Context.recentGifsDataStore by preferencesDataStore(name = "larpgram_recent_gifs")

@Serializable
private data class StoredGif(
    val id: String,
    val description: String = "",
    val url: String,
    @SerialName("previewUrl") val previewUrl: String,
    val width: Long? = null,
    val height: Long? = null,
    val size: Long? = null,
)

/**
 * Недавно отправленные гифки, хранятся на устройстве.
 *
 * Зачем: бесплатный ключ Giphy это около сотни запросов в час на всех пользователей, а
 * люди чаще всего переотправляют одно и то же. Недавние показываются без единого запроса
 * наружу, как вкладка с сохранёнными в Telegram.
 *
 * Хранится локально, а не в account data: список меняется на каждой отправке, и гонять
 * из-за него запросы к серверу незачем. Плата: на новом устройстве список пуст.
 */
class RecentGifsStore(
    private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getRecent(): List<Gif> {
        val raw = context.recentGifsDataStore.data.first()[KEY_RECENT] ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<StoredGif>>(raw).map {
                Gif(
                    id = it.id,
                    description = it.description,
                    url = it.url,
                    previewUrl = it.previewUrl,
                    width = it.width,
                    height = it.height,
                    size = it.size,
                )
            }
        }.getOrElse {
            Timber.e(it, "не удалось прочитать недавние гифки")
            emptyList()
        }
    }

    /** Добавляет гифку в начало списка, без дублей. */
    suspend fun remember(gif: Gif) {
        val current = getRecent().filterNot { it.id == gif.id }
        val updated = (listOf(gif) + current).take(MAX_RECENT)
        val serialized = json.encodeToString(
            updated.map {
                StoredGif(
                    id = it.id,
                    description = it.description,
                    url = it.url,
                    previewUrl = it.previewUrl,
                    width = it.width,
                    height = it.height,
                    size = it.size,
                )
            }
        )
        context.recentGifsDataStore.edit { it[KEY_RECENT] = serialized }
    }

    private companion object {
        val KEY_RECENT = stringPreferencesKey("recent_gifs")

        /** Больше сотни в ленте всё равно никто не пролистает, а место занимать будет. */
        const val MAX_RECENT = 40
    }
}
