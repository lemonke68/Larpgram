/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.roomlist

import com.google.common.truth.Truth.assertThat
import io.element.android.features.home.impl.model.LatestEvent
import io.element.android.features.home.impl.model.aRoomListRoomSummary
import org.junit.Test

class ClearedHistoryPreviewTest {
    private val summary = aRoomListRoomSummary(latestEvent = LatestEvent.Synced("Hello")).copy(latestEventTimestampMillis = 1_000)

    @Test
    fun `the latest message under the mark is hidden`() {
        assertThat(summary.withoutClearedLatestEvent(clearedUpTo = 1_000).latestEvent).isEqualTo(LatestEvent.None)
    }

    @Test
    fun `a message newer than the mark stays`() {
        assertThat(summary.withoutClearedLatestEvent(clearedUpTo = 999)).isEqualTo(summary)
    }

    @Test
    fun `a chat without a mark is untouched`() {
        assertThat(summary.withoutClearedLatestEvent(clearedUpTo = null)).isEqualTo(summary)
    }
}
