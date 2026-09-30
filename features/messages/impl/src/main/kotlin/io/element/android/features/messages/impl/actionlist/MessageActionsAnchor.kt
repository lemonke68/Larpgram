/*
 * Copyright (c) 2026 Larpgram.
 * Правка форка: меню долгого нажатия привязано к самому сообщению (фаза 2).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.actionlist

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.unit.toSize
import io.element.android.libraries.core.extensions.runCatchingExceptions

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
    private val layers = mutableMapOf<String, GraphicsLayer>()

    /**
     * Координаты нажатого пузыря для открытого меню. State, потому что оверлей появляется по
     * состоянию из презентера и координаты должны быть готовы к его компоновке.
     */
    var bubbleBounds: Rect? by mutableStateOf(null)

    /**
     * Полоса экрана, где лента видна: от низа шапки до верха поля ввода. Пузырь в меню обрезается
     * по ней, как в Telegram (`ChatActivity`, отрисовка `scrimView` между шапкой и полем ввода).
     */
    var visibleTopPx: Float = 0f
    var visibleBottomPx: Float = Float.MAX_VALUE

    fun register(id: String, layoutCoordinates: LayoutCoordinates) {
        coordinates[id] = layoutCoordinates
    }

    /** Слой, в который пузырь рисует себя: из него меню берёт точную копию пузыря целиком. */
    fun registerLayer(id: String, layer: GraphicsLayer) {
        layers[id] = layer
    }

    fun unregister(id: String) {
        coordinates.remove(id)
        layers.remove(id)
    }

    /** `boundsInWindow` пузыря по id, либо null (пузырь ушёл с экрана или ещё не размещён). */
    fun boundsFor(id: String): Rect? =
        coordinates[id]?.takeIf { it.isAttached }?.boundsInWindow()

    /** Пузырь на экране целиком, без обрезки краями ленты. */
    fun unclippedBoundsFor(id: String): Rect? =
        coordinates[id]?.takeIf { it.isAttached }?.let { Rect(it.positionOnScreen(), it.size.toSize()) }

    /** Копия пузыря: его форма, хвост и содержимое, без шапки и поля ввода поверх. */
    suspend fun snapshotFor(id: String): ImageBitmap? {
        val layer = layers[id]?.takeIf { !it.isReleased && it.size.width > 0 && it.size.height > 0 } ?: return null
        return runCatchingExceptions { layer.toImageBitmap() }.getOrNull()
    }
}

/**
 * По умолчанию null: в превью и тестах якоря нет, и меню откатывается на шторку снизу.
 * `static`, потому что значение задаётся раз за сессию экрана, а не туда-сюда.
 */
val LocalMessageActionsAnchor = staticCompositionLocalOf<MessageActionsAnchor?> { null }
