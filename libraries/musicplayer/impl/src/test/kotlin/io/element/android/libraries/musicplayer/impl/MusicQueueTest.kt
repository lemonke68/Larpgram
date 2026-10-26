/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.musicplayer.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.musicplayer.api.MusicRepeatMode
import io.element.android.libraries.musicplayer.test.aMusicTrack
import org.junit.Test

class MusicQueueTest {
    private val a = aMusicTrack(id = "a")
    private val b = aMusicTrack(id = "b")
    private val c = aMusicTrack(id = "c")
    private val d = aMusicTrack(id = "d")

    @Test
    fun `build starts at the tapped track and keeps the timeline order`() {
        val built = MusicQueue.build(listOf(a, b, c), startTrackId = "b")
        assertThat(built.tracks).containsExactly(a, b, c).inOrder()
        assertThat(built.startIndex).isEqualTo(1)
    }

    @Test
    fun `build drops duplicates and falls back to the first track`() {
        val built = MusicQueue.build(listOf(a, b, a, c), startTrackId = "unknown")
        assertThat(built.tracks).containsExactly(a, b, c).inOrder()
        assertThat(built.startIndex).isEqualTo(0)
    }

    @Test
    fun `inserts adds older tracks in front and newer at the end`() {
        val inserts = MusicQueue.inserts(current = listOf("b", "c"), desired = listOf(a, b, c, d))
        assertThat(inserts).containsExactly(
            MusicQueue.Insert(0, listOf(a)),
            MusicQueue.Insert(3, listOf(d)),
        ).inOrder()
    }

    @Test
    fun `inserts batches consecutive tracks and fills gaps`() {
        val inserts = MusicQueue.inserts(current = listOf("a", "d"), desired = listOf(a, b, c, d))
        assertThat(inserts).containsExactly(MusicQueue.Insert(1, listOf(b, c)))
    }

    @Test
    fun `inserts is empty when nothing is new`() {
        assertThat(MusicQueue.inserts(current = listOf("a", "b"), desired = listOf(a, b))).isEmpty()
        assertThat(MusicQueue.inserts(current = listOf("a", "b"), desired = listOf(b))).isEmpty()
    }

    @Test
    fun `repeat cycles off, all, one like Telegram`() {
        assertThat(MusicQueue.nextRepeatMode(MusicRepeatMode.Off)).isEqualTo(MusicRepeatMode.All)
        assertThat(MusicQueue.nextRepeatMode(MusicRepeatMode.All)).isEqualTo(MusicRepeatMode.One)
        assertThat(MusicQueue.nextRepeatMode(MusicRepeatMode.One)).isEqualTo(MusicRepeatMode.Off)
    }
}
