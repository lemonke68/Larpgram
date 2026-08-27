/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.messages.impl.timeline.model.event.GalleryItem
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemGalleryContent
import io.element.android.libraries.designsystem.components.blurhash.blurHashBackground
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.ui.media.contentvalidation.ContentValidationValue
import io.element.android.libraries.matrix.ui.media.contentvalidation.collectMediaState
import io.element.android.libraries.matrix.ui.media.contentvalidation.rememberEventContentValidationState
import io.element.android.libraries.ui.utils.time.formatShort

private const val MAX_TILES = 6
private val GALLERY_WIDTH = 264.dp
private val GRID_SPACING = 2.dp
private val GROUP_CORNER_RADIUS = 12.dp

// Границы высоты тайлов. Telegram-мозаика считает высоту рядов из аспектов, но не даёт им
// вырождаться: очень широкое фото не сплющивается в полоску, очень узкое не вытягивается на
// пол-экрана.
private val MIN_TILE_HEIGHT = 72.dp
private val MAX_ROW_HEIGHT = 200.dp
private val MAX_STACK_HEIGHT = 320.dp

/** Аспект тайла (ш/в), с запасными 1:1 и зажимом, чтобы крайние пропорции не ломали ряд. */
private fun GalleryItem.ratio(): Float = (aspectRatio ?: 1f).coerceIn(0.5f, 2.0f)

/** Естественная высота ряда «в строку»: при ней ширины ∝ аспектам и сумма равна ширине блока. */
private fun rowHeight(items: List<GalleryItem>): Dp {
    val sumRatio = items.fold(0f) { acc, item -> acc + item.ratio() }
    if (sumRatio <= 0f) return MIN_TILE_HEIGHT
    val available = GALLERY_WIDTH - GRID_SPACING * (items.size - 1)
    return (available / sumRatio).coerceIn(MIN_TILE_HEIGHT, MAX_ROW_HEIGHT)
}

/** Высота одного тайла «во всю ширину» из его аспекта. */
private fun stackHeight(item: GalleryItem): Dp =
    (GALLERY_WIDTH / item.ratio()).coerceIn(MIN_TILE_HEIGHT, MAX_STACK_HEIGHT)

/** Один тайл мозаики: индекс в галерее плюс пометка последнего с «+N». */
private data class GalleryCell(
    val index: Int,
    val item: GalleryItem,
    val isLast: Boolean = false,
    val remaining: Int = 0,
)

/**
 * Telegram-подобная мозаика альбома.
 *
 * Раскладка выбирается по аспектам, а не по фиксированным шаблонам: горизонтальные фото идут
 * стопкой, вертикальные — рядом, тройки/четвёрки складываются в «большой + столбик» либо в сетку.
 * Тайлы кадрируются (`ContentScale.Crop`) и стыкуются тонким швом цвета канваса, как в Telegram.
 * Геометрия снята со скриншотов TG-кита, код Telegram не переносится.
 */
@Composable
fun TimelineItemGalleryView(
    eventId: EventId?,
    content: TimelineItemGalleryContent,
    onGalleryItemClick: (Int) -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val items = content.items
    val totalItems = items.size
    val showOverflow = totalItems > MAX_TILES
    val overflowCount = totalItems - MAX_TILES
    // Тайлы сверх лимита в мозаике не рисуем — последний виден показывает «+N».
    val visible = items.take(MAX_TILES)

    Column(modifier = modifier) {
        Column(
            modifier = Modifier
                .width(GALLERY_WIDTH)
                .clip(RoundedCornerShape(GROUP_CORNER_RADIUS))
                // Нейтральные швы: без фона 2dp-зазоры просвечивают акцентным пузырём поста
                // канала — между фото шли фиолетовые полосы. Канвас читается как тонкий TG-шов.
                .background(ElementTheme.colors.bgCanvasDefault),
            verticalArrangement = Arrangement.spacedBy(GRID_SPACING),
        ) {
            val cells = visible.mapIndexed { index, item ->
                val isLastVisible = index == visible.lastIndex
                GalleryCell(
                    index = index,
                    item = item,
                    isLast = showOverflow && isLastVisible,
                    remaining = if (showOverflow && isLastVisible) overflowCount else 0,
                )
            }
            MosaicLayout(
                eventId = eventId,
                cells = cells,
                onItemClick = onGalleryItemClick,
                onLongClick = onLongClick,
            )
        }
    }
}

