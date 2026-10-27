/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.musicplayer.impl

import io.element.android.libraries.musicplayer.api.MusicOrder
import io.element.android.libraries.musicplayer.api.MusicTrackTags

class InMemoryTrackPositionStore : TrackPositionStore {
    val positions = mutableMapOf<String, Long>()
    override fun get(key: String): Long? = positions[key]
    override fun set(key: String, positionMs: Long) {
        positions[key] = positionMs
    }
    override fun clear(key: String) {
        positions.remove(key)
    }
}

class InMemoryTrackTagsStore(initial: Map<String, MusicTrackTags> = emptyMap()) : TrackTagsStore {
    val tags = initial.toMutableMap()
    override fun loadAll(): Map<String, MusicTrackTags> = tags.toMap()
    override fun put(key: String, tags: MusicTrackTags) {
        this.tags[key] = tags
    }
}

class FakeMusicServiceConnector : MusicServiceConnector {
    var isConnected = false
    override fun connect() {
        isConnected = true
    }
    override fun disconnect() {
        isConnected = false
    }
}

class InMemoryMusicSettingsStore(
    override var speed: Float = 1f,
    override var order: MusicOrder = MusicOrder.Default,
) : MusicSettingsStore
