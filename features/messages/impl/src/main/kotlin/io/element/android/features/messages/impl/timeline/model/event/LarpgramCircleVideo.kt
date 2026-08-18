/*
 * Правка форка: кружочки, круглые видеосообщения как в Telegram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.messages.impl.timeline.model.event

import io.element.android.libraries.matrix.api.timeline.item.event.LARPGRAM_CIRCLE_FILENAME_PREFIX

/**
 * Кружочек ли это.
 *
 * Сам маркер и его обоснование лежат в
 * [io.element.android.libraries.matrix.api.timeline.item.event.LARPGRAM_CIRCLE_FILENAME_PREFIX]:
 * тот же признак нужен списку чатов и уведомлениям, а туда модель таймлайна не доезжает.
 */
val TimelineItemVideoContent.isLarpgramCircle: Boolean
    get() = filename.startsWith(LARPGRAM_CIRCLE_FILENAME_PREFIX)
