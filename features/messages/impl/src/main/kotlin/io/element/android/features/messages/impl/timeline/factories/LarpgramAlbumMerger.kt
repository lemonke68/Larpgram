/*
 * Правка форка: сборка альбома Larpgram в одну мозаику.
 *
 * Альбом приходит несколькими обычными фото/видео с меткой в имени файла (см. `LarpgramAlbum`).
 * Подряд идущие части одного альбома от одного отправителя склеиваются в один элемент ленты с
 * галереей — его уже умеет рисовать мозаика TG и открывать просмотрщик. Если посреди альбома
 * вклинилось чужое сообщение, каждая половина собирается отдельно; одиночная часть остаётся фото.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.messages.impl.timeline.factories

import io.element.android.features.messages.impl.timeline.model.TimelineItem
import io.element.android.features.messages.impl.timeline.model.TimelineItemGroupPosition
import io.element.android.features.messages.impl.timeline.model.event.GalleryItem
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemGalleryContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemImageContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemVideoContent
import io.element.android.features.messages.impl.timeline.model.event.captionOrNull
import io.element.android.features.messages.impl.timeline.model.event.formattedCaptionOrNull
import io.element.android.features.messages.impl.timeline.model.event.htmlCaptionOrNull
import io.element.android.libraries.matrix.api.timeline.item.event.LarpgramAlbum
import io.element.android.libraries.matrix.api.timeline.item.event.LarpgramAlbumPart
import io.element.android.libraries.matrix.api.timeline.item.event.LocalEventSendState
import kotlinx.collections.immutable.toImmutableList

/** Список идёт от новых к старым, как его отдаёт [TimelineItemsFactory]. */
internal fun List<TimelineItem>.mergeLarpgramAlbums(): List<TimelineItem> {
    if (none { (it as? TimelineItem.Event)?.albumPart() != null }) return this
    val result = ArrayList<TimelineItem>(size)
    var index = 0
    while (index < size) {
        val item = this[index]
        val part = (item as? TimelineItem.Event)?.albumPart()
        if (part == null) {
            result.add(item)
            index++
            continue
        }
        var end = index + 1
        while (end < size) {
            val next = this[end] as? TimelineItem.Event ?: break
            if (next.senderId != item.senderId || next.albumPart()?.albumId != part.albumId) break
            end++
        }
        val run = subList(index, end).map { it as TimelineItem.Event }
        result.add(if (run.size > 1) mergeAlbum(run) else item)
        index = end
    }
    return result
}

private fun TimelineItem.Event.albumPart(): LarpgramAlbumPart? = when (val content = content) {
    is TimelineItemImageContent -> LarpgramAlbum.parse(content.filename)
    is TimelineItemVideoContent -> LarpgramAlbum.parse(content.filename)
    else -> null
}

/** [run] — части альбома от новых к старым. */
private fun mergeAlbum(run: List<TimelineItem.Event>): TimelineItem.Event {
    val parts = run.sortedBy { it.albumPart()?.index ?: 0 }
    val base = parts.first()
    val newest = run.first()
    val withCaption = parts.firstOrNull { it.content.captionOrNull() != null }?.content
    return base.copy(
        content = TimelineItemGalleryContent(
            body = withCaption?.captionOrNull().orEmpty(),
            caption = withCaption?.captionOrNull(),
            formattedCaption = withCaption?.formattedCaptionOrNull(),
            htmlCaption = withCaption?.htmlCaptionOrNull(),
            isEdited = false,
            items = parts.mapNotNull { it.toGalleryItem() }.toImmutableList(),
            albumParts = parts.map { it.eventOrTransactionId }.toImmutableList(),
        ),
        sentTimeMillis = newest.sentTimeMillis,
        sentTime = newest.sentTime,
        sentDate = newest.sentDate,
        isEditable = false,
        groupPosition = mergedGroupPosition(oldest = run.last().groupPosition, newest = newest.groupPosition),
        readReceiptState = newest.readReceiptState,
        localSendState = run.firstOrNull { it.localSendState is LocalEventSendState.Failed }?.localSendState
            ?: run.firstOrNull { it.localSendState is LocalEventSendState.Sending }?.localSendState
            ?: newest.localSendState,
    )
}

private fun mergedGroupPosition(
    oldest: TimelineItemGroupPosition,
    newest: TimelineItemGroupPosition,
): TimelineItemGroupPosition = when {
    oldest.isNew() && newest.isLast() -> TimelineItemGroupPosition.None
    oldest.isNew() -> TimelineItemGroupPosition.First
    newest.isLast() -> TimelineItemGroupPosition.Last
    else -> TimelineItemGroupPosition.Middle
}

private fun TimelineItem.Event.toGalleryItem(): GalleryItem? = when (val content = content) {
    is TimelineItemImageContent -> GalleryItem(
        filename = content.filename,
        mimeType = content.mimeType,
        mediaSource = content.mediaSource,
        type = GalleryItem.Type.Image,
        thumbnailSource = content.thumbnailSource,
        width = content.width,
        height = content.height,
        thumbnailWidth = content.thumbnailWidth,
        thumbnailHeight = content.thumbnailHeight,
        blurhash = content.blurhash,
    )
    is TimelineItemVideoContent -> GalleryItem(
        filename = content.filename,
        mimeType = content.mimeType,
        mediaSource = content.mediaSource,
        type = GalleryItem.Type.Video,
        thumbnailSource = content.thumbnailSource,
        width = content.width,
        height = content.height,
        thumbnailWidth = content.thumbnailWidth,
        thumbnailHeight = content.thumbnailHeight,
        blurhash = content.blurHash,
        duration = content.duration,
    )
    else -> null
}