@Composable
private fun ColumnScope.MosaicLayout(
    eventId: EventId?,
    cells: List<GalleryCell>,
    onItemClick: (Int) -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val ratios = cells.map { it.item.ratio() }
    when (cells.size) {
        0 -> Unit
        1 -> StackedCell(eventId, cells[0], stackHeight(cells[0].item), onItemClick, onLongClick)
        2 -> {
            // Оба горизонтальные — стопкой; иначе рядом (два вертикальных встают в два столбца).
            if (ratios.all { it > 1f }) {
                cells.forEach { StackedCell(eventId, it, stackHeight(it.item), onItemClick, onLongClick) }
            } else {
                MosaicRow(eventId, cells, rowHeight(cells.map { it.item }), onItemClick, onLongClick)
            }
        }
        3 -> {
            if (ratios[0] < 1f) {
                // Первый вертикальный крупно слева, два справа в столбик.
                BigLeftWithColumn(eventId, cells, onItemClick, onLongClick)
            } else {
                // Первый во всю ширину сверху, два под ним в ряд.
                StackedCell(eventId, cells[0], stackHeight(cells[0].item), onItemClick, onLongClick)
                MosaicRow(eventId, cells.subList(1, 3), rowHeight(cells.subList(1, 3).map { it.item }), onItemClick, onLongClick)
            }
        }
        4 -> {
            if (ratios[0] > 1f) {
                // Широкий первый сверху, три под ним в ряд.
                StackedCell(eventId, cells[0], stackHeight(cells[0].item), onItemClick, onLongClick)
                MosaicRow(eventId, cells.subList(1, 4), rowHeight(cells.subList(1, 4).map { it.item }), onItemClick, onLongClick)
            } else {
                // Сетка 2x2.
                MosaicRow(eventId, cells.subList(0, 2), rowHeight(cells.subList(0, 2).map { it.item }), onItemClick, onLongClick)
                MosaicRow(eventId, cells.subList(2, 4), rowHeight(cells.subList(2, 4).map { it.item }), onItemClick, onLongClick)
            }
        }
        else -> {
            // 5–6: верхний ряд из двух, остальные (2–4) в нижнем ряду; «+N» на последнем.
            MosaicRow(eventId, cells.subList(0, 2), rowHeight(cells.subList(0, 2).map { it.item }), onItemClick, onLongClick)
            val bottom = cells.subList(2, cells.size)
            MosaicRow(eventId, bottom, rowHeight(bottom.map { it.item }), onItemClick, onLongClick)
        }
    }
}

/** Ряд тайлов равной высоты; ширины распределяются по аспектам (weight = аспект). */
@Composable
private fun MosaicRow(
    eventId: EventId?,
    cells: List<GalleryCell>,
    height: Dp,
    onItemClick: (Int) -> Unit,
    onLongClick: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(GRID_SPACING),
    ) {
        cells.forEach { cell ->
            GalleryItemCell(
                eventId = eventId,
                item = cell.item,
                isLast = cell.isLast,
                remainingCount = cell.remaining,
                onClick = { onItemClick(cell.index) },
                onLongClick = onLongClick,
                modifier = Modifier
                    .weight(cell.item.ratio())
                    .height(height),
            )
        }
    }
}

/** Один тайл во всю ширину блока. */
@Composable
private fun StackedCell(
    eventId: EventId?,
    cell: GalleryCell,
    height: Dp,
    onItemClick: (Int) -> Unit,
    onLongClick: (() -> Unit)?,
) {
    GalleryItemCell(
        eventId = eventId,
        item = cell.item,
        isLast = cell.isLast,
        remainingCount = cell.remaining,
        onClick = { onItemClick(cell.index) },
        onLongClick = onLongClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
    )
}

