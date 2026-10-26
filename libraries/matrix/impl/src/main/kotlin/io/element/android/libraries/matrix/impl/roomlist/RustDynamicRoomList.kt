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
    initialFilter: RoomListFilter,
    private val addPagesCount: Int = DEFAULT_ADD_PAGES_COUNT
) : DynamicRoomList {
    private val mutex = Mutex()

    // The controller is created asynchronously, once the entries of the room list are observed
    @Volatile
    private var dynamicController: RoomListDynamicEntriesController? = null

    // The last requested filter, so that it can be applied when the controller is created
    private var currentFilter: RoomListFilter = initialFilter

    /**
     * To be called when the controller is created. The controller already has the initial filter set, so apply the filter
     * only if it has been updated in the meantime.
     */
    suspend fun onControllerCreated(controller: RoomListDynamicEntriesController, initialFilter: RoomListFilter) {
        mutex.withLock {
            dynamicController = controller
            if (currentFilter != initialFilter) {
                controller.applyFilter(currentFilter)
            }
        }
    }

    override suspend fun rebuildSummaries() {
        processor.rebuildRoomSummaries()
    }

    override suspend fun updateFilter(filter: RoomListFilter) {
        withController { controller ->
            currentFilter = filter
            controller?.applyFilter(filter)
        }
    }

    private fun RoomListDynamicEntriesController.applyFilter(filter: RoomListFilter) {
        // Reset pagination when filter changes
        resetToOnePage()
        setFilter(RoomListFilterMapper.toRustFilter(filter))
        // Then preload some pages
        addPages(addPagesCount)
    }

    override suspend fun loadMore() {
        withController { it?.addPages(addPagesCount) }
    }

    override suspend fun reset() {
        withController { it?.resetToOnePage() }
    }

    /**
     * Правка форка: вызывается перед уничтожением контроллера, чтобы не звать методы мёртвого объекта.
     */
    fun onControllerDestroyed() {
        dynamicController = null
    }

    // Правка форка: контроллер уничтожается в awaitClose потока записей (выход из сессии, сбой
    // потока), и это может случиться между чтением ссылки и вызовом. Мёртвый объект uniffi бросает
    // IllegalStateException — глотаем его: списка уже нет, пагинировать нечего.
    private suspend fun withController(block: (RoomListDynamicEntriesController?) -> Unit) {
        mutex.withLock {
            try {
                block(dynamicController)
            } catch (e: IllegalStateException) {
                Timber.w(e, "Room list controller already destroyed")
            }
        }
    }

    private fun RoomListDynamicEntriesController.addPages(pageCount: Int) = repeat(pageCount) { addOnePage() }
}
