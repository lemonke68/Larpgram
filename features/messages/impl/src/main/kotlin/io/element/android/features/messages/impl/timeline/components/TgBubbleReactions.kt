/*
 * Copyright (c) 2026 Larpgram.
 * Правка форка: реакции внутри пузыря, как в Telegram (аудит A-008).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.messages.impl.timeline.model.AggregatedReaction
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.api.media.MediaSource
import io.element.android.libraries.matrix.ui.media.MediaRequestData
import kotlinx.collections.immutable.ImmutableList

/**
 * Ряд реакций в нижней части пузыря: пилюли «эмодзи + число», а справа в той же строке время
 * сообщения ([timestamp]), как в Telegram. Пилюли идут от начала строки и переносятся; время
 * встаёт в последнюю строку, если влезает, иначе отдельной строкой справа.
 *
 * Ширина — сколько нужно пилюлям и времени, но не меньше minWidth: в [EqualWidthColumn] ряд
 * получает ширину пузыря, и время прижимается к его правому краю.
 *
 * Цвета как в TG: своя реакция — залитая пилюля (в исходящем пузыре белая, во входящем цвета
 * акцента), чужая — полупрозрачная того же цвета.
 */
@Composable
internal fun TgBubbleReactions(
    reactions: ImmutableList<AggregatedReaction>,
    isMine: Boolean,
    userCanSendReaction: Boolean,
    onReactionClick: (emoji: String) -> Unit,
    onReactionLongClick: (emoji: String) -> Unit,
    timestamp: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val gapPx = with(density) { PILL_GAP.roundToPx() }
    val timestampGapPx = with(density) { TIMESTAMP_GAP.roundToPx() }
    Layout(
        modifier = modifier,
        content = {
            reactions.forEach { reaction ->
                ReactionPill(
                    reaction = reaction,
                    isMine = isMine,
                    onClick = {
                        // Свою реакцию снять можно всегда, поставить — только с правом реагировать.
                        if (reaction.isHighlighted || userCanSendReaction) onReactionClick(reaction.key)
                    },
                    onLongClick = { onReactionLongClick(reaction.key) },
                )
            }
            timestamp?.invoke()
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val pills = measurables.take(reactions.size).map { it.measure(loose) }
        val time = if (timestamp != null) measurables.getOrNull(reactions.size)?.measure(loose) else null
        val maxWidth = constraints.maxWidth

        // Раскладываем пилюли по строкам.
        val lines = mutableListOf(mutableListOf<Int>())
        var lineWidth = 0
        pills.forEachIndexed { index, pill ->
            val needed = if (lines.last().isEmpty()) pill.width else lineWidth + gapPx + pill.width
            if (lines.last().isNotEmpty() && needed > maxWidth) {
                lines += mutableListOf(index)
                lineWidth = pill.width
            } else {
                lines.last() += index
                lineWidth = needed
            }
        }
        val lineHeight = pills.maxOfOrNull { it.height } ?: 0
        val timeWidth = time?.width ?: 0
        val timeFitsLastLine = time == null || lineWidth + timestampGapPx + timeWidth <= maxWidth
        val widestLine = lines.maxOf { line -> line.sumOf { pills[it].width } + gapPx * (line.size - 1).coerceAtLeast(0) }
        val contentWidth = when {
            time == null -> widestLine
            timeFitsLastLine -> maxOf(widestLine, lineWidth + timestampGapPx + timeWidth)
            else -> maxOf(widestLine, timeWidth)
        }
        val width = contentWidth.coerceIn(constraints.minWidth, maxWidth)
        val pillsHeight = lines.size * lineHeight + (lines.size - 1) * gapPx
        val height = if (timeFitsLastLine) pillsHeight else pillsHeight + gapPx + (time?.height ?: 0)

        layout(width, height) {
            lines.forEachIndexed { lineIndex, line ->
                var x = 0
                val y = lineIndex * (lineHeight + gapPx)
                line.forEach { index ->
                    pills[index].placeRelative(x, y)
                    x += pills[index].width + gapPx
                }
            }
            time?.let {
                val y = if (timeFitsLastLine) {
                    (lines.size - 1) * (lineHeight + gapPx) + (lineHeight - it.height) / 2
                } else {
                    pillsHeight + gapPx
                }
                it.placeRelative(width - it.width, y)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReactionPill(
    reaction: AggregatedReaction,
    isMine: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val accent = ElementTheme.colors.bgAccentRest
    // В исходящем пузыре (он сам цвета акцента) основа белая, во входящем — акцент.
    val base = if (isMine) Color.White else accent
    val background = if (reaction.isHighlighted) base else base.copy(alpha = 0.18f)
    val contentColor = when {
        reaction.isHighlighted && isMine -> accent
        reaction.isHighlighted -> Color.White
        isMine -> Color.White
        else -> accent
    }
    Row(
        modifier = Modifier
            .height(PILL_HEIGHT)
            .clip(CircleShape)
            .background(background)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp)
            .semantics { contentDescription = "${reaction.displayKey} ${reaction.count}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (reaction.key.startsWith("mxc://")) {
            AsyncImage(
                modifier = Modifier
                    .height(18.dp)
                    .aspectRatio(1f),
                model = MediaRequestData(MediaSource(reaction.key), MediaRequestData.Kind.Content),
                contentDescription = null,
            )
        } else {
            Text(
                text = reaction.displayKey,
                style = ElementTheme.typography.fontBodyLgRegular,
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = reaction.count.toString(),
            color = contentColor,
            style = ElementTheme.typography.fontBodyMdMedium,
        )
    }
}

private val PILL_HEIGHT = 28.dp
private val PILL_GAP = 4.dp
private val TIMESTAMP_GAP = 8.dp

/**
 * Колонка «содержимое + ряд реакций» с одной композицией. [EqualWidthColumn] компонует детей
 * дважды (SubcomposeLayout) — в дереве появлялись дубли времени и сообщения. Здесь содержимое
 * меряется один раз, а ряд реакций — с minWidth по ширине содержимого, чтобы время стояло у
 * правого края пузыря.
 */
@Composable
internal fun ContentWithReactionsColumn(
    content: @Composable () -> Unit,
    reactions: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(
        contents = listOf(content, reactions),
        modifier = modifier,
    ) { (contentMeasurables, reactionMeasurables), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val contentPlaceables = contentMeasurables.map { it.measure(loose) }
        val contentWidth = contentPlaceables.maxOfOrNull { it.width } ?: 0
        val reactionConstraints = loose.copy(minWidth = contentWidth.coerceAtMost(loose.maxWidth))
        val reactionPlaceables = reactionMeasurables.map { it.measure(reactionConstraints) }
        val width = maxOf(contentWidth, reactionPlaceables.maxOfOrNull { it.width } ?: 0)
        val height = contentPlaceables.sumOf { it.height } + reactionPlaceables.sumOf { it.height }
        layout(width, height) {
            var y = 0
            (contentPlaceables + reactionPlaceables).forEach {
                it.placeRelative(0, y)
                y += it.height
            }
        }
    }
}
