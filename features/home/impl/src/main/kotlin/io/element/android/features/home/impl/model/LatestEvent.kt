/*
 * Copyright (c) 2025 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.model

import androidx.compose.runtime.Immutable
import io.element.android.libraries.designsystem.components.messages.MessageDeliveryState
import io.element.android.libraries.matrix.api.media.MediaSource

@Immutable
sealed interface LatestEvent {
    data object None : LatestEvent

    data class Synced(
        val content: CharSequence?,
        /** Правка форка: своё сообщение помечается галочками в строке списка. */
        val isOwn: Boolean = false,
        /** Правка форка: мини-превью медиа перед текстом, как в Telegram. */
        val thumbnail: MediaSource? = null,
    ) : LatestEvent

    data class Sending(
        val content: CharSequence?,
        val thumbnail: MediaSource? = null,
    ) : LatestEvent

    data object Error : LatestEvent

    fun content(): CharSequence? {
        return when (this) {
            is None -> null
            is Synced -> content
            is Sending -> content
            is Error -> null
        }
    }

    /** Картинка для мини-превью, если последнее событие её вообще имеет. */
    fun thumbnail(): MediaSource? = when (this) {
        is Synced -> thumbnail
        is Sending -> thumbnail
        is None, is Error -> null
    }

    /**
     * Правка форка: состояние доставки для галочек, как в Telegram.
     *
     * Двух галочек тут быть не может: чужие read receipts в сводке комнаты не приезжают
     * (в `RoomInfo` их нет вовсе), а лезть за ними в каждую комнату списка слишком дорого.
     * Поэтому «прочитано» показывает только таймлайн, а список различает часики и одну
     * галочку. У чужих сообщений галочек нет вообще, как и в Telegram.
     */
    fun deliveryState(): MessageDeliveryState? = when (this) {
        is Sending -> MessageDeliveryState.Sending
        is Synced -> MessageDeliveryState.Sent.takeIf { isOwn }
        is None, is Error -> null
    }
}
