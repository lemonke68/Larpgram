/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.spacefilters

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.LarpgramType
import io.element.android.compound.tokens.larpgramColors
import io.element.android.features.home.impl.R
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.api.spaces.SpaceServiceFilter

private const val ALL_KEY = "all"

/**
 * Folder strip of the chat list (redesign V1 final): one horizontal, scrollable row of folders
 * («Все» then the user's spaces) with the selected folder shown as a white / translucent pill
 * that slides inside the same strip. It has no background of its own: it sits on the top row of
 * the glass folder card.
 *
 * @param allUnreadCount unread counter shown on «Все».
 */
@Composable
fun SpaceFolderPillsView(
    state: SpaceFiltersState,
    modifier: Modifier = Modifier,
    allUnreadCount: Int = 0,
) {
    val filters = state.availableFilters()
    val selectedFilter = state.selectedFilter()
    val selectedKey = selectedFilter?.spaceRoom?.roomId?.value ?: ALL_KEY

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

    // Position and width of every folder in the strip, to place the sliding pill.
    val bounds = remember { mutableStateMapOf<String, Pair<Dp, Dp>>() }
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    val selectedBounds = bounds[selectedKey]
    val pillOffset by animateDpAsState(selectedBounds?.first ?: 0.dp, label = "folderPillOffset")
    val pillWidth by animateDpAsState(selectedBounds?.second ?: 0.dp, label = "folderPillWidth")
    KeepSelectedVisible(scrollState, selectedBounds, density.run { 1.dp.toPx() })

    Box(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(4.dp),
    ) {
        if (selectedBounds != null) {
            Box(
                Modifier
                    .offset(x = pillOffset)
                    .width(pillWidth)
                    .height(34.dp)
                    .shadow(4.dp, RoundedCornerShape(17.dp), ambientColor = Color(0x24000000), spotColor = Color(0x24000000))
                    .background(larpgramColors.folderChipSelected, RoundedCornerShape(17.dp))
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            fun Modifier.track(key: String) = onPlaced { coordinates ->
                with(density) {
                    bounds[key] = coordinates.positionInParent().x.toDp() to coordinates.size.width.toDp()
                }
            }
            FolderItem(
                label = stringResource(R.string.screen_roomlist_folder_all),
                selected = selectedKey == ALL_KEY,
                count = allUnreadCount,
                onClick = ::onSelectAll,
                modifier = Modifier.track(ALL_KEY),
            )
            filters.forEach { filter ->
                val key = filter.spaceRoom.roomId.value
                FolderItem(
                    label = filter.spaceRoom.displayName,
                    selected = selectedKey == key,
                    count = 0,
                    onClick = { onSelectSpace(filter) },
                    modifier = Modifier.track(key),
                )
            }
        }
    }
}

@Composable
private fun KeepSelectedVisible(scrollState: ScrollState, selected: Pair<Dp, Dp>?, pxPerDp: Float) {
    LaunchedEffect(selected) {
        val (start, width) = selected ?: return@LaunchedEffect
        val left = start.value * pxPerDp
        val right = left + width.value * pxPerDp
        val viewport = scrollState.viewportSize
        if (viewport == 0) return@LaunchedEffect
        when {
            left < scrollState.value -> scrollState.animateScrollTo(left.toInt())
            right > scrollState.value + viewport -> scrollState.animateScrollTo((right - viewport).toInt())
        }
    }
}

@Composable
private fun FolderItem(
    label: String,
    selected: Boolean,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = larpgramColors
    Row(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = LarpgramType.folderChip,
            color = if (selected) ElementTheme.colors.textPrimary else ElementTheme.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (count > 0) {
            Text(
                text = if (count > 999) "999+" else count.toString(),
                style = LarpgramType.badge.copy(fontSize = LarpgramType.badge.fontSize * (11f / 12f)),
                color = if (selected) Color.White else colors.accentText,
                modifier = Modifier
                    .background(if (selected) colors.accent else colors.accentSoft, RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
    }
}

@PreviewsDayNight
@Composable
internal fun SpaceFolderPillsViewPreview(
    @PreviewParameter(SpaceFolderPillsSpaceFiltersStatePreviewParam::class) state: SpaceFiltersState,
) = ElementPreview {
    SpaceFolderPillsView(state = state, allUnreadCount = 3)
}

internal class SpaceFolderPillsSpaceFiltersStatePreviewParam : androidx.compose.ui.tooling.preview.PreviewParameterProvider<SpaceFiltersState> {
    override val values: Sequence<SpaceFiltersState>
        get() = sequenceOf(
            anUnselectedSpaceFiltersState(),
            aSelectedSpaceFiltersState(),
        )
}
