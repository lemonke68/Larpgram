/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 * Правка форка: шапка списка чатов Telegram с папками-пилюлями. Заменяет элементовский `HomeTopBar`, он оставлен как в апстриме (аудит C-009).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.element.android.appconfig.RoomListConfig
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.LarpgramType
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.home.impl.R
import io.element.android.features.home.impl.spacefilters.SpaceFiltersState
import io.element.android.features.home.impl.spacefilters.SpaceFolderPagerState
import io.element.android.features.home.impl.spacefilters.SpaceFolderPillsView
import io.element.android.features.home.impl.spacefilters.aSelectedSpaceFiltersState
import io.element.android.features.home.impl.spacefilters.anUnselectedSpaceFiltersState
import io.element.android.libraries.designsystem.components.glass.TgGlassTint
import io.element.android.libraries.designsystem.components.glass.tgGlass
import io.element.android.libraries.designsystem.components.glass.tgTopFade
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.DropdownMenu
import io.element.android.libraries.designsystem.theme.components.DropdownMenuItem
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.ui.strings.CommonStrings

/**
 * Header of the chat list (redesign V1 final), floating over the list: the `larpgram` wordmark with
 * compose (new chat) and the overflow menu on a blurred fade, and under it the glass folder card.
 * The card's top row is the folder strip; system notices ([notices]) are glued under it inside the
 * same card. Search moved to the bottom bar, compose moved here from the old floating button.
 */
@Composable
fun TgHomeTopBar(
    spaceFiltersState: SpaceFiltersState,
    allUnreadCount: Int,
    onStartChatClick: () -> Unit,
    onMenuActionClick: (RoomListMenuAction) -> Unit,
    canReportBug: Boolean,
    modifier: Modifier = Modifier,
    pagerState: SpaceFolderPagerState? = null,
    notices: @Composable ColumnScope.() -> Unit = {},
) {
    val horizontalInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).asPaddingValues()
    Box(modifier) {
        // Blurred fade behind the status bar, the wordmark row and the upper half of the card.
        Box(
            Modifier
                .matchParentSize()
                .padding(bottom = 36.dp)
                .tgTopFade()
        )
        Column(Modifier.padding(horizontalInsets)) {
            Row(
                modifier = Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .height(54.dp)
                    .padding(start = 18.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val title = when (spaceFiltersState) {
                    is SpaceFiltersState.Selected -> spaceFiltersState.selectedFilter.spaceRoom.displayName
                    else -> null
                }
                Text(
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                    // Правка форка: словесный знак `larpgram` (Unbounded); в папке — её имя.
                    text = title ?: "larpgram",
                    style = if (title == null) LarpgramType.wordmark else LarpgramType.screenTitle,
                    color = ElementTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = onStartChatClick) {
                    Icon(
                        modifier = Modifier.size(23.dp),
                        imageVector = CompoundIcons.Edit(),
                        contentDescription = stringResource(R.string.screen_home_new_chat),
                    )
                }
                OverflowMenu(onMenuActionClick = onMenuActionClick, canReportBug = canReportBug)
            }
            Column(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth()
                    .tgGlass(RoundedCornerShape(22.dp), tint = TgGlassTint.Surface, elevation = 10.dp),
            ) {
                SpaceFolderPillsView(state = spaceFiltersState, allUnreadCount = allUnreadCount, pagerState = pagerState)
                notices()
            }
        }
    }
}

@Composable
private fun OverflowMenu(
    onMenuActionClick: (RoomListMenuAction) -> Unit,
    canReportBug: Boolean,
) {
    if (!RoomListConfig.HAS_DROP_DOWN_MENU) return
    var showMenu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { showMenu = !showMenu }) {
            Icon(
                imageVector = CompoundIcons.OverflowVertical(),
                contentDescription = null,
            )
        }
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            if (RoomListConfig.SHOW_INVITE_MENU_ITEM) {
                DropdownMenuItem(
                    onClick = {
                        showMenu = false
                        onMenuActionClick(RoomListMenuAction.InviteFriends)
                    },
                    text = { Text(stringResource(id = CommonStrings.action_invite)) },
                    leadingIcon = {
                        Icon(
                            imageVector = CompoundIcons.ShareAndroid(),
                            tint = ElementTheme.colors.iconSecondary,
                            contentDescription = null,
                        )
                    }
                )
            }
            if (RoomListConfig.SHOW_REPORT_PROBLEM_MENU_ITEM && canReportBug) {
                DropdownMenuItem(
                    onClick = {
                        showMenu = false
                        onMenuActionClick(RoomListMenuAction.ReportBug)
                    },
                    text = { Text(stringResource(id = CommonStrings.common_report_a_problem)) },
                    leadingIcon = {
                        Icon(
                            imageVector = CompoundIcons.ChatProblem(),
                            tint = ElementTheme.colors.iconSecondary,
                            contentDescription = null,
                        )
                    }
                )
            }
        }
    }
}

@PreviewsDayNight
@Composable
internal fun TgHomeTopBarPreview() = ElementPreview {
    TgHomeTopBar(
        spaceFiltersState = anUnselectedSpaceFiltersState(),
        allUnreadCount = 3,
        onStartChatClick = {},
        onMenuActionClick = {},
        canReportBug = true,
    )
}

@PreviewsDayNight
@Composable
internal fun TgHomeTopBarSpaceFiltersSelectedPreview() = ElementPreview {
    TgHomeTopBar(
        spaceFiltersState = aSelectedSpaceFiltersState(),
        allUnreadCount = 0,
        onStartChatClick = {},
        onMenuActionClick = {},
        canReportBug = true,
    )
}
