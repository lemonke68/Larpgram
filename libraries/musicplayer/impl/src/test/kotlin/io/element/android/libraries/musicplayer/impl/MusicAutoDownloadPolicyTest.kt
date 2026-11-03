/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.musicplayer.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.musicplayer.api.MusicAutoDownload
import io.element.android.libraries.musicplayer.api.MusicAutoDownload.Network
import io.element.android.libraries.musicplayer.test.FakeMusicAutoDownloadStore
import io.element.android.libraries.musicplayer.test.aMusicTrack
import org.junit.Test

class MusicAutoDownloadPolicyTest {
    private val small = aMusicTrack().copy(fileSize = 5L * 1024 * 1024)
    private val big = aMusicTrack().copy(fileSize = 80L * 1024 * 1024)
    private val unknownSize = aMusicTrack().copy(fileSize = null)

    private fun policy(network: Network, maxMb: Long?, unmetered: Boolean) = MusicAutoDownloadPolicy(
        FakeMusicAutoDownloadStore(MusicAutoDownload(network, maxMb?.let { it * 1024 * 1024 })),
    ) { unmetered }

    @Test
    fun `wifi only waits for an unmetered network`() {
        assertThat(policy(Network.WifiOnly, 50, unmetered = true).allows(small)).isTrue()
        assertThat(policy(Network.WifiOnly, 50, unmetered = false).allows(small)).isFalse()
    }

    @Test
    fun `files over the limit or of unknown size are skipped`() {
        val limited = policy(Network.Always, 50, unmetered = false)
        assertThat(limited.allows(big)).isFalse()
        assertThat(limited.allows(unknownSize)).isFalse()
        assertThat(policy(Network.Always, null, unmetered = false).allows(big)).isTrue()
        assertThat(policy(Network.Always, null, unmetered = false).allows(unknownSize)).isTrue()
    }

    @Test
    fun `never loads nothing ahead`() {
        assertThat(policy(Network.Never, null, unmetered = true).allows(small)).isFalse()
    }
}
