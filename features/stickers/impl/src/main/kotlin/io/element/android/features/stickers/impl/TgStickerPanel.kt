/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.stickers.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.imagepacks.api.ImagePackImage
import io.element.android.libraries.matrix.api.media.MediaSource
import io.element.android.libraries.matrix.ui.media.MediaRequestData
import kotlinx.coroutines.launch

/**
 * Правка форка: вкладка «Стикеры» панели Telegram (`EmojiView`, раздел стикеров). Сверху ряд
 * значков паков (первый стикер пака) и «+» для импорта; ниже все паки одной сеткой под
 * заголовками. Тап по значку прокручивает к паку, значок пака наверху подсвечен. Ячейка от 72dp,
 * на обычном телефоне 5 в ряд, как в TG.
 */
@Composable
fun TgStickerPanel(
    state: StickerPickerState,
    onStickerClick: (ImagePackImage) -> Unit,
    modifier: Modifier = Modifier,
) {
    ImportDialog(state = state)

    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            state.isEmpty -> Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Стикеров пока нет",
                    color = ElementTheme.colors.textSecondary,
                )
                TextButton(
                    text = "Добавить пак из Telegram",
                    onClick = { state.eventSink(StickerPickerEvents.ShowImport) },
                )
            }
            else -> StickerSections(state = state, onStickerClick = onStickerClick)
        }
    }
}

@Composable
private fun StickerSections(
    state: StickerPickerState,
    onStickerClick: (ImagePackImage) -> Unit,
) {
    val packs = state.packs
    val sectionStarts = remember(packs) {
        var index = 0
        packs.map { pack ->
            val start = index
            index += 1 + pack.stickers.size
            start
        }
    }
    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()
    val currentSection by remember(sectionStarts) {
        derivedStateOf {
            val first = gridState.firstVisibleItemIndex
            sectionStarts.indexOfLast { it <= first }.coerceAtLeast(0)
        }
    }
    val accent = ElementTheme.colors.iconAccentPrimary

    Column(modifier = Modifier.fillMaxSize()) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            contentPadding = PaddingValues(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(packs) { index, pack ->
                val isSelected = index == currentSection
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) accent.copy(alpha = 0.15f) else Color.Transparent)
                        .clickable {
                            coroutineScope.launch { gridState.scrollToItem(sectionStarts[index]) }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    val icon = pack.stickers.firstOrNull()
                    if (icon != null) {
                        StickerImage(sticker = icon, modifier = Modifier.size(30.dp))
                    }
                }
            }
            item {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { state.eventSink(StickerPickerEvents.ShowImport) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        modifier = Modifier.size(24.dp),
                        imageVector = CompoundIcons.Plus(),
                        contentDescription = "Добавить пак",
                        tint = ElementTheme.colors.iconTertiary,
                    )
                }
            }
        }
        LazyVerticalGrid(
            modifier = Modifier.fillMaxSize(),
            state = gridState,
            columns = GridCells.Adaptive(minSize = 72.dp),
            contentPadding = PaddingValues(start = 6.dp, end = 6.dp, bottom = 8.dp),
        ) {
            packs.forEachIndexed { index, pack ->
                item(
                    key = "header_$index",
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    Text(
                        modifier = Modifier.padding(start = 8.dp, top = 10.dp, bottom = 6.dp),
                        text = pack.displayName.orEmpty().ifBlank { "Пак ${index + 1}" },
                        style = ElementTheme.typography.fontBodySmMedium,
                        color = ElementTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                items(
                    count = pack.stickers.size,
                    key = { "${index}_$it" },
                ) { stickerIndex ->
                    val sticker = pack.stickers[stickerIndex]
                    StickerImage(
                        sticker = sticker,
                        modifier = Modifier
                            .aspectRatio(1f)
                            .padding(4.dp)
                            .clickable { onStickerClick(sticker) },
                    )
                }
            }
        }
    }
}

@Composable
private fun StickerImage(
    sticker: ImagePackImage,
    modifier: Modifier = Modifier,
) {
    AsyncImage(
        modifier = modifier,
        model = MediaRequestData(
            source = MediaSource(url = sticker.url),
            kind = MediaRequestData.Kind.Content,
        ),
        contentDescription = sticker.bestDescription,
        contentScale = ContentScale.Fit,
    )
}
