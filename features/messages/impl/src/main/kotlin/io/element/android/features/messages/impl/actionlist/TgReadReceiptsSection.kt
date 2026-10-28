/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.actionlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.LarpgramType
import io.element.android.compound.tokens.larpgramColors
import io.element.android.features.messages.impl.R
import io.element.android.features.messages.impl.timeline.model.ReadReceiptData
import io.element.android.features.messages.impl.timeline.model.TimelineItem
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.components.messages.MessageDeliveryState
import io.element.android.libraries.designsystem.components.messages.MessageDeliveryTicks
import io.element.android.libraries.designsystem.theme.components.Text
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * Who read [event]: a Matrix read receipt marks the newest event a user has read, so everyone
 * whose receipt sits on this event or on a newer one has read it. [this] is the timeline as the
 * timeline view holds it, newest first.
 */
internal fun List<TimelineItem>.readersOf(event: TimelineItem.Event): ImmutableList<ReadReceiptData> {
    val readers = LinkedHashMap<String, ReadReceiptData>()
    for (item in this) {
        val events = when (item) {
            is TimelineItem.Event -> listOf(item)
            is TimelineItem.GroupedEvents -> item.events
            is TimelineItem.Virtual -> emptyList()
        }
        for (candidate in events) {
            candidate.readReceiptState.receipts.forEach { receipt -> readers.putIfAbsent(receipt.avatarData.id, receipt) }
            if (candidate.id == event.id) return readers.values.toImmutableList()
        }
    }
    return readers.values.toImmutableList()
}

/**
 * First section of the message menu for the user's own messages (redesign V1 final): «Прочитали
 * N из M» with an accent double check, then one row per reader with the time of reading.
 *
 * @param otherMembersCount members of the room other than the user, null when unknown.
 */
@Composable
internal fun TgReadReceiptsSection(
    readers: ImmutableList<ReadReceiptData>,
    otherMembersCount: Long?,
    modifier: Modifier = Modifier,
) {
    val colors = larpgramColors
    Column(modifier = modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MessageDeliveryTicks(state = MessageDeliveryState.Read)
            Text(
                text = if (otherMembersCount != null && otherMembersCount >= readers.size) {
                    stringResource(R.string.larpgram_message_read_by_of, readers.size, otherMembersCount.toInt())
                } else {
                    stringResource(R.string.larpgram_message_read_by, readers.size)
                },
                style = LarpgramType.listItem.copy(fontSize = LarpgramType.folderChip.fontSize, fontWeight = FontWeight.ExtraBold),
                color = colors.accentText,
            )
        }
        readers.forEach { reader ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Avatar(avatarData = reader.avatarData, avatarType = AvatarType.User, forcedAvatarSize = 30.dp)
                Text(
                    text = reader.avatarData.name ?: reader.avatarData.id,
                    style = LarpgramType.listItem,
                    color = ElementTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = reader.formattedDate,
                    style = LarpgramType.caption,
                    color = ElementTheme.colors.textSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}
