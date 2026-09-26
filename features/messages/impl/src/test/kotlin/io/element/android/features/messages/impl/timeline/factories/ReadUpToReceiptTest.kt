/*
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.messages.impl.timeline.factories

import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.timeline.aTimelineItemEvent
import io.element.android.features.messages.impl.timeline.aTimelineItemReadReceipts
import io.element.android.features.messages.impl.timeline.components.receipt.aReadReceiptData
import io.element.android.features.messages.impl.timeline.model.TimelineItem
import org.junit.Test

class ReadUpToReceiptTest {
    @Test
    fun `own messages older than a receipt are read, newer ones are not`() {
        // Список от новых к старым: [новое своё, своё с квитанцией, чужое, старое своё].
        val newer = aTimelineItemEvent(isMine = true)
        val withReceipt = aTimelineItemEvent(
            isMine = true,
            readReceiptState = aTimelineItemReadReceipts(listOf(aReadReceiptData(0))),
        )
        val theirs = aTimelineItemEvent(isMine = false)
        val older = aTimelineItemEvent(isMine = true)

        val result = listOf(newer, withReceipt, theirs, older).markReadUpToLatestReceipt()
            .map { (it as TimelineItem.Event).isReadByOthers }

        assertThat(result).containsExactly(false, false, false, true).inOrder()
    }

    @Test
    fun `a receipt on someone else's message also marks older own messages read`() {
        val theirsRead = aTimelineItemEvent(
            isMine = false,
            readReceiptState = aTimelineItemReadReceipts(listOf(aReadReceiptData(0))),
        )
        val mine = aTimelineItemEvent(isMine = true)

        val result = listOf(theirsRead, mine).markReadUpToLatestReceipt()

        assertThat((result[1] as TimelineItem.Event).isReadByOthers).isTrue()
    }

    @Test
    fun `without any receipt nothing is marked read`() {
        val items = listOf(aTimelineItemEvent(isMine = true), aTimelineItemEvent(isMine = true))
        assertThat(items.markReadUpToLatestReceipt()).isEqualTo(items)
    }
}
