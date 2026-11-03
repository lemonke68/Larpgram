/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.musicplayer.api

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MusicFilesTest {
    @Test
    fun `any audio mime is playable`() {
        assertThat(MusicFiles.isPlayable("audio/flac", "demo")).isTrue()
        assertThat(MusicFiles.isPlayable("audio/mpeg", "demo.bin")).isTrue()
    }

    @Test
    fun `a file sent as a document is playable by its extension`() {
        assertThat(MusicFiles.isPlayable("application/octet-stream", "01 - Demo.FLAC")).isTrue()
        assertThat(MusicFiles.isPlayable(null, "demo.wav")).isTrue()
        assertThat(MusicFiles.isPlayable("", "demo.opus")).isTrue()
    }

    @Test
    fun `other files are not playable`() {
        assertThat(MusicFiles.isPlayable("application/octet-stream", "demo.ape")).isFalse()
        assertThat(MusicFiles.isPlayable("application/pdf", "demo.flac")).isFalse()
        assertThat(MusicFiles.isPlayable("application/octet-stream", "flac")).isFalse()
    }
}
