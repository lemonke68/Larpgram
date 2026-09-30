/*
 * Copyright (c) 2026 Larpgram.
 * Правка форка: всё, что экрану чата Telegram нужно сверх элементовского `MessagesState`.
 * Одним полем, чтобы не расползаться по файлам Element (аудит C-009).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl

import io.element.android.features.circles.impl.CircleRecorderState
import io.element.android.features.gifs.impl.GifPickerState
import io.element.android.features.messages.impl.attachments.tgattach.TgAttachRestore
import io.element.android.features.messages.impl.chatcleanup.ChatCleanupState
import io.element.android.features.stickers.impl.StickerPickerState
import io.element.android.libraries.imagepacks.api.ImagePackSource
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.media.MatrixMediaLoader

data class TgChatState(
    /** Канал: писать могут только админы (eventsDefault поднят). */
    val isChannel: Boolean,
    /** Звук чата выключен. Считается для любого чата: пункт «Выключить уведомления» есть в ⋮ везде. */
    val isMuted: Boolean,
    /** Подписчики в шапке канала; null — не канал. */
    val channelSubscriberCount: Long?,
    /** Собеседник ЛС заблокирован (в ignoredUsers): вместо поля ввода — полоса «Разблокировать». */
    val isUserBlocked: Boolean,
    /** Собеседник ЛС — для «в сети / был(а)» в шапке. */
    val dmUserId: UserId?,
    /** Участники группы для подзаголовка; null в «Избранном». */
    val memberCount: Long?,
    // Пикеры стикеров и гифок, запись кружочка, меню ⋮; null в превью и тестах.
    val stickerPickerState: StickerPickerState?,
    val gifPickerState: GifPickerState?,
    val circleRecorderState: CircleRecorderState?,
    val chatCleanupState: ChatCleanupState?,
    /** Загрузчик медиа для кружочков в ленте; null в превью и тестах. */
    val circleMediaLoader: MatrixMediaLoader?,
    /** Источник стикер-паков: по тапу на стикер показываем его пак. */
    val imagePackSource: ImagePackSource?,
    /** Меню вложений, которое надо открыть снова после «Назад» с предпросмотра; null — не надо. */
    val attachRestore: TgAttachRestore?,
    val eventSink: (TgChatEvent) -> Unit,
)

sealed interface TgChatEvent {
    /** Выключить или включить звук чата (полоса подписчика канала, ⋮). */
    data object ToggleMute : TgChatEvent

    /** Снять блок с собеседника ЛС (unignoreUser). */
    data object UnblockUser : TgChatEvent

    /** Меню вложений открыто снова из [TgChatState.attachRestore]. */
    data object AttachRestored : TgChatEvent
}

fun aTgChatState(
    isChannel: Boolean = false,
    isMuted: Boolean = false,
    channelSubscriberCount: Long? = null,
    isUserBlocked: Boolean = false,
    dmUserId: UserId? = null,
    memberCount: Long? = null,
    eventSink: (TgChatEvent) -> Unit = {},
) = TgChatState(
    isChannel = isChannel,
    isMuted = isMuted,
    channelSubscriberCount = channelSubscriberCount,
    isUserBlocked = isUserBlocked,
    dmUserId = dmUserId,
    memberCount = memberCount,
    stickerPickerState = null,
    gifPickerState = null,
    circleRecorderState = null,
    chatCleanupState = null,
    circleMediaLoader = null,
    imagePackSource = null,
    attachRestore = null,
    eventSink = eventSink,
)
