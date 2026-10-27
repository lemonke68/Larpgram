/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.musicplayer.impl

import androidx.media3.common.C
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.musicplayer.api.MusicOrder
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

    @Test
    fun `order cycles default, reversed, shuffle`() {
        assertThat(MusicQueue.nextOrder(MusicOrder.Default)).isEqualTo(MusicOrder.Reversed)
        assertThat(MusicQueue.nextOrder(MusicOrder.Reversed)).isEqualTo(MusicOrder.Shuffle)
        assertThat(MusicQueue.nextOrder(MusicOrder.Shuffle)).isEqualTo(MusicOrder.Default)
    }

    @Test
    fun `speed cycles 1x, 1_5x, 2x and snaps odd speeds up`() {
        assertThat(MusicQueue.nextSpeed(1f)).isEqualTo(1.5f)
        assertThat(MusicQueue.nextSpeed(1.5f)).isEqualTo(2f)
        assertThat(MusicQueue.nextSpeed(2f)).isEqualTo(1f)
        assertThat(MusicQueue.nextSpeed(1.2f)).isEqualTo(2f)
        assertThat(MusicQueue.nextSpeed(0.5f)).isEqualTo(1.5f)
        assertThat(MusicQueue.nextSpeed(3f)).isEqualTo(1f)
    }

    @Test
    fun `reversed shuffle order walks the playlist upwards`() {
        val order = ReversedShuffleOrder(3)
        assertThat(order.firstIndex).isEqualTo(2)
        assertThat(order.lastIndex).isEqualTo(0)
        assertThat(order.getNextIndex(2)).isEqualTo(1)
        assertThat(order.getNextIndex(0)).isEqualTo(C.INDEX_UNSET)
        assertThat(order.getPreviousIndex(0)).isEqualTo(1)
        assertThat(order.getPreviousIndex(2)).isEqualTo(C.INDEX_UNSET)
        assertThat(order.cloneAndInsert(0, 2).length).isEqualTo(5)
        assertThat(order.cloneAndRemove(0, 2).length).isEqualTo(1)
        assertThat(order.cloneAndClear().firstIndex).isEqualTo(C.INDEX_UNSET)
    }
}
