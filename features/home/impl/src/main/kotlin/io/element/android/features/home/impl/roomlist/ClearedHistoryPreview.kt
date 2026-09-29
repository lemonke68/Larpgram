/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.roomlist

import io.element.android.features.home.impl.model.LatestEvent
import io.element.android.features.home.impl.model.RoomListRoomSummary

/**
 * После «Очистить историю» строка чата в Telegram пустая. [clearedUpTo] — отметка очистки из
 * `ChatCleanupService`: последнее сообщение не новее её прячем, новое после очистки показываем.
 */
internal fun RoomListRoomSummary.withoutClearedLatestEvent(clearedUpTo: Long?): RoomListRoomSummary {
    val latestTs = latestEventTimestampMillis ?: return this
    return if (clearedUpTo != null && latestTs <= clearedUpTo) copy(latestEvent = LatestEvent.None) else this
}
