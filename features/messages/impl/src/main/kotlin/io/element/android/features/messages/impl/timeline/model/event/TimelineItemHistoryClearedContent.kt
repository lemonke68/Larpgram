/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.model.event

/**
 * Отметка «история очищена у обоих» (состояние комнаты `HISTORY_CLEARED_STATE_TYPE`). В ленте не
 * рисуется: по её времени `applyClearedHistory` прячет всё, что было раньше.
 */
data object TimelineItemHistoryClearedContent : TimelineItemStateContent {
    override val body: String = ""
    override val type: String = "TimelineItemHistoryClearedContent"
}
