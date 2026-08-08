/*
 * Модуль форка: пикер стикеров.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.stickers.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import io.element.android.libraries.designsystem.theme.components.TextField
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.designsystem.components.dialogs.ListDialog
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.imagepacks.api.ImagePack
import io.element.android.libraries.imagepacks.api.ImagePackImage
import io.element.android.libraries.matrix.api.media.MediaSource
import io.element.android.libraries.matrix.ui.media.MediaRequestData


/**
 * Сетка стикеров с вкладками паков сверху.
 *
 * Сама по себе не знает, где показывается: экраном, шторкой или чем-то ещё.
 */
@Composable
fun StickerPickerView(
    state: StickerPickerState,
    onStickerClick: (ImagePackImage) -> Unit,
    modifier: Modifier = Modifier,
) {
    ImportDialog(state = state)

    Column(modifier = modifier.fillMaxWidth()) {
        when {
            state.isLoading -> LoadingContent()
            state.isEmpty -> EmptyContent(onAddClick = { state.eventSink(StickerPickerEvents.ShowImport) })
            else -> {
                PackTabs(
                    packs = state.packs,
                    selectedIndex = state.selectedPackIndex,
                    onPackClick = { index -> state.eventSink(StickerPickerEvents.SelectPack(index)) },
                    onAddClick = { state.eventSink(StickerPickerEvents.ShowImport) },
                )
                StickerGrid(
                    stickers = state.visibleStickers,
                    onStickerClick = onStickerClick,
                )
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(PICKER_HEIGHT),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyContent(onAddClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(PICKER_HEIGHT),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Стикеров пока нет",
                color = ElementTheme.colors.textSecondary,
            )
            TextButton(text = "Добавить пак из Telegram", onClick = onAddClick)
        }
    }
}

/** Окно добавления пака: ввод имени, ход импорта и результат. */
@Composable
private fun ImportDialog(state: StickerPickerState) {
    val importState = state.importState
    if (importState is ImportState.Hidden) return

    var packName by rememberSaveable { mutableStateOf("") }
    val isFinished = importState is ImportState.Done || importState is ImportState.Error
    val subtitle = when (importState) {
        is ImportState.InProgress -> "Забираем стикеры, это займёт несколько секунд."
        is ImportState.Done -> if (importState.skipped > 0) {
            // Честно говорим про анимированные, иначе человек решит, что пак приехал битым.
            "Готово: «${importState.packName}». Анимированных пропущено: ${importState.skipped}."
        } else {
            "Готово: «${importState.packName}»."
        }
        is ImportState.Error -> importState.message
        else -> "Вставь ссылку вида t.me/addstickers/имя или просто имя пака."
    }

    // ListDialog, а не ConfirmationDialog: у последнего нет слота под содержимое,
    // и поле ввода уезжает в слот иконки, то есть выше заголовка.
    ListDialog(
        title = "Пак из Telegram",
        subtitle = subtitle,
        submitText = if (isFinished) "Понятно" else "Добавить",
        enabled = isFinished || (importState is ImportState.Asking && packName.isNotBlank()),
        onSubmit = {
            if (isFinished) {
                state.eventSink(StickerPickerEvents.DismissImport)
            } else {
                state.eventSink(StickerPickerEvents.ImportPack(packName))
            }
        },
        onDismissRequest = { state.eventSink(StickerPickerEvents.DismissImport) },
    ) {
        if (importState is ImportState.Asking) {
            item {
                TextField(
                    value = packName,
                    onValueChange = { packName = it },
                    placeholder = "имя пака",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun PackTabs(
    packs: List<ImagePack>,
    selectedIndex: Int,
    onPackClick: (Int) -> Unit,
    onAddClick: () -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            // Плюс всегда первым: добавить пак нужно и когда паков ещё нет.
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(ElementTheme.colors.bgSubtleSecondary)
                    .clickable(onClick = onAddClick)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "+", color = ElementTheme.colors.textPrimary)
            }
        }
        items(packs.size) { index ->
            val pack = packs[index]
            val isSelected = index == selectedIndex
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) {
                            ElementTheme.colors.bgActionPrimaryRest
                        } else {
                            ElementTheme.colors.bgSubtleSecondary
                        }
                    )
                    .clickable { onPackClick(index) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = pack.displayName.orEmpty().ifBlank { "Пак ${index + 1}" },
                    color = if (isSelected) {
                        ElementTheme.colors.textOnSolidPrimary
                    } else {
                        ElementTheme.colors.textPrimary
                    },
                )
            }
        }
    }
}

@Composable
private fun StickerGrid(
    stickers: List<ImagePackImage>,
    onStickerClick: (ImagePackImage) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = STICKER_SIZE),
        modifier = Modifier
            .fillMaxWidth()
            .height(PICKER_HEIGHT),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(stickers) { sticker ->
            AsyncImage(
                modifier = Modifier
                    .size(STICKER_SIZE)
                    .clickable { onStickerClick(sticker) },
                model = MediaRequestData(
                    source = MediaSource(url = sticker.url),
                    kind = MediaRequestData.Kind.Content,
                ),
                contentDescription = sticker.bestDescription,
                contentScale = ContentScale.Fit,
            )
        }
    }
}

private val PICKER_HEIGHT = 280.dp
private val STICKER_SIZE = 80.dp
