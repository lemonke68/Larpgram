/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.mediaviewer.impl.model

import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.mediaviewer.impl.gallery.MediaGalleryMode
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

data class GroupedMediaItems(
    val imageAndVideoItems: ImmutableList<MediaItem>,
    val fileItems: ImmutableList<MediaItem>,
    // Правка форка: голосовые и кружочки вместе, в порядке ленты — вкладка «Голосовые» профиля,
    // как в Telegram (аудит A-025). Кружочки остаются и в imageAndVideoItems для галереи Element.
    val voiceAndCircleItems: ImmutableList<MediaItem> = persistentListOf(),
) {
    fun getItems(mode: MediaGalleryMode): ImmutableList<MediaItem> {
        return when (mode) {
            MediaGalleryMode.Images -> imageAndVideoItems
            MediaGalleryMode.Files -> fileItems
        }
    }
}

fun GroupedMediaItems.hasEvent(eventId: EventId): Boolean {
    return (fileItems + imageAndVideoItems)
        .filterIsInstance<MediaItem.Event>()
        .any { it.eventId() == eventId }
}
