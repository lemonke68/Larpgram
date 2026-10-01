/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.preview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import dev.zacsweers.metro.ContributesBinding
import io.element.android.appnav.di.RoomGraphFactory
import io.element.android.features.messages.api.preview.ChatPreviewRenderer
import io.element.android.features.messages.impl.timeline.components.TimelineItemRow
import io.element.android.features.messages.impl.timeline.components.chatWallpaper
import io.element.android.features.messages.impl.timeline.components.selectedChatWallpaper
import io.element.android.features.messages.impl.timeline.di.LocalTimelineItemPresenterFactories
import io.element.android.features.messages.impl.timeline.model.TimelineItem
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.timeline.Timeline

/** За сколько строк до верха подгружать историю. */
private const val LOAD_MORE_THRESHOLD = 8

@ContributesBinding(SessionScope::class)
class DefaultChatPreviewRenderer(
    private val matrixClient: MatrixClient,
    private val roomGraphFactory: RoomGraphFactory,
) : ChatPreviewRenderer {
    @Composable
    override fun Preview(
        roomId: RoomId,
        onClick: () -> Unit,
        modifier: Modifier,
    ) {
        // Своя комната и свой граф комнаты на время превью: навигация в чат не задействована, поэтому
        // «комната открыта» (сброс уведомлений, подписка, недавние) не срабатывает.
        val bindings by produceState<ChatPreviewBindings?>(null, roomId) {
            val room = matrixClient.getJoinedRoom(roomId) ?: return@produceState
            value = roomGraphFactory.create(room) as ChatPreviewBindings
            awaitDispose { room.destroy() }
        }
        Box(modifier = modifier.chatWallpaper(selectedChatWallpaper())) {
            bindings?.let { ChatPreviewTimeline(bindings = it, onClick = onClick) }
        }
    }
}

@Composable
private fun ChatPreviewTimeline(
    bindings: ChatPreviewBindings,
    onClick: () -> Unit,
) {
    val presenter = remember(bindings) { bindings.chatPreviewPresenter }
    CompositionLocalProvider(
        LocalTimelineItemPresenterFactories provides bindings.timelineItemPresenterFactories,
    ) {
        val state = presenter.present()
        val listState = rememberLazyListState()
        val shouldLoadMore by remember {
            derivedStateOf {
                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisible >= listState.layoutInfo.totalItemsCount - LOAD_MORE_THRESHOLD
            }
        }
        LaunchedEffect(shouldLoadMore, state.timelineItems.size) {
            if (shouldLoadMore) state.loadMore()
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            reverseLayout = true,
            // Листает слой поверх списка (ниже): сами строки касаний не получают.
            userScrollEnabled = false,
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(
                items = state.timelineItems,
                contentType = { it.contentType() },
                key = { it.identifier() },
            ) { timelineItem ->
                PreviewRow(timelineItem = timelineItem, state = state)
            }
        }
        // Прозрачный слой над лентой: листание и тап «открыть чат». До сообщений касания не доходят,
        // поэтому ни ссылок, ни реакций, ни меню, ни запуска кружка со звуком — как в превью Telegram.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .scrollable(
                    state = listState,
                    orientation = Orientation.Vertical,
                    reverseDirection = ScrollableDefaults.reverseDirection(
                        layoutDirection = LocalLayoutDirection.current,
                        orientation = Orientation.Vertical,
                        reverseScrolling = true,
                    ),
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
        )
    }
}

@Composable
private fun PreviewRow(
    timelineItem: TimelineItem,
    state: ChatPreviewState,
) {
    TimelineItemRow(
        timelineItem = timelineItem,
        timelineMode = Timeline.Mode.Live,
        timelineRoomInfo = state.timelineRoomInfo,
        timelineProtectionState = state.timelineProtectionState,
        isLastOutgoingMessage = false,
        focusedEventId = null,
        displayThreadSummaries = false,
        onUserDataClick = {},
        onLinkClick = {},
        onLinkLongClick = {},
        onContentClick = {},
        onGalleryItemClick = { _, _ -> },
        onLongClick = {},
        inReplyToClick = {},
        onReactionClick = { _, _ -> },
        onReactionLongClick = { _, _ -> },
        onMoreReactionsClick = {},
        onReadReceiptClick = {},
        onSwipeToReply = {},
        onJoinCallClick = {},
        eventSink = {},
    )
}
