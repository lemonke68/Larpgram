/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.musicplayer.api

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MusicFilenamesTest {
    @Test
    fun `artist and title are split on the first dash`() {
        assertThat(MusicFilenames.parse("Daft Punk - Veridis Quo.flac"))
            .isEqualTo(MusicFilenames.Parsed(title = "Veridis Quo", artist = "Daft Punk"))
    }

    @Test
    fun `a leading track number is dropped`() {
        assertThat(MusicFilenames.parse("01 - Daft Punk - Veridis Quo.flac"))
            .isEqualTo(MusicFilenames.Parsed(title = "Veridis Quo", artist = "Daft Punk"))
        assertThat(MusicFilenames.parse("07. Intro.mp3")).isEqualTo(MusicFilenames.Parsed(title = "Intro", artist = null))
    }

    @Test
    fun `a number without a separator is part of the title`() {
        assertThat(MusicFilenames.parse("99 Luftballons.mp3")).isEqualTo(MusicFilenames.Parsed(title = "99 Luftballons", artist = null))
    }

    @Test
    fun `a name without extension or dash is the title`() {
        assertThat(MusicFilenames.parse("track")).isEqualTo(MusicFilenames.Parsed(title = "track", artist = null))
    }

    @Test
    fun `tags win over the filename`() {
        val track = aMusicTrack(filename = "01 - A - B.mp3")
        assertThat(track.displayTitle(MusicTrackTags(title = "Real title"))).isEqualTo("Real title")
        assertThat(track.displayArtist(MusicTrackTags(title = "Real title"))).isEqualTo("A")
        assertThat(track.displayTitle(MusicTrackTags(title = " "))).isEqualTo("B")
        assertThat(track.displayTitle(null)).isEqualTo("B")
    }
}
