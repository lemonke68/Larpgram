/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.musicplayer.impl

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MusicLibraryExporterTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `a free name is kept`() {
        assertThat(uniqueFile(folder.root, "demo.flac").name).isEqualTo("demo.flac")
    }

    @Test
    fun `a taken name gets a counter before the extension`() {
        folder.newFile("demo.flac")
        folder.newFile("demo (2).flac")
        assertThat(uniqueFile(folder.root, "demo.flac").name).isEqualTo("demo (3).flac")
        folder.newFile("readme")
        assertThat(uniqueFile(folder.root, "readme").name).isEqualTo("readme (2)")
    }

    @Test
    fun `unsafe names are cleaned`() {
        assertThat(musicExportName("a/b.mp3")).isEqualTo("a_b.mp3")
        assertThat(musicExportName("..")).isEqualTo("track")
        assertThat(musicExportName("  ")).isEqualTo("track")
    }
}
