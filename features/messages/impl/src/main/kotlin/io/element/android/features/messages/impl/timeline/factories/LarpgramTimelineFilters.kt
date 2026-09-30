/*
 * Copyright (c) 2026 Larpgram.
 * Правка форка: фильтры ленты Telegram поверх элементовской сборки элементов. Вынесено из `TimelineItemsFactory.kt` (аудит C-009).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.factories

import io.element.android.features.messages.impl.timeline.model.TimelineItem
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemRedactedContent
import io.element.android.features.messages.impl.timeline.model.virtual.TimelineItemDaySeparatorModel

/**
 * Правка форка: квитанция о прочтении стоит только на последнем прочитанном событии, но значит
 * «прочитано всё до него». Список идёт от новых к старым: всё своё после первой встреченной
 * квитанции помечаем прочитанным ([TimelineItem.Event.isReadByOthers]).
 */
internal fun List<TimelineItem>.markReadUpToLatestReceipt(): List<TimelineItem> {
    var seenReceipt = false
    return map { item ->
        when (item) {
            is TimelineItem.Event -> when {
                item.readReceiptState.receipts.isNotEmpty() -> item.also { seenReceipt = true }
                seenReceipt && item.isMine && !item.isReadByOthers -> item.copy(isReadByOthers = true)
                else -> item
            }
            is TimelineItem.GroupedEvents -> item.also { if (item.aggregatedReadReceipts.isNotEmpty()) seenReceipt = true }
            is TimelineItem.Virtual -> item
        }
    }
}

/**
 * Правка форка: плашка дня без единого сообщения под ней (все сообщения дня скрыты — например,
 * нерасшифрованные) не нужна. Список идёт от новых к старым, поэтому сообщения дня стоят в списке
 * перед его плашкой: если перед плашкой не событие, день пустой.
 */
internal fun List<TimelineItem>.dropEmptyDaySeparators(): List<TimelineItem> =
    filterIndexed { index, item ->
        val isDaySeparator = item is TimelineItem.Virtual && item.model is TimelineItemDaySeparatorModel
        if (!isDaySeparator) return@filterIndexed true
        val previous = (index - 1 downTo 0).asSequence()
            .map { this[it] }
            .firstOrNull { it !is TimelineItem.Virtual || it.model is TimelineItemDaySeparatorModel }
        previous is TimelineItem.Event || previous is TimelineItem.GroupedEvents
    }

internal val roomCreateTypeRegex = Regex(""""type"\s*:\s*"m\.room\.create"""")

/**
 * Правка форка: свёрнутый блок «N изменений в комнате» от создания чата (m.room.create, вход
 * создателя, права, правила входа…) в Telegram не показывается: новый чат пустой. Прячем группу
 * служебных событий, в которой есть само создание комнаты. В других клиентах события на месте.
 */
internal fun List<TimelineItem>.dropRoomCreationGroup(): List<TimelineItem> =
    filterNot { item ->
        item is TimelineItem.GroupedEvents &&
            item.events.any { event -> event.debugInfo.originalJson?.let(roomCreateTypeRegex::containsMatchIn) == true }
    }

/**
 * Правка форка: удалённое сообщение в Telegram исчезает, плашки «Сообщение удалено» нет.
 * Прячем только у себя: redaction — штатное событие Matrix, другие клиенты рисуют его как обычно.
 */
internal fun List<TimelineItem>.dropRedactedMessages(): List<TimelineItem> =
    // Удалённое сообщение, у которого есть ветка (корень или ответ), оставляем: без него
    // ветку не открыть.
    filterNot { it is TimelineItem.Event && it.content is TimelineItemRedactedContent && it.threadInfo == null }
