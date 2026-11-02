/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.emoji.impl.picker

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.zacsweers.metro.ContributesBinding
import io.element.android.compound.theme.ElementTheme
import io.element.android.emojibasebindings.Emoji
import io.element.android.libraries.designsystem.components.tg.TgSearchField
import io.element.android.libraries.designsystem.text.toSp
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.SearchBarResultState
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.emoji.api.picker.EmojiKeyboardRenderer
import io.element.android.libraries.emoji.api.picker.EmojiPickerState
import io.element.android.libraries.emoji.impl.R
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.launch

@ContributesBinding(SessionScope::class)
class DefaultEmojiKeyboardRenderer : EmojiKeyboardRenderer {
    @Composable
    override fun Render(
        state: EmojiPickerState,
        onSelectEmoji: (Emoji) -> Unit,
        onSearchFocusChange: (Boolean) -> Unit,
        modifier: Modifier,
    ) {
        if (state is DefaultEmojiPickerState) {
            TgEmojiKeyboard(state = state, onSelectEmoji = onSelectEmoji, onSearchFocusChange = onSearchFocusChange, modifier = modifier)
        }
    }
}

/**
 * Клавиатура эмодзи Telegram (`EmojiView`): значки категорий сверху, ниже одна сетка со всеми
 * категориями подряд под заголовками. Тап по значку прокручивает к разделу, значок раздела,
 * который сейчас наверху, подсвечен. Ячейка 44dp, эмодзи 32dp — как в TG. Долгое нажатие
 * открывает выбор оттенка кожи (апстримовский `EmojiItem`).
 */
@Composable
internal fun TgEmojiKeyboard(
    state: DefaultEmojiPickerState,
    onSelectEmoji: (Emoji) -> Unit,
    onSearchFocusChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val categories = state.categories
    // Индекс первого элемента каждого раздела в сетке: заголовок + эмодзи.
    val sectionStarts = remember(categories) {
        var index = 0
        categories.map { category ->
            val start = index
            index += 1 + category.emojis.size
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
    var skinPickerEmoji by remember { mutableStateOf<Emoji?>(null) }
    val accent = ElementTheme.colors.iconAccentPrimary

    val searchFocusCallback by rememberUpdatedState(onSearchFocusChange)
    DisposableEffect(Unit) {
        onDispose { searchFocusCallback(false) }
    }
    val searching = state.searchQuery.text.isNotBlank()
    Column(modifier) {
        TgSearchField(
            value = state.searchQuery.text.toString(),
            onValueChange = { value -> state.searchQuery.edit { replace(0, length, value) } },
            placeholder = stringResource(R.string.emoji_picker_search_placeholder),
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .onFocusChanged { searchFocusCallback(it.isFocused) },
        )
        if (!searching) {
            LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            contentPadding = PaddingValues(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(categories) { index, category ->
                val isSelected = index == currentSection
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) accent.copy(alpha = 0.15f) else Color.Transparent)
                        .clickable {
                            coroutineScope.launch { gridState.scrollToItem(sectionStarts[index]) }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    val tint = if (isSelected) accent else ElementTheme.colors.iconTertiary
                    val description = stringResource(id = category.titleId)
                    when (category.icon) {
                        is IconSource.Resource -> Icon(
                            modifier = Modifier.size(22.dp),
                            resourceId = category.icon.id,
                            contentDescription = description,
                            tint = tint,
                        )
                        is IconSource.Vector -> Icon(
                            modifier = Modifier.size(22.dp),
                            imageVector = category.icon.vector,
                            contentDescription = description,
                            tint = tint,
                        )
                    }
                }
            }
        }
        }
        LazyVerticalGrid(
            modifier = Modifier.fillMaxSize(),
            state = gridState,
            columns = GridCells.Adaptive(minSize = 44.dp),
            contentPadding = PaddingValues(start = 6.dp, end = 6.dp, bottom = 8.dp),
        ) {
            if (searching) {
                val resultState = state.searchResults
                val results = (resultState as? SearchBarResultState.Results)?.results.orEmpty()
                if (resultState is SearchBarResultState.Results && results.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = stringResource(CommonStrings.common_no_results),
                            color = ElementTheme.colors.textSecondary,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                items(count = results.size, key = { results[it].unicode }) { index ->
                    val item = results[index]
                    EmojiItem(
                        modifier = Modifier.aspectRatio(1f),
                        item = item,
                        isSelected = false,
                        onSelectEmoji = onSelectEmoji,
                        onLongPress = { skinPickerEmoji = it },
                        skinPickerEmoji = skinPickerEmoji,
                        onDismissSkinPicker = { skinPickerEmoji = null },
                        emojiSize = 30.dp.toSp(),
                        selectedSkinUnicodes = persistentSetOf(),
                        hasSelectedSkin = false,
                    )
                }
            } else {
                categories.forEachIndexed { index, category ->
                item(
                    key = "header_$index",
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    Text(
                        modifier = Modifier.padding(start = 8.dp, top = 10.dp, bottom = 6.dp),
                        text = stringResource(id = category.titleId),
                        style = ElementTheme.typography.fontBodySmMedium,
                        color = ElementTheme.colors.textSecondary,
                    )
                }
                items(
                    count = category.emojis.size,
                    key = { "${index}_${category.emojis[it].unicode}" },
                ) { emojiIndex ->
                    val item = category.emojis[emojiIndex]
                    EmojiItem(
                        modifier = Modifier.aspectRatio(1f),
                        item = item,
                        isSelected = false,
                        onSelectEmoji = onSelectEmoji,
                        onLongPress = { skinPickerEmoji = it },
                        skinPickerEmoji = skinPickerEmoji,
                        onDismissSkinPicker = { skinPickerEmoji = null },
                        emojiSize = 30.dp.toSp(),
                        selectedSkinUnicodes = persistentSetOf(),
                        hasSelectedSkin = false,
                    )
                }
            }
            }
        }
    }
}
