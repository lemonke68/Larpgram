/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.spacefilters

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.home.impl.R
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.api.spaces.SpaceServiceFilter

/**
 * Telegram-style folder row shown under the home title. The first pill ("All") clears the
 * space filter; the following pills are the user's spaces, acting as chat folders. Renders
 * nothing when spaces are disabled or the picker sheet is open.
 */
@Composable
fun SpaceFolderPillsView(
    state: SpaceFiltersState,
    modifier: Modifier = Modifier,
) {
    val filters = state.availableFilters()
    if (filters.isEmpty()) return

    val selectedFilter = state.selectedFilter()

    fun onSelectAll() {
        when (state) {
            is SpaceFiltersState.Selected -> state.eventSink(SpaceFiltersEvent.Selected.ClearSelection)
            else -> Unit
        }
    }

    fun onSelectSpace(filter: SpaceServiceFilter) {
        when (state) {
            is SpaceFiltersState.Unselected -> state.eventSink(SpaceFiltersEvent.Unselected.SelectFilter(filter))
            is SpaceFiltersState.Selected -> state.eventSink(SpaceFiltersEvent.Selected.SelectFilter(filter))
            else -> Unit
        }
    }

    // «Большой бабл»: один закруглённый полупрозрачный (80%) контейнер, внутри — лента папок-пилюль.
    // Активная папка = светлая пилюля (bgSubtleSecondary) на фоне более тёмного полупрозрачного
    // контейнера (bgCanvasDefault @ 0.8), как сегмент-контрол в Telegram.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(CircleShape)
            .background(ElementTheme.colors.bgCanvasDefault.copy(alpha = 0.8f)),
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item("all") {
                FolderPill(
                    label = stringResource(R.string.screen_roomlist_folder_all),
                    selected = selectedFilter == null,
                    onClick = ::onSelectAll,
                )
            }
            items(
                count = filters.size,
                key = { index -> filters[index].spaceRoom.roomId.value },
            ) { index ->
                val filter = filters[index]
                FolderPill(
                    label = filter.spaceRoom.displayName,
                    selected = selectedFilter?.spaceRoom?.roomId == filter.spaceRoom.roomId,
                    onClick = { onSelectSpace(filter) },
                )
            }
        }
    }
}

// TG-стиль вкладок-папок: выбранная — субтл-серая пилюля (bgSubtleSecondary), невыбранные —
// просто текст без заливки и рамки. Без акцент-фиолета и без белой заливки (см. фидбек юзера).
@Composable
private fun FolderPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(CircleShape)
            .background(if (selected) ElementTheme.colors.bgSubtleSecondary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = ElementTheme.typography.fontBodyMdMedium,
            color = if (selected) ElementTheme.colors.textPrimary else ElementTheme.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@PreviewsDayNight
@Composable
internal fun SpaceFolderPillsViewPreview(
    @PreviewParameter(SpaceFolderPillsStateProvider::class) state: SpaceFiltersState,
) = ElementPreview {
    SpaceFolderPillsView(state = state)
}

internal class SpaceFolderPillsStateProvider : androidx.compose.ui.tooling.preview.PreviewParameterProvider<SpaceFiltersState> {
    override val values: Sequence<SpaceFiltersState>
        get() = sequenceOf(
            anUnselectedSpaceFiltersState(),
            aSelectedSpaceFiltersState(),
        )
}
