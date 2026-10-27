/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.musicplayer.impl

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.ShuffleOrder

/**
 * «Обратный порядок» Telegram средствами ExoPlayer: режим shuffle с порядком от последнего
 * элемента плейлиста к первому. Так «следующий» в уведомлении и на гарнитуре тоже идёт вверх.
 */
@OptIn(UnstableApi::class)
internal class ReversedShuffleOrder(private val length: Int) : ShuffleOrder {
    override fun getLength(): Int = length

    override fun getNextIndex(index: Int): Int = if (index > 0) index - 1 else C.INDEX_UNSET

    override fun getPreviousIndex(index: Int): Int = if (index < length - 1) index + 1 else C.INDEX_UNSET

    override fun getLastIndex(): Int = if (length > 0) 0 else C.INDEX_UNSET

    override fun getFirstIndex(): Int = if (length > 0) length - 1 else C.INDEX_UNSET

    override fun cloneAndInsert(insertionIndex: Int, insertionCount: Int): ShuffleOrder = ReversedShuffleOrder(length + insertionCount)

    override fun cloneAndRemove(indexFrom: Int, indexToExclusive: Int): ShuffleOrder = ReversedShuffleOrder(length - (indexToExclusive - indexFrom))

    override fun cloneAndClear(): ShuffleOrder = ReversedShuffleOrder(0)
}
