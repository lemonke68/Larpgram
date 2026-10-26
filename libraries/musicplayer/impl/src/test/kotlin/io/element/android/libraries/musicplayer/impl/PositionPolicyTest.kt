/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.musicplayer.impl

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PositionPolicyTest {
    private val long = 60 * 60 * 1000L

    @Test
    fun `position of a long recording is saved`() {
        assertThat(PositionPolicy.positionToSave(positionMs = 120_000, durationMs = long)).isEqualTo(120_000)
    }

    @Test
    fun `position of a song is not saved`() {
        assertThat(PositionPolicy.positionToSave(positionMs = 120_000, durationMs = 4 * 60_000)).isNull()
    }

    @Test
    fun `the very start and the end are not saved`() {
        assertThat(PositionPolicy.positionToSave(positionMs = 1_000, durationMs = long)).isNull()
        assertThat(PositionPolicy.positionToSave(positionMs = long - 5_000, durationMs = long)).isNull()
    }

    @Test
    fun `restore uses the saved position unless it is at the end`() {
        assertThat(PositionPolicy.positionToRestore(savedMs = 120_000, durationMs = null)).isEqualTo(120_000)
        assertThat(PositionPolicy.positionToRestore(savedMs = 120_000, durationMs = long)).isEqualTo(120_000)
        assertThat(PositionPolicy.positionToRestore(savedMs = long - 1_000, durationMs = long)).isEqualTo(0)
        assertThat(PositionPolicy.positionToRestore(savedMs = null, durationMs = long)).isEqualTo(0)
        assertThat(PositionPolicy.positionToRestore(savedMs = 2_000, durationMs = long)).isEqualTo(0)
    }
}
