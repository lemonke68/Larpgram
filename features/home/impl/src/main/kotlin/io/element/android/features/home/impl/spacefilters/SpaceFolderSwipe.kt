/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.spacefilters

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.Animatable
import kotlinx.coroutines.launch

// Порог горизонтального свайпа для переключения папки. Меньше — легко переключить случайно
// при диагональном скролле; больше — приходится тянуть далеко. Замерено на глаз, правит юзер.
private val FOLDER_SWIPE_THRESHOLD = 56.dp

/** Индекс текущей вкладки в ленте (0 = «Все», далее папки в порядке ленты). */
private fun SpaceFiltersState.currentTabIndex(): Int {
    val current = selectedFilter() ?: return 0
    return availableFilters().indexOfFirst { it.spaceRoom.roomId == current.spaceRoom.roomId } + 1
}

/** Можно ли переключиться в направлении (1 = следующая справа, -1 = предыдущая слева). */
fun canSwitchSpaceFilter(state: SpaceFiltersState, direction: Int): Boolean {
    val size = state.availableFilters().size
    if (size == 0) return false
    val target = (state.currentTabIndex() + direction).coerceIn(0, size)
    return target != state.currentTabIndex()
}

/**
 * Larpgram: переключение «папок» (Matrix Spaces) горизонтальным свайпом по списку чатов, как в
 * Telegram. Направление 1 — следующая папка справа по ленте; -1 — предыдущая слева.
 */
fun switchSpaceFilter(state: SpaceFiltersState, direction: Int) {
    val filters = state.availableFilters()
    if (filters.isEmpty()) return
    val currentIndex = state.currentTabIndex()
    val target = (currentIndex + direction).coerceIn(0, filters.size)
    if (target == currentIndex) return
    when (state) {
        is SpaceFiltersState.Unselected -> {
            if (target > 0) state.eventSink(SpaceFiltersEvent.Unselected.SelectFilter(filters[target - 1]))
        }
        is SpaceFiltersState.Selected -> {
            if (target == 0) {
                state.eventSink(SpaceFiltersEvent.Selected.ClearSelection)
            } else {
                state.eventSink(SpaceFiltersEvent.Selected.SelectFilter(filters[target - 1]))
            }
        }
        else -> Unit
    }
}

/**
 * Обёртка над списком чатов: горизонтальный свайп плавно листает папки. Содержимое едет за пальцем
 * (превью), на отпускании выше порога — старая лента доезжает за край, папка переключается, новая
 * лента въезжает с противоположной стороны. Свайп из краевых зон строк уже перехватывают сами строки
 * (действия чата), сюда всплывает только середина/пустое место.
 *
 * `draggable(Horizontal)` не мешает вертикальному скроллу списка (ориентация-locked) — в отличие от
 * прежнего `detectHorizontalDragGestures`, который блокировал прокрутку.
 */
@Composable
fun SpaceFolderSwipe(
    state: SpaceFiltersState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (state.availableFilters().isEmpty()) {
        Box(modifier) { content() }
        return
    }
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    var widthPx by remember { mutableFloatStateOf(0f) }
    val thresholdPx = with(density) { FOLDER_SWIPE_THRESHOLD.toPx() }
    val latest = rememberUpdatedState(state)

    Box(
        modifier = modifier
            .onSizeChanged { widthPx = it.width.toFloat() }
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    // Тянуть можно только туда, где есть соседняя папка; иначе лёгкое сопротивление.
                    val dir = if (delta < 0) 1 else -1
                    val allowed = canSwitchSpaceFilter(latest.value, dir)
                    val applied = if (allowed) delta else delta * 0.2f
                    val w = widthPx.takeIf { it > 0f } ?: Float.MAX_VALUE
                    scope.launch { offsetX.snapTo((offsetX.value + applied).coerceIn(-w, w)) }
                },
                onDragStopped = {
                    val w = widthPx
                    scope.launch {
                        when {
                            w > 0f && offsetX.value <= -thresholdPx && canSwitchSpaceFilter(latest.value, 1) -> {
                                offsetX.animateTo(-w)
                                switchSpaceFilter(latest.value, 1)
                                offsetX.snapTo(w)
                                offsetX.animateTo(0f)
                            }
                            w > 0f && offsetX.value >= thresholdPx && canSwitchSpaceFilter(latest.value, -1) -> {
                                offsetX.animateTo(w)
                                switchSpaceFilter(latest.value, -1)
                                offsetX.snapTo(-w)
                                offsetX.animateTo(0f)
                            }
                            else -> offsetX.animateTo(0f)
                        }
                    }
                },
            ),
    ) {
        Box(Modifier.graphicsLayer { translationX = offsetX.value }) {
            content()
        }
    }
}
