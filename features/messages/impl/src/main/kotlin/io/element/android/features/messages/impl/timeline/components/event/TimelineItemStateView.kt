/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemStateContent
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemStateEventContent
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Surface
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.floatingDateBadgeBackground

@Composable
fun TimelineItemStateView(
    content: TimelineItemStateContent,
    modifier: Modifier = Modifier
) {
    // Правка форка: служебное сообщение — таблетка, как дата и как в Telegram (`ChatActionCell`),
    // а не серый текст на обоях, который на них не читался.
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = ElementTheme.colors.floatingDateBadgeBackground,
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            color = ElementTheme.colors.textPrimary,
            style = ElementTheme.typography.fontBodySmMedium,
            text = content.body,
            textAlign = TextAlign.Center,
        )
    }
}

@PreviewsDayNight
@Composable
internal fun TimelineItemStateViewPreview() = ElementPreview {
    TimelineItemStateView(
        content = aTimelineItemStateEventContent(),
    )
}
