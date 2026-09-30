/*
 * Copyright (c) 2026 Larpgram.
 * Правка форка: подвал поста канала с комментариями, как в Telegram. Вынесено из `TimelineItemEventRow.kt` (аудит C-009).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.messages.impl.R
import io.element.android.libraries.designsystem.components.EqualWidthColumn
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.ui.messages.reply.content

// Larpgram (роумлесс, gap D): TG-чип «Комментарии», влитый нижней секцией в карточку поста канала.
// Ширину получает от PostWithCommentsFooter (= ширина контента поста); текст на weight ужимается на
// узком посте. Без делителя — по требованию дизайна.
@Composable
internal fun ChannelPostCommentsFooter(
    count: Long?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = CompoundIcons.Chat(),
            contentDescription = null,
            tint = ElementTheme.colors.textActionAccent,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        // weight + ellipsis: на узком посте (вертикальное медиа) текст ужимается/обрезается, а не
        // раздвигает рамку — ширину задаёт контент поста (см. PostWithCommentsFooter).
        Text(
            text = if (count != null && count > 0) {
                pluralStringResource(id = R.plurals.channel_comments_count, count = count.toInt(), count.toInt())
            } else {
                stringResource(id = R.string.screen_channel_comments)
            },
            style = ElementTheme.typography.fontBodyMdMedium,
            color = ElementTheme.colors.textActionAccent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = CompoundIcons.ChevronRight(),
            contentDescription = null,
            tint = ElementTheme.colors.iconTertiary,
            modifier = Modifier.size(18.dp),
        )
    }
}

// Larpgram: круглый бабл комментариев под безпузырным постом канала (стикер/гифка/кружок), где влить
// чип в карточку некуда. TG-стиль: компактный круг «иконка (+число)», без слова «Комментарии» — оно
// раздувало пилюлю под мелким стикером. CircleShape → круг при пустом счётчике, стадион при числе.
@Composable
internal fun StandaloneChannelCommentsChip(
    count: Long?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasCount = count != null && count > 0
    val a11yLabel = if (hasCount) {
        pluralStringResource(id = R.plurals.channel_comments_count, count = count!!.toInt(), count.toInt())
    } else {
        stringResource(id = R.string.screen_channel_comments)
    }
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(ElementTheme.colors.bgSubtleSecondary)
            .clickable(onClick = onClick)
            .semantics { contentDescription = a11yLabel }
            .padding(horizontal = if (hasCount) 10.dp else 7.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = CompoundIcons.Chat(),
            contentDescription = null,
            tint = ElementTheme.colors.textActionAccent,
            modifier = Modifier.size(18.dp),
        )
        if (hasCount) {
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = count!!.toInt().toString(),
                style = ElementTheme.typography.fontBodyMdMedium,
                color = ElementTheme.colors.textActionAccent,
                maxLines = 1,
            )
        }
    }
}

/**
 * Larpgram: колонка «контент поста + футер комментариев», где футер ПРИНИМАЕТ ширину контента, а не
 * навязывает свою (в отличие от [EqualWidthColumn], который берёт максимум). Контент меряется первым;
 * футер меряется с фиксированной шириной = ширине контента, поэтому рамка комментов всегда точно по
 * размеру поста — голосовое, картинка, текст, узкое вертикальное медиа.
 */
@Composable
internal fun PostWithCommentsFooter(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
    footer: @Composable () -> Unit,
) {
    SubcomposeLayout(modifier = modifier) { constraints ->
        val contentPlaceables = subcompose("content", content).map { it.measure(constraints) }
        val width = contentPlaceables.maxOfOrNull { it.width } ?: 0
        val contentHeight = contentPlaceables.sumOf { it.height }
        val footerConstraints = constraints.copy(minWidth = width, maxWidth = width)
        val footerPlaceables = subcompose("footer", footer).map { it.measure(footerConstraints) }
        val footerHeight = footerPlaceables.sumOf { it.height }
        layout(width, contentHeight + footerHeight) {
            var y = 0
            contentPlaceables.forEach {
                it.placeRelative(0, y)
                y += it.height
            }
            footerPlaceables.forEach {
                it.placeRelative(0, y)
                y += it.height
            }
        }
    }
}

@Composable
internal fun ChannelCommentsDivider() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        Text(
            text = stringResource(id = R.string.screen_channel_comments),
            style = ElementTheme.typography.fontBodySmMedium,
            color = ElementTheme.colors.textSecondary,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
}
