/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline

import io.element.android.features.messages.impl.timeline.factories.dropEmptyDaySeparators
import io.element.android.features.messages.impl.timeline.model.TimelineItem
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemHistoryClearedContent
import io.element.android.features.messages.impl.timeline.model.virtual.TimelineItemDaySeparatorModel
import kotlinx.collections.immutable.toImmutableList

/** Лента после «Очистить историю». */
internal data class ClearedHistoryTimeline(
    val items: List<TimelineItem>,
    /** Время самой свежей отметки «очищено у обоих» в загруженной ленте, если она есть. */
    val markerTs: Long?,
    /** Лента догрузилась до отметки: всё старше неё скрыто, назад листать незачем. */
    val reachedCutoff: Boolean,
)

/**
 * Прячет всё, что было до отметки очистки: [clearedUpTo] (у себя, из account data) или отметки
 * «у обоих» — состояния комнаты, которое пришло в ленте. Список идёт от новых к старым.
 * Неотправленные сообщения (без eventId) не трогаем: их время — время устройства.
 */
internal fun List<TimelineItem>.applyClearedHistory(clearedUpTo: Long?): ClearedHistoryTimeline {
    val markerTs = flatMap { it.events() }.filter { it.isHistoryClearedMarker() }.maxOfOrNull { it.sentTimeMillis }
    val cutoff = listOfNotNull(clearedUpTo, markerTs).maxOrNull()
        ?: return ClearedHistoryTimeline(items = this, markerTs = null, reachedCutoff = false)
    val reachedCutoff = markerTs != null || flatMap { it.events() }.any { it.isRemote && it.sentTimeMillis <= cutoff }

    fun TimelineItem.Event.isHidden() = isHistoryClearedMarker() || (isRemote && sentTimeMillis <= cutoff)
    val kept = mapNotNull { item ->
        when (item) {
            is TimelineItem.Event -> item.takeUnless { it.isHidden() }
            is TimelineItem.GroupedEvents -> {
                val events = item.events.filterNot { it.isHidden() }
                when {
                    events.isEmpty() -> null
                    events.size == item.events.size -> item
                    else -> item.copy(events = events.toImmutableList())
                }
            }
            is TimelineItem.Virtual -> item
        }
    }
    if (!reachedCutoff) return ClearedHistoryTimeline(items = kept.dropEmptyDaySeparators(), markerTs = markerTs, reachedCutoff = false)
    // Старше последнего оставшегося сообщения остаются только плашки дней: индикатор догрузки,
    // «начало чата» и черта «Непрочитанные» над пустотой не нужны. Плашка дня, чьи сообщения все
    // скрыты, тоже уходит.
    val oldestContentIndex = kept.indexOfLast { it !is TimelineItem.Virtual }
    val trimmed = kept.filterIndexed { index, item ->
        index <= oldestContentIndex || (item is TimelineItem.Virtual && item.model is TimelineItemDaySeparatorModel)
    }
    return ClearedHistoryTimeline(items = trimmed.dropEmptyDaySeparators(), markerTs = markerTs, reachedCutoff = true)
}

private fun TimelineItem.events(): List<TimelineItem.Event> = when (this) {
    is TimelineItem.Event -> listOf(this)
    is TimelineItem.GroupedEvents -> events
    is TimelineItem.Virtual -> emptyList()
}

private fun TimelineItem.Event.isHistoryClearedMarker() = content is TimelineItemHistoryClearedContent
