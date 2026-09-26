/*
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.messages.impl.timeline.factories

import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.timeline.aTimelineItemEvent
import io.element.android.features.messages.impl.timeline.model.TimelineItem
import io.element.android.features.messages.impl.timeline.model.TimelineItemGroupPosition
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemGalleryContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemImageContent
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemImageContent
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemTextContent
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.timeline.item.event.LarpgramAlbum
import io.element.android.libraries.matrix.api.timeline.item.event.LarpgramAlbumPart
import io.element.android.libraries.matrix.api.timeline.item.event.toEventOrTransactionId
import org.junit.Test

class LarpgramAlbumMergerTest {
    @Test
    fun `album filename round-trips`() {
        val name = LarpgramAlbum.filename("0a1b2c3d", index = 2, count = 5, extension = "jpg")
        assertThat(name).isEqualTo("larpgram-album-0a1b2c3d-3-5.jpg")
        assertThat(LarpgramAlbum.parse(name)).isEqualTo(LarpgramAlbumPart("0a1b2c3d", index = 2, count = 5))
    }

    @Test
    fun `ordinary and malformed filenames are not album parts`() {
        assertThat(LarpgramAlbum.parse("IMG_2026.jpg")).isNull()
        assertThat(LarpgramAlbum.parse("larpgram-album-0a1b2c3d-6-5.jpg")).isNull()
        assertThat(LarpgramAlbum.parse("larpgram-album-0a1b2c3d-1-1.jpg")).isNull()
        assertThat(LarpgramAlbum.parse(null)).isNull()
    }

    @Test
    fun `consecutive parts merge into one gallery ordered by part, caption from the first part`() {
        // Список идёт от новых к старым.
        val third = anAlbumPart("\$c", index = 2, groupPosition = TimelineItemGroupPosition.Last)
        val second = anAlbumPart("\$b", index = 1, groupPosition = TimelineItemGroupPosition.Middle)
        val first = anAlbumPart("\$a", index = 0, caption = "Отпуск", groupPosition = TimelineItemGroupPosition.First)
        val text = aTimelineItemEvent(content = aTimelineItemTextContent())

        val result = listOf(third, second, first, text).mergeLarpgramAlbums()

        assertThat(result).hasSize(2)
        val album = result[0] as TimelineItem.Event
        assertThat(album.eventId).isEqualTo(EventId("\$a"))
        assertThat(album.groupPosition).isEqualTo(TimelineItemGroupPosition.None)
        val content = album.content as TimelineItemGalleryContent
        assertThat(content.caption).isEqualTo("Отпуск")
        assertThat(content.items.map { it.filename }).containsExactly(
            LarpgramAlbum.filename(ALBUM_ID, 0, 3, "jpg"),
            LarpgramAlbum.filename(ALBUM_ID, 1, 3, "jpg"),
            LarpgramAlbum.filename(ALBUM_ID, 2, 3, "jpg"),
        ).inOrder()
        assertThat(content.albumParts).containsExactly(
            EventId("\$a").toEventOrTransactionId(),
            EventId("\$b").toEventOrTransactionId(),
            EventId("\$c").toEventOrTransactionId(),
        ).inOrder()
        assertThat(result[1]).isEqualTo(text)
    }

    @Test
    fun `a lone part split off by another message stays a plain photo`() {
        val second = anAlbumPart("\$b", index = 1)
        val text = aTimelineItemEvent(content = aTimelineItemTextContent())
        val first = anAlbumPart("\$a", index = 0)

        val result = listOf(second, text, first).mergeLarpgramAlbums()

        assertThat(result).containsExactly(second, text, first).inOrder()
        assertThat((result[0] as TimelineItem.Event).content).isInstanceOf(TimelineItemImageContent::class.java)
    }

    private fun anAlbumPart(
        eventId: String,
        index: Int,
        count: Int = 3,
        caption: String? = null,
        groupPosition: TimelineItemGroupPosition = TimelineItemGroupPosition.None,
    ) = aTimelineItemEvent(
        eventId = EventId(eventId),
        groupPosition = groupPosition,
        content = aTimelineItemImageContent(
            filename = LarpgramAlbum.filename(ALBUM_ID, index, count, "jpg"),
            caption = caption,
        ),
    )

    private companion object {
        const val ALBUM_ID = "0a1b2c3d"
    }
}
