/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.virtual

import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.element.android.features.messages.impl.R
import io.element.android.libraries.designsystem.atomic.molecules.ComposerAlertMolecule
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.preview.ROOM_NAME
import io.element.android.libraries.designsystem.text.toAnnotatedString
import io.element.android.libraries.designsystem.utils.allBooleans
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.tombstone.PredecessorRoom

@Composable
fun TimelineItemRoomBeginningView(
    roomName: String?,
    predecessorRoom: PredecessorRoom?,
    isDm: Boolean,
    onPredecessorRoomClick: (RoomId) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        if (predecessorRoom != null) {
            ComposerAlertMolecule(
                avatar = null,
                content = stringResource(R.string.screen_room_timeline_upgraded_room_message).toAnnotatedString(),
                onSubmitClick = { onPredecessorRoomClick(predecessorRoom.roomId) },
                submitText = stringResource(R.string.screen_room_timeline_upgraded_room_action)
            )
        }
        // Правка форка: «Это начало …» не показываем — в TG начала переписки не подписывают.
    }
}

@PreviewsDayNight
@Composable
internal fun TimelineItemRoomBeginningViewPreview() = ElementPreview {
    Column(verticalArrangement = spacedBy(16.dp)) {
        allBooleans.forEach { isDm ->
            TimelineItemRoomBeginningView(
                predecessorRoom = null,
                roomName = null,
                isDm = isDm,
                onPredecessorRoomClick = {},
            )
            TimelineItemRoomBeginningView(
                predecessorRoom = null,
                roomName = ROOM_NAME,
                isDm = isDm,
                onPredecessorRoomClick = {},
            )
            TimelineItemRoomBeginningView(
                predecessorRoom = PredecessorRoom(RoomId("!roomId:matrix.org")),
                roomName = ROOM_NAME,
                isDm = isDm,
                onPredecessorRoomClick = {},
            )
        }
    }
}
