/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2022-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.messages.impl.timeline.model.TimelineItemGroupPosition
import io.element.android.features.messages.impl.timeline.model.bubble.BubbleState
import io.element.android.features.messages.impl.timeline.model.bubble.BubbleStateProvider
import io.element.android.libraries.core.extensions.to01
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.modifiers.onKeyboardContextMenuAction
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.text.toDp
import io.element.android.libraries.designsystem.text.toPx
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.messageFromMeBackground
import io.element.android.libraries.designsystem.theme.messageFromOtherBackground
import io.element.android.libraries.testtags.TestTags
import io.element.android.libraries.testtags.testTag
import io.element.android.libraries.ui.utils.a11y.isTalkbackActive
import io.element.android.libraries.ui.utils.graphics.drawInLayer

private val BUBBLE_RADIUS = TelegramBubbleShape.BUBBLE_RADIUS
private val avatarRadius = AvatarSize.TimelineSender.dp / 2

private val MIN_BUBBLE_WIDTH = 80.dp

@Composable
fun MessageEventBubble(
    state: BubbleState,
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    customBackgroundColor: Color? = null,
    borderColor: Color? = null,
    // Правка форка: стикеры, гифки и кружочки рисуются прямо на обоях. Тогда ни подложки,
    // ни хвостика, ни отступа под него быть не должно.
    showBubble: Boolean = true,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val clickableModifier = if (isTalkbackActive()) {
        Modifier
    } else {
        Modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                indication = ripple(),
                interactionSource = interactionSource
            )
            .onKeyboardContextMenuAction(onLongClick)
    }

    val cutTopStart = state.cutTopStart && showBubble
    // Ignore state.isHighlighted for now, we need a design decision on it.
    val backgroundBubbleColor by rememberUpdatedState(
        when {
            !showBubble -> Color.Transparent
            else -> customBackgroundColor ?: MessageEventBubbleDefaults.backgroundBubbleColor(state.isMine)
        }
    )
    val bubbleShape = remember(state, showBubble) {
        if (showBubble) {
            MessageEventBubbleDefaults.shape(state.cutTopStart, state.groupPosition, state.isMine)
        } else {
            RectangleShape
        }
    }
    val radiusPx = (avatarRadius + SENDER_AVATAR_BORDER_WIDTH).toPx()
    val yOffsetPx = -(NEGATIVE_MARGIN_FOR_BUBBLE + avatarRadius).toPx()

    val updatedBorderColor by rememberUpdatedState(borderColor)
    BoxWithConstraints(
        modifier = modifier
            .drawWithCache {
                // Calculate the outline of the background and cache it
                val outline = bubbleShape.createOutline(size, layoutDirection, this)

                onDrawWithContent {
                    // Draw the contents in a layer to be able to clip them with the same outline
                    // For some reason, doing this clipping outside a layer messes up with the touch events
                    drawInLayer(
                        composingStrategy = CompositingStrategy.Offscreen,
                        outline = outline,
                        clip = true,
                    ) {
                        // Draw the background first, so that it's behind the content
                        drawRect(backgroundBubbleColor)

                        // Then draw the content on top of it
                        drawContent()

                        // Draw border color, if any
                        updatedBorderColor?.let { drawOutline(outline, it, style = Stroke(width = 1.dp.toPx())) }

                        // And then clip the top start corner if needed to make room for the avatar
                        if (cutTopStart) {
                            drawCircle(
                                color = Color.Black,
                                center = Offset(
                                    x = if (layoutDirection == LayoutDirection.Rtl) size.width else 0f,
                                    y = yOffsetPx,
                                ),
                                radius = radiusPx,
                                blendMode = BlendMode.Clear,
                            )
                        }
                    }
                }
            },
        // Need to set the contentAlignment again (it's already set in TimelineItemEventRow), for the case
        // when content width is low.
        contentAlignment = if (state.isMine) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .testTag(TestTags.messageBubble)
                .widthIn(
                    min = MIN_BUBBLE_WIDTH,
                    max = (constraints.maxWidth * MessageEventBubbleDefaults.BUBBLE_WIDTH_RATIO)
                        .toInt()
                        .toDp()
                )
                // The tail is drawn inside the bubble bounds, so keep the content off it.
                .padding(
                    start = if (state.isMine || !showBubble) 0.dp else TelegramBubbleShape.TAIL_WIDTH,
                    end = if (state.isMine && showBubble) TelegramBubbleShape.TAIL_WIDTH else 0.dp,
                )
                .then(clickableModifier),
            content = content,
        )
    }
}

object MessageEventBubbleDefaults {
    /**
     * Every bubble is the same shape: uniformly rounded, with a nub on the sender's side. The kit
     * gives grouped messages no special treatment, so the group position no longer changes the
     * corners — the only thing that still does is [cutTopStart], which makes room for the avatar.
     */
    @Suppress("UNUSED_PARAMETER")
    fun shape(cutTopStart: Boolean, groupPosition: TimelineItemGroupPosition, isMine: Boolean): Shape {
        return TelegramBubbleShape(
            topStart = if (cutTopStart) 0.dp else BUBBLE_RADIUS,
            topEnd = BUBBLE_RADIUS,
            bottomEnd = BUBBLE_RADIUS,
            bottomStart = BUBBLE_RADIUS,
            tailSide = if (isMine) BubbleTailSide.End else BubbleTailSide.Start,
            hasTail = true,
        )
    }

    @Composable
    fun backgroundBubbleColor(isMine: Boolean): Color {
        return if (isMine) {
            ElementTheme.colors.messageFromMeBackground
        } else {
            ElementTheme.colors.messageFromOtherBackground
        }
    }

    // Design says: The maximum width of a bubble is still 3/4 of the screen width. But try with 78% now.
    const val BUBBLE_WIDTH_RATIO = 0.78f
}

@PreviewsDayNight
@Composable
internal fun MessageEventBubblePreview(@PreviewParameter(BubbleStateProvider::class) state: BubbleState) = ElementPreview {
    // Due to position offset, surround with a Box
    Box(
        modifier = Modifier
            .size(width = 240.dp, height = 64.dp)
            .padding(vertical = 8.dp),
        contentAlignment = if (state.isMine) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        MessageEventBubble(
            state = state,
            interactionSource = remember { MutableInteractionSource() },
            onClick = {},
            onLongClick = {},
        ) {
            // Render the state as a text to better understand the previews
            Box(
                modifier = Modifier
                    .size(width = 120.dp, height = 32.dp)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${state.groupPosition.javaClass.simpleName} isMine:${state.isMine.to01()}",
                    style = ElementTheme.typography.fontBodyXsRegular,
                )
            }
        }
    }
}
