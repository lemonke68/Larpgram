/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.search

import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.features.home.impl.datasource.RoomListRoomSummaryFactory
import io.element.android.features.home.impl.model.RoomListRoomSummary
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.roomlist.RoomList
import io.element.android.libraries.matrix.api.roomlist.RoomListFilter
import io.element.android.libraries.matrix.api.roomlist.RoomListService
import io.element.android.libraries.matrix.api.roomlist.updateVisibleRange
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

private const val PAGE_SIZE = 30

@AssistedInject
class RoomListSearchDataSource(
    @Assisted coroutineScope: CoroutineScope,
    roomListService: RoomListService,
    coroutineDispatchers: CoroutineDispatchers,
    private val roomSummaryFactory: RoomListRoomSummaryFactory,
) {
    @AssistedFactory
    interface Factory {
        fun create(coroutineScope: CoroutineScope): RoomListSearchDataSource
    }

    private val roomList = roomListService.createRoomList(
        pageSize = PAGE_SIZE,
        source = RoomList.Source.All,
        coroutineScope = coroutineScope
    )

    // Правка форка: личку ищем и по @username собеседника (как в Telegram), а не только по имени.
    private val allRooms = roomListService.allRooms

    val loadingState = roomList.loadingState

    val roomSummaries: Flow<ImmutableList<RoomListRoomSummary>> = roomList.summaries
        .map { roomSummaries ->
            roomSummaries
                .map(roomSummaryFactory::create)
                .toImmutableList()
        }
        .flowOn(coroutineDispatchers.computation)

    suspend fun updateVisibleRange(visibleRange: IntRange) {
        roomList.updateVisibleRange(visibleRange)
    }

    suspend fun setSearchQuery(searchQuery: String) = coroutineScope {
        val query = searchQuery.trim()
        val filter = if (query.isBlank()) {
            RoomListFilter.None
        } else {
            val byName = RoomListFilter.NormalizedMatchRoomName(query)
            val dmsByUsername = dmRoomsMatchingUsername(query)
            if (dmsByUsername.isEmpty()) byName else RoomListFilter.Any(listOf(byName, RoomListFilter.Identifiers(dmsByUsername)))
        }
        roomList.updateFilter(filter)
    }

    /** Личные чаты, у собеседника которых localpart (`@lemonke67` → `lemonke67`) содержит запрос. */
    private fun dmRoomsMatchingUsername(query: String): List<RoomId> {
        val needle = query.removePrefix("@").substringBefore(':').lowercase()
        if (needle.isEmpty()) return emptyList()
        return allRooms.summaries.replayCache.lastOrNull().orEmpty()
            .filter { summary ->
                summary.isDm && summary.info.heroes.any { hero ->
                    hero.userId.value.removePrefix("@").substringBefore(':').lowercase().contains(needle)
                }
            }
            .map { it.roomId }
    }
}
