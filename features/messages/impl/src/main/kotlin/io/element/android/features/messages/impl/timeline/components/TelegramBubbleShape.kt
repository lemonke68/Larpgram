/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.chatWallpaperBackground
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.messageFromMeBackground
import io.element.android.libraries.designsystem.theme.messageFromOtherBackground

/**
 * Which side of the bubble the sender is on, in start/end terms rather than left/right so that RTL
 * keeps working. The tail, when there is one, grows on that side.
 */
enum class BubbleTailSide {
    Start,
    End,
}

/**
 * A message bubble: a generously rounded rectangle with a small nub at the bottom, pointing towards
 * the sender.
 *
 * Measured off the 2023 redesign contest kit (`design/tg-ref/redesign2023/component_message.png`),
 * not the old iOS one: corners are ~20dp, and the tail is a plain 3.5dp x 8dp nub with a rounded
 * tip. The classic client carves a deep concave scoop under its tail; this one does not, and reads
 * calmer for it.
 *
 * Every bubble gets a tail here, including the middle of a group. That is what the kit does, and it
 * removes the group-position corner juggling entirely.
 *
 * The tail lives *inside* the bubble bounds: the body is inset by [tailWidth] on [tailSide] whether
 * or not a tail is drawn, so bubbles keep a common body edge. Callers must pad the content by the
 * same amount on that side.
 */
data class TelegramBubbleShape(
    val topStart: Dp,
    val topEnd: Dp,
    val bottomEnd: Dp,
    val bottomStart: Dp,
    val tailSide: BubbleTailSide,
    val hasTail: Boolean,
    val tailWidth: Dp = TAIL_WIDTH,
    val tailHeight: Dp = TAIL_HEIGHT,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val rtl = layoutDirection == LayoutDirection.Rtl
        val tailOnLeft = (tailSide == BubbleTailSide.Start) != rtl

        with(density) {
            val tailW = tailWidth.toPx()
            val tailH = tailHeight.toPx()

            val leftTop = (if (rtl) topEnd else topStart).toPx()
            val leftBottom = (if (rtl) bottomEnd else bottomStart).toPx()
            val rightTop = (if (rtl) topStart else topEnd).toPx()
            val rightBottom = (if (rtl) bottomStart else bottomEnd).toPx()

            // The tail corner is squared off, the hook takes its place.
            val squaredLeft = hasTail && tailOnLeft
            val squaredRight = hasTail && !tailOnLeft

            val body = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = if (tailOnLeft) tailW else 0f,
                        top = 0f,
                        right = if (tailOnLeft) size.width else size.width - tailW,
                        bottom = size.height,
                        topLeftCornerRadius = CornerRadius(leftTop),
                        topRightCornerRadius = CornerRadius(rightTop),
                        bottomLeftCornerRadius = CornerRadius(if (squaredLeft) 0f else leftBottom),
                        bottomRightCornerRadius = CornerRadius(if (squaredRight) 0f else rightBottom),
                    )
                )
            }

            if (!hasTail) return Outline.Generic(body)

            val bottom = size.height
            // Everything below is written for a tail pointing outwards from the body edge; `dir`
            // flips it to the other side.
            val dir = if (tailOnLeft) -1f else 1f
            val edge = if (tailOnLeft) tailW else size.width - tailW
            fun x(offset: Float) = edge + dir * offset

            // The nub. It hugs the body for most of its height and only flares out near the bottom,
            // which is what the measured profile does; a straight diagonal reads as a clipped
            // corner instead of a tail.
            val tipRound = tailW * 0.45f
            val tail = Path().apply {
                moveTo(x(0f), bottom - tailH)
                cubicTo(
                    x(tailW * 0.03f),
                    bottom - tailH * 0.87f,
                    x(tailW),
                    bottom - tailH * 0.12f,
                    x(tailW),
                    bottom - tipRound,
                )
                // Round the very tip instead of leaving a needle point.
                quadraticTo(x(tailW), bottom, x(tailW - tipRound), bottom)
                lineTo(x(0f), bottom)
                close()
            }

            return Outline.Generic(Path().apply { op(body, tail, PathOperation.Union) })
        }
    }

    companion object {
        /** Body corner radius. Large and uniform, as in the 2023 redesign kit. */
        val BUBBLE_RADIUS = 20.dp
        val TAIL_WIDTH = 3.5.dp
        val TAIL_HEIGHT = 8.dp
    }
}

/**
 * Blown-up view of the shape on its own, so the tail can actually be judged. 44dp is the height of
 * a single-line bubble in the kit, which makes this directly comparable to
 * `design/tg-ref/redesign2023/component_message.png`.
 */
@PreviewsDayNight
@Composable
internal fun TelegramBubbleShapePreview() = ElementPreview {
    Column(
        modifier = Modifier
            .background(ElementTheme.colors.chatWallpaperBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        listOf(
            "incoming, tail" to (BubbleTailSide.Start to true),
            "incoming, grouped" to (BubbleTailSide.Start to false),
            "outgoing, tail" to (BubbleTailSide.End to true),
            "outgoing, grouped" to (BubbleTailSide.End to false),
        ).forEach { (label, config) ->
            val (side, hasTail) = config
            Box(
                modifier = Modifier
                    .size(width = 260.dp, height = 44.dp)
                    .background(
                        color = if (side == BubbleTailSide.End) {
                            ElementTheme.colors.messageFromMeBackground
                        } else {
                            ElementTheme.colors.messageFromOtherBackground
                        },
                        shape = TelegramBubbleShape(
                            topStart = TelegramBubbleShape.BUBBLE_RADIUS,
                            topEnd = TelegramBubbleShape.BUBBLE_RADIUS,
                            bottomEnd = TelegramBubbleShape.BUBBLE_RADIUS,
                            bottomStart = TelegramBubbleShape.BUBBLE_RADIUS,
                            tailSide = side,
                            hasTail = hasTail,
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = label, style = ElementTheme.typography.fontBodyMdRegular)
            }
        }
    }
}
