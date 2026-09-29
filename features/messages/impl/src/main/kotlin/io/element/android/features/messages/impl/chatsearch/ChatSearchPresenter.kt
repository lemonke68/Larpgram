/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.chatsearch

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.zacsweers.metro.Inject
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.dateformatter.api.DateFormatter
import io.element.android.libraries.dateformatter.api.DateFormatterMode
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.eventformatter.api.RoomLatestEventFormatter
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.api.roomlist.LatestEventValue
import io.element.android.libraries.matrix.api.search.MessageSearchPaginationState
import io.element.android.libraries.matrix.api.search.MessageSearchResult
import io.element.android.libraries.matrix.api.search.MessageSearchService
import io.element.android.libraries.matrix.api.timeline.item.event.getAvatarUrl
import io.element.android.libraries.matrix.api.timeline.item.event.getDisambiguatedDisplayName
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds

private const val PAGINATE_THRESHOLD = 10

@Inject
class ChatSearchPresenter(
    private val room: JoinedRoom,
    private val matrixClient: MatrixClient,
    private val messageSearchService: MessageSearchService,
    private val latestEventFormatter: RoomLatestEventFormatter,
    private val dateFormatter: DateFormatter,
) : Presenter<ChatSearchState> {
    @Composable
    override fun present(): ChatSearchState {
        val coroutineScope = rememberCoroutineScope()
        val isAvailable = remember { matrixClient.isMessageSearchAvailable }
        val search = remember { messageSearchService.createMessageSearch(scope = coroutineScope, roomId = room.roomId) }
        val query = rememberTextFieldState()
        val rawResults by search.results.collectAsState()
        val paginationState by search.paginationState.collectAsState()
        val queryText = query.text.toString().trim()

        LaunchedEffect(queryText) {
            if (queryText.isEmpty() || !isAvailable) return@LaunchedEffect
            delay(200.milliseconds)
            search.setQuery(queryText).onFailure { Timber.e(it, "chat search: query failed") }
        }

        val results = if (queryText.isEmpty()) {
            emptyList()
        } else {
            rawResults.filter { it.roomId == room.roomId }.mapNotNull { it.toChatSearchResult() }
        }

        fun handleEvent(event: ChatSearchEvent) {
            when (event) {
                is ChatSearchEvent.VisibleRangeChanged -> {
                    val state = search.paginationState.value
                    if (state is MessageSearchPaginationState.Idle && !state.endReached && event.range.last >= results.size - PAGINATE_THRESHOLD) {
                        coroutineScope.launch { search.paginate() }
                    }
                }
            }
        }

        return ChatSearchState(
            query = query,
            results = results.toImmutableList(),
            isLoading = queryText.isNotEmpty() && paginationState is MessageSearchPaginationState.Loading,
            isAvailable = isAvailable,
            eventSink = ::handleEvent,
        )
    }

    private fun MessageSearchResult.toChatSearchResult(): ChatSearchResult? {
        val body = latestEventFormatter.format(
            latestEvent = LatestEventValue.Remote(
                timestamp = timestamp,
                content = content,
                senderId = senderId,
                senderProfile = senderProfile,
                isOwn = matrixClient.isMe(senderId),
            ),
            // Имя отправителя стоит заголовком строки, в тексте его не повторяем.
            isDmRoom = true,
        ) ?: return null
        val name = senderProfile.getDisambiguatedDisplayName(senderId)
        return ChatSearchResult(
            eventId = eventId,
            senderName = name,
            senderAvatar = AvatarData(id = senderId.value, name = name, url = senderProfile.getAvatarUrl(), size = AvatarSize.RoomListItem),
            body = body.toString(),
            formattedTimestamp = dateFormatter.format(timestamp, DateFormatterMode.TimeOrDate, useRelative = true),
        )
    }
}
