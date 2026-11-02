/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.api.media

/** Правка форка: размеры записей медиакэша, отдельно от выделенного места БД и активных отправок. */
data class MediaCacheUsage(
    val images: Long,
    val videos: Long,
    val audio: Long,
    val other: Long,
    val thumbnails: Long,
    val protectedBytes: Long,
    val databaseBytes: Long?,
) {
    val removableBytes: Long get() = images + videos + audio + other + thumbnails
    val databaseOverheadBytes: Long? get() = databaseBytes?.let { (it - removableBytes - protectedBytes).coerceAtLeast(0) }
}
