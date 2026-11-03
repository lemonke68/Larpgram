/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.musicplayer.api

object MusicFiles {
    // Только то, что ExoPlayer разбирает сам. APE, DSD и WMA сюда не попадают.
    private val extensions = setOf("flac", "wav", "aiff", "aif", "m4a", "alac", "mp3", "ogg", "oga", "opus", "aac")

    /**
     * Аудио, которое можно отдать плееру. С ПК lossless часто приходит документом с
     * `application/octet-stream`, поэтому без аудио-mime смотрим на расширение.
     */
    fun isPlayable(mimeType: String?, filename: String): Boolean {
        if (mimeType?.startsWith("audio/") == true) return true
        val genericMime = mimeType.isNullOrBlank() || mimeType == "application/octet-stream"
        return genericMime && filename.substringAfterLast('.', "").lowercase() in extensions
    }
}
