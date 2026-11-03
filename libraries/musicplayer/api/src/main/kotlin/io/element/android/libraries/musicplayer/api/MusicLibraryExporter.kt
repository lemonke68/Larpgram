/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.musicplayer.api

/**
 * «Сохранить в Музыку»: копия трека в общей папке Music/Larpgram, её видят другие плееры.
 * До Android 10 нужно WRITE_EXTERNAL_STORAGE, спросить его должен экран, который вызывает.
 */
fun interface MusicLibraryExporter {
    suspend fun export(track: MusicTrack): Result<Unit>
}
