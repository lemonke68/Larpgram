/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.home.impl.model.RoomListRoomSummary
import io.element.android.features.home.impl.model.aRoomListRoomSummary
import io.element.android.features.home.impl.roomlist.RoomListEvent
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val ACTION_WIDTH = 76.dp

/**
 * A single revealed swipe action (icon + label on a coloured panel).
 */
data class SwipeAction(
    val icon: ImageVector,
    val label: String,
    val background: Color,
    val onClick: () -> Unit,
)

/**
 * Telegram-style swipe-to-reveal wrapper for a chat row.
 * Swiping the row left reveals [endActions] on the right; swiping right reveals [startActions]
 * on the left. Tapping an action runs it and closes the row. Phase 1 wires only Mute (end).
 */
@Composable
fun SwipeableRoomListRow(
    endActions: List<SwipeAction>,
    modifier: Modifier = Modifier,
    startActions: List<SwipeAction> = emptyList(),
    content: @Composable () -> Unit,
) {
    if (endActions.isEmpty() && startActions.isEmpty()) {
        content()
        return
    }
    val density = LocalDensity.current
    val endWidthPx = with(density) { (ACTION_WIDTH * endActions.size).toPx() }
    val startWidthPx = with(density) { (ACTION_WIDTH * startActions.size).toPx() }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    fun close() {
        scope.launch { offsetX.animateTo(0f) }
    }

    SwipeableRowLayout(
        offsetX = offsetX.value,
        startActions = startActions,
        endActions = endActions,
        onActionClick = { action ->
            action.onClick()
            close()
        },
        onContentTapWhileOpen = { close() },
        modifier = modifier.draggable(
            orientation = Orientation.Horizontal,
            state = rememberDraggableState { delta ->
                scope.launch { offsetX.snapTo((offsetX.value + delta).coerceIn(-endWidthPx, startWidthPx)) }
            },
            onDragStopped = {
                val target = when {
                    offsetX.value <= -endWidthPx / 2 -> -endWidthPx
                    offsetX.value >= startWidthPx / 2 -> startWidthPx
                    else -> 0f
                }
                scope.launch { offsetX.animateTo(target) }
            },
        ),
        content = content,
    )
}

/**
 * Stateless layout: action panels behind, the row content shifted by [offsetX] px on top.
 * Kept separate so a fixed offset can be rendered in previews.
 */
@Composable
private fun SwipeableRowLayout(
    offsetX: Float,
    startActions: List<SwipeAction>,
    endActions: List<SwipeAction>,
    onActionClick: (SwipeAction) -> Unit,
    onContentTapWhileOpen: () -> Unit,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        // matchParentSize: fill the row's content height without dictating it (the front
        // content is what sizes the Box; in a LazyColumn the height is otherwise unbounded).
        Row(modifier = Modifier.matchParentSize()) {
            startActions.forEach { action ->
                SwipeActionPanel(action = action, onClick = { onActionClick(action) })
            }
            Spacer(modifier = Modifier.weight(1f))
            endActions.forEach { action ->
                SwipeActionPanel(action = action, onClick = { onActionClick(action) })
            }
        }
        Box(
            modifier = Modifier
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) {
                        placeable.placeRelative(offsetX.roundToInt(), 0)
                    }
                }
                // Opaque background so the action panel behind is hidden when the row is closed
                // and only appears in the gap that opens as the row slides.
                .background(ElementTheme.colors.bgCanvasDefault),
        ) {
            content()
            if (offsetX != 0f) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onContentTapWhileOpen,
                        )
                )
            }
        }
    }
}

@Composable
private fun SwipeActionPanel(
    action: SwipeAction,
    onClick: () -> Unit,
    width: Dp = ACTION_WIDTH,
) {
    Column(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .background(action.background)
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = action.label,
            tint = Color.White,
        )
        Spacer(modifier = Modifier.width(0.dp))
        Text(
            text = action.label,
            color = Color.White,
            style = ElementTheme.typography.fontBodyXsMedium,
        )
    }
}

/**
 * Builds the Telegram-style swipe actions for a chat row. Phase 1: Mute/Unmute only (end side).
 */
@Composable
fun rememberChatSwipeEndActions(
    room: RoomListRoomSummary,
    eventSink: (RoomListEvent) -> Unit,
): List<SwipeAction> {
    return listOf(
        SwipeAction(
            icon = if (room.isMuted) CompoundIcons.Notifications() else CompoundIcons.NotificationsOffSolid(),
            label = if (room.isMuted) "Unmute" else "Mute",
            background = Color(0xFFF0A030),
            onClick = { eventSink(RoomListEvent.SetRoomIsMuted(room.roomId, isMuted = !room.isMuted)) },
        )
    )
}

@PreviewsDayNight
@Composable
internal fun SwipeableRoomListRowRevealedPreview() = ElementPreview {
    val room = aRoomListRoomSummary()
    SwipeableRowLayoutPreviewHost(
        endActions = listOf(
            SwipeAction(
                icon = CompoundIcons.NotificationsOffSolid(),
                label = "Mute",
                background = Color(0xFFF0A030),
                onClick = {},
            )
        ),
        room = room,
    )
}

@Composable
private fun SwipeableRowLayoutPreviewHost(
    endActions: List<SwipeAction>,
    room: RoomListRoomSummary,
) {
    val density = LocalDensity.current
    val revealed = with(density) { -(ACTION_WIDTH * endActions.size).toPx() }
    SwipeableRowLayout(
        offsetX = revealed,
        startActions = emptyList(),
        endActions = endActions,
        onActionClick = {},
        onContentTapWhileOpen = {},
        content = {
            RoomSummaryRow(
                room = room,
                hideInviteAvatars = false,
                isInviteSeen = false,
                showUnreadCount = true,
                onClick = {},
                eventSink = {},
            )
        },
    )
}

@Preview
@Composable
private fun SwipeActionPanelPreview() = ElementPreview {
    SwipeActionPanel(
        action = SwipeAction(
            icon = CompoundIcons.NotificationsOffSolid(),
            label = "Mute",
            background = Color(0xFFF0A030),
            onClick = {},
        ),
        onClick = {},
    )
}