/** Крупный тайл слева на всю высоту, два тайла справа в столбик равной высоты. */
@Composable
private fun BigLeftWithColumn(
    eventId: EventId?,
    cells: List<GalleryCell>,
    onItemClick: (Int) -> Unit,
    onLongClick: (() -> Unit)?,
) {
    // Левый занимает ~2/3 ширины; высота блока — из его аспекта при этой ширине.
    val leftWidth = GALLERY_WIDTH * 2f / 3f
    val height = (leftWidth / cells[0].item.ratio()).coerceIn(140.dp, MAX_STACK_HEIGHT)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        horizontalArrangement = Arrangement.spacedBy(GRID_SPACING),
    ) {
        GalleryItemCell(
            eventId = eventId,
            item = cells[0].item,
            isLast = cells[0].isLast,
            remainingCount = cells[0].remaining,
            onClick = { onItemClick(cells[0].index) },
            onLongClick = onLongClick,
            modifier = Modifier
                .weight(2f)
                .fillMaxHeight(),
        )
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(GRID_SPACING),
        ) {
            for (i in 1..2) {
                GalleryItemCell(
                    eventId = eventId,
                    item = cells[i].item,
                    isLast = cells[i].isLast,
                    remainingCount = cells[i].remaining,
                    onClick = { onItemClick(cells[i].index) },
                    onLongClick = onLongClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
        }
    }
}

@Composable
private fun GalleryItemCell(
    eventId: EventId?,
    item: GalleryItem,
    isLast: Boolean,
    remainingCount: Int,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val eventContentValidationState = rememberEventContentValidationState(eventId, needsValidation = true)
    val thumbnailContentValidationState by eventContentValidationState.collectMediaState(item.thumbnailSource?.safeUrl)
    val mediaContentValidationState by eventContentValidationState.collectMediaState(item.mediaSource.safeUrl)

    val itemContentValidationState = remember(thumbnailContentValidationState, mediaContentValidationState) {
        if (thumbnailContentValidationState.isInvalid() || mediaContentValidationState.isInvalid()) {
            ContentValidationValue.Invalid
        } else if (thumbnailContentValidationState.hasUnrecoverableError() || mediaContentValidationState.hasUnrecoverableError()) {
            listOf(thumbnailContentValidationState, mediaContentValidationState).first { it is ContentValidationValue.UnrecoverableError }
        } else if (thumbnailContentValidationState.isLoading() || mediaContentValidationState.isLoading()) {
            ContentValidationValue.Loading
        } else {
            mediaContentValidationState
        }
    }

    Box(
        modifier = modifier
            .blurHashBackground(item.blurhash, alpha = 0.9f)
            .then(
                if (itemContentValidationState.isValid()) {
                    Modifier.combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            modifier = Modifier.fillMaxSize(),
            model = item.thumbnailMediaRequestData,
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
            contentDescription = item.filename,
        )

        if (item.type == GalleryItem.Type.Video) {
            VideoOverlay(duration = item.duration)
        }

        if (itemContentValidationState.isLoading()) {
            CircularProgressIndicator()
        } else if (itemContentValidationState.hasError()) {
            Box(
                modifier = Modifier.fillMaxSize().background(ElementTheme.colors.bgCriticalSubtle)
            ) {
                Icon(
                    modifier = Modifier.align(Alignment.Center),
                    imageVector = CompoundIcons.Error(),
                    tint = ElementTheme.colors.iconCriticalPrimary,
                    contentDescription = null,
                )
            }
        } else if (isLast && remainingCount > 0) {
            RemainingCountOverlay(count = remainingCount)
        }
    }
}

@Composable
private fun VideoOverlay(duration: kotlin.time.Duration) {
    val gradientColor = ElementTheme.colors.bgCanvasDefault

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .align(Alignment.BottomCenter)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(gradientColor.copy(alpha = 0f), gradientColor.copy(alpha = 1f))
                    )
                )
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = CompoundIcons.VideoCallSolid(),
                contentDescription = null,
                tint = ElementTheme.colors.textPrimary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = duration.formatShort(),
                color = ElementTheme.colors.textPrimary,
                style = ElementTheme.typography.fontBodySmMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RemainingCountOverlay(count: Int) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ElementTheme.colors.bgSubtleTertiary.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "+$count",
            color = Color.White,
            style = ElementTheme.typography.fontHeadingSmMedium,
        )
    }
}

@PreviewsDayNight
@Composable
internal fun TimelineItemGalleryViewPreview(
    @PreviewParameter(TimelineItemGalleryContentProvider::class) content: TimelineItemGalleryContent,
) = ElementPreview {
    TimelineItemGalleryView(
        eventId = null,
        content = content,
        onGalleryItemClick = {},
        onLongClick = {},
    )
}
