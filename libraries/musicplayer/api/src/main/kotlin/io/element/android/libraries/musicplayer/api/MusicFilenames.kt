/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.musicplayer.api

/**
 * Запасной вариант, пока тегов нет: «01 - Artist - Title.flac» → артист и название.
 */
object MusicFilenames {
    data class Parsed(val title: String, val artist: String?)

    // Номер трека только с разделителем: «99 Luftballons» — это название, а не номер.
    private val trackNumber = Regex("""^\d{1,3}\s*[-._)]\s*""")

    fun parse(filename: String): Parsed {
        val base = filename.substringBeforeLast('.', filename).trim().ifEmpty { filename }
        val noNumber = base.replaceFirst(trackNumber, "").trim().ifEmpty { base }
        val parts = noNumber.split(" - ", limit = 2).map { it.trim() }
        return if (parts.size == 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty()) {
            Parsed(title = parts[1], artist = parts[0])
        } else {
            Parsed(title = noNumber, artist = null)
        }
    }
}
