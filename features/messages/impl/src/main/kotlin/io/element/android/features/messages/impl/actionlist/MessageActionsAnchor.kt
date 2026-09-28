/*
 * Copyright (c) 2026 Larpgram.
 * Правка форка: меню долгого нажатия привязано к самому сообщению (фаза 2).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.actionlist

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.positionInWindow

/**
 * Куда привязать всплывающее меню: экранные координаты нажатого пузыря.
 *
 * Пузыри регистрируют свои координаты **по id сообщения**, а не в колбэке нажатия. Так меню
 * встаёт на место при любом способе открытия — из пузыря, из содержимого, из времени: у всех
 * путей общий обработчик `onMessageLongClick(event)`, и он просто спрашивает координаты по id.
 * Первая попытка ловила координаты в обёртке одного колбэка и в группах промахивалась мимо.
 */
class MessageActionsAnchor {
    private val coordinates = mutableMapOf<String, LayoutCoordinates>()

    /**
     * Координаты нажатого пузыря для открытого меню. State, потому что оверлей появляется по
     * состоянию из презентера и координаты должны быть готовы к его компоновке.
     */
    var bubbleBounds: Rect? by mutableStateOf(null)

    /**
     * Правка форка: список ленты, чтобы перед меню выдвинуть пузырь из-под шапки. Пузырь в меню —
     * снимок экрана, и шапка поверх пузыря попадала в снимок (находка сессии 2026-09-27, N-1).
     */
    var listState: LazyListState? = null

    fun register(id: String, layoutCoordinates: LayoutCoordinates) {
        coordinates[id] = layoutCoordinates
    }

    fun unregister(id: String) {
        coordinates.remove(id)
    }

    /** `boundsInWindow` пузыря по id, либо null (пузырь ушёл с экрана или ещё не размещён). */
    fun boundsFor(id: String): Rect? =
        coordinates[id]?.takeIf { it.isAttached }?.boundsInWindow()

    /**
     * Верх пузыря в окне без обрезки краем ленты. `boundsInWindow` для пузыря, ушедшего под верх
     * списка, даёт 0, и сдвиг «выдвинуть из-под шапки» получался меньше нужного.
     */
    fun unclippedTopFor(id: String): Float? =
        coordinates[id]?.takeIf { it.isAttached }?.positionInWindow()?.y
}

/**
 * По умолчанию null: в превью и тестах якоря нет, и меню откатывается на шторку снизу.
 * `static`, потому что значение задаётся раз за сессию экрана, а не туда-сюда.
 */
val LocalMessageActionsAnchor = staticCompositionLocalOf<MessageActionsAnchor?> { null }
