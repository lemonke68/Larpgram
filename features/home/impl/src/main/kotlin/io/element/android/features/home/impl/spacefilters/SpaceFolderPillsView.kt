/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.spacefilters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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

@Composable
private fun FolderPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier.height(32.dp),
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = ElementTheme.colors.bgCanvasDefault,
            selectedContainerColor = ElementTheme.colors.bgActionPrimaryRest,
            labelColor = ElementTheme.colors.textPrimary,
            selectedLabelColor = ElementTheme.colors.textOnSolidPrimary,
        ),
        label = {
            Text(
                text = label,
                style = ElementTheme.typography.fontBodyMdRegular,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = ElementTheme.colors.borderInteractiveSecondary,
            selectedBorderColor = Color.Transparent,
        ),
    )
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
