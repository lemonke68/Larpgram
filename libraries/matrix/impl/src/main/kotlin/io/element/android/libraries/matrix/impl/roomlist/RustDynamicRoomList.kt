/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.roomlist

import io.element.android.libraries.matrix.api.roomlist.DynamicRoomList
import io.element.android.libraries.matrix.api.roomlist.RoomList
import io.element.android.libraries.matrix.api.roomlist.RoomListFilter
import io.element.android.libraries.matrix.api.roomlist.RoomSummary
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.matrix.rustcomponents.sdk.RoomListDynamicEntriesController
import timber.log.Timber

private const val DEFAULT_ADD_PAGES_COUNT = 3

internal class RustDynamicRoomList(
    override val summaries: MutableSharedFlow<List<RoomSummary>>,
    override val loadingState: MutableStateFlow<RoomList.LoadingState>,
    private val processor: RoomSummaryListProcessor,
    override val pageSize: Int,
    private val dynamicController: () -> RoomListDynamicEntriesController?,
    private val addPagesCount: Int = DEFAULT_ADD_PAGES_COUNT
) : DynamicRoomList {
    private val mutex = Mutex()

    override suspend fun rebuildSummaries() {
        processor.rebuildRoomSummaries()
    }

    override suspend fun updateFilter(filter: RoomListFilter) {
        withController { controller ->
            // Reset pagination when filter changes
            controller.resetToOnePage()
            val rustFilter = RoomListFilterMapper.toRustFilter(filter)
            controller.setFilter(rustFilter)
            // Then preload some pages
            controller.addPages(addPagesCount)
        }
    }

    override suspend fun loadMore() {
        withController { it.addPages(addPagesCount) }
    }

    override suspend fun reset() {
        withController { it.resetToOnePage() }
    }

    // Правка форка: контроллер уничтожается в awaitClose потока записей (выход из сессии, сбой
    // потока), и это может случиться между чтением ссылки и вызовом. Мёртвый объект uniffi бросает
    // IllegalStateException — глотаем его: списка уже нет, пагинировать нечего.
    private suspend fun withController(block: (RoomListDynamicEntriesController) -> Unit) {
        mutex.withLock {
            val controller = dynamicController() ?: return
            try {
                block(controller)
            } catch (e: IllegalStateException) {
                Timber.w(e, "Room list controller already destroyed")
            }
        }
    }

    private fun RoomListDynamicEntriesController.addPages(pageCount: Int) = repeat(pageCount) { addOnePage() }
}
