/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.model.bubble

import io.element.android.features.messages.impl.timeline.TimelineRoomInfo
import io.element.android.features.messages.impl.timeline.model.TimelineItemGroupPosition

data class BubbleState(
    val groupPosition: TimelineItemGroupPosition,
    val isMine: Boolean,
    val timelineRoomInfo: TimelineRoomInfo,
) {
    /**
     * True to cut out the top start corner of the bubble, to give margin for the sender avatar.
     *
     * Правка форка: всегда false. Аватар больше не наезжает на угол пузыря сверху, он стоит
     * слева от него, поэтому вырезать под него нечего.
     */
    val cutTopStart: Boolean = false
}
