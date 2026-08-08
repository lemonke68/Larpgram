/*
 * Модуль форка: гифки через свой прокси к Tenor.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.gifs.impl

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.designsystem.theme.components.TextField

/** Поиск гифок и сетка результатов. */
@Composable
fun GifPickerView(
    state: GifPickerState,
    onGifClick: (Gif) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        TextField(
            value = state.query,
            onValueChange = { state.eventSink(GifPickerEvents.QueryChanged(it)) },
            placeholder = "Поиск гифок",
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        when {
            state.isLoading -> CenteredBox { CircularProgressIndicator() }
            state.hasFailed -> CenteredBox {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Не получилось загрузить гифки",
                        color = ElementTheme.colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(
                        text = "Повторить",
                        onClick = { state.eventSink(GifPickerEvents.Retry) },
                    )
                }
            }
            state.isEmpty -> CenteredBox {
                Text(
                    text = if (state.query.isBlank()) "Тут появятся отправленные гифки" else "Ничего не нашлось",
                    color = ElementTheme.colors.textSecondary,
                )
            }
            else -> {
                if (state.isShowingRecent) {
                    Text(
                        text = "Недавние",
                        color = ElementTheme.colors.textSecondary,
                        modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
                    )
                }
                GifGrid(state = state, onGifClick = onGifClick)
            }
        }
    }
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(PICKER_HEIGHT),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun GifGrid(
    state: GifPickerState,
    onGifClick: (Gif) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        modifier = Modifier
            .fillMaxWidth()
            .height(PICKER_HEIGHT),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.gifs) { gif ->
            AsyncImage(
                // В сетке показываем уменьшенную копию: полная весит мегабайты, и на
                // мобильном интернете сетка из них грузилась бы вечность.
                model = gif.previewUrl,
                contentDescription = gif.description,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .height(GIF_CELL_HEIGHT)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onGifClick(gif) },
            )
        }
    }
}

private const val GRID_COLUMNS = 2
private val PICKER_HEIGHT = 320.dp
private val GIF_CELL_HEIGHT = 110.dp
