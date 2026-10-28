/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.virtual

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.tokens.LarpgramType
import io.element.android.compound.tokens.larpgramColors
import io.element.android.features.messages.impl.timeline.model.virtual.TimelineItemDaySeparatorModel
import io.element.android.features.messages.impl.timeline.model.virtual.TimelineItemDaySeparatorModelPreviewParam
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Surface
import io.element.android.libraries.designsystem.theme.components.Text

@Composable
internal fun TimelineItemDaySeparatorView(
    model: TimelineItemDaySeparatorModel,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(vertical = 12.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Правка форка: дата в «таблетке», как в Telegram, а не голым текстом на обоях. Форма и
        // цвет те же, что у всплывающей даты при прокрутке (FloatingDateBadge), чтобы это
        // читалось как один и тот же элемент.
        Surface(
            // Правка форка (Larpgram V1 final): маленькая пилюля `dateChip` с белым текстом 12/800.
            shape = RoundedCornerShape(12.dp),
            color = larpgramColors.dateChip,
        ) {
            Text(
                modifier = Modifier
                    .padding(horizontal = 11.dp, vertical = 4.dp)
                    .semantics {
                        heading()
                    },
                text = model.formattedDate,
                style = LarpgramType.badge,
                color = Color.White,
            )
        }
    }
}

@PreviewsDayNight
@Composable
internal fun TimelineItemDaySeparatorViewPreview(
    @PreviewParameter(TimelineItemDaySeparatorModelPreviewParam::class) model: TimelineItemDaySeparatorModel
) = ElementPreview {
    TimelineItemDaySeparatorView(
        model = model,
    )
}
