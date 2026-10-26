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
import io.element.android.libraries.musicplayer.api.MusicTrackTags
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Теги уже скачанных треков, чтобы экран «Музыка» показывал их без повторной загрузки. */
interface TrackTagsStore {
    fun loadAll(): Map<String, MusicTrackTags>
    fun put(key: String, tags: MusicTrackTags)
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class SharedPreferencesTrackTagsStore(
    @ApplicationContext context: Context,
) : TrackTagsStore {
    private val prefs = context.getSharedPreferences("larpgram_music_tags", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    override fun loadAll(): Map<String, MusicTrackTags> = prefs.all.mapNotNull { (key, value) ->
        val tags = (value as? String)?.let { runCatching { json.decodeFromString<StoredTags>(it) }.getOrNull() }
        tags?.let { key to it.toTags() }
    }.toMap()

    override fun put(key: String, tags: MusicTrackTags) {
        prefs.edit { putString(key, json.encodeToString(StoredTags.from(tags))) }
    }
}

@Serializable
private data class StoredTags(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val artworkPath: String? = null,
) {
    fun toTags() = MusicTrackTags(title = title, artist = artist, album = album, artworkPath = artworkPath)

    companion object {
        fun from(tags: MusicTrackTags) = StoredTags(tags.title, tags.artist, tags.album, tags.artworkPath)
    }
}
