/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.messageDeliveredIcon
import io.element.android.libraries.ui.strings.CommonStrings

/**
 * Telegram shows one tick for "left the device", two for "the other side has seen it". Matrix has
 * both facts, just spelled differently: the local send state, and read receipts.
 */
enum class MessageDeliveryState {
    Sending,
    Sent,
    Read,
}

private val TICKS_SIZE = 16.dp
private val TICK_STROKE = 1.5.dp

/**
 * The ticks are drawn rather than shipped as an asset: Compound has a single `Check`, and stacking
 * two of them cannot produce the overlap Telegram uses.
 */
@Composable
fun MessageDeliveryTicks(
    state: MessageDeliveryState,
    modifier: Modifier = Modifier,
) {
    when (state) {
        MessageDeliveryState.Sending -> Icon(
            imageVector = CompoundIcons.Time(),
            contentDescription = stringResource(CommonStrings.common_sending),
            tint = ElementTheme.colors.textSecondary,
            modifier = modifier.size(14.dp),
        )
        MessageDeliveryState.Sent,
        MessageDeliveryState.Read -> {
            val color = ElementTheme.colors.messageDeliveredIcon
            val isRead = state == MessageDeliveryState.Read
            val description = stringResource(
                if (isRead) CommonStrings.common_seen_by else CommonStrings.common_sent
            )
            Canvas(
                modifier = modifier
                    .size(TICKS_SIZE, TICKS_SIZE * 0.7f)
                    .semantics { contentDescription = description },
            ) {
                val stroke = Stroke(
                    width = TICK_STROKE.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                )
                if (isRead) {
                    // Two ticks, the second peeking out from behind the first.
                    drawTick(xScale = 0.62f, xOffset = 0f, color = color, stroke = stroke)
                    drawTick(xScale = 0.62f, xOffset = size.width * 0.38f, color = color, stroke = stroke)
                } else {
                    drawTick(xScale = 0.78f, xOffset = size.width * 0.11f, color = color, stroke = stroke)
                }
            }
        }
    }
}

private fun DrawScope.drawTick(
    xScale: Float,
    xOffset: Float,
    color: Color,
    stroke: Stroke,
) {
    val w = size.width * xScale
    val h = size.height
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(xOffset + w * 0.06f, h * 0.55f)
        lineTo(xOffset + w * 0.36f, h * 0.86f)
        lineTo(xOffset + w * 0.94f, h * 0.16f)
    }
    drawPath(path = path, color = color, style = stroke)
}

@PreviewsDayNight
@Composable
internal fun MessageDeliveryTicksPreview() = ElementPreview {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MessageDeliveryState.entries.forEach { state ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = state.name, style = ElementTheme.typography.fontBodyMdRegular)
                MessageDeliveryTicks(state = state)
            }
        }
    }
}
