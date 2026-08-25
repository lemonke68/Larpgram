/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.element.android.appconfig.RoomListConfig
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.home.impl.HomeNavigationBarItem
import io.element.android.features.home.impl.R
import io.element.android.features.home.impl.filters.RoomListFiltersState
import io.element.android.features.home.impl.filters.aRoomListFiltersState
import io.element.android.features.home.impl.spacefilters.SpaceFiltersState
import io.element.android.features.home.impl.spacefilters.SpaceFolderPillsView
import io.element.android.features.home.impl.spacefilters.aSelectedSpaceFiltersState
import io.element.android.features.home.impl.spacefilters.anUnselectedSpaceFiltersState
import io.element.android.features.home.impl.spacefilters.availableFilters
import io.element.android.libraries.designsystem.atomic.atoms.RedIndicatorAtom
import io.element.android.libraries.designsystem.components.TopAppBarScrollBehaviorLayout
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.modifiers.backgroundVerticalGradient
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.preview.USER_NAME_ALICE
import io.element.android.libraries.designsystem.theme.aliasScreenTitle
import io.element.android.libraries.designsystem.theme.components.DropdownMenu
import io.element.android.libraries.designsystem.theme.components.DropdownMenuItem
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.matrix.ui.components.aMatrixUser
import io.element.android.libraries.matrix.ui.components.aMatrixUserList
import io.element.android.libraries.matrix.ui.model.getAvatarData
import io.element.android.libraries.testtags.TestTags
import io.element.android.libraries.testtags.testTag
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTopBar(
    selectedNavigationItem: HomeNavigationBarItem,
    currentUserAndNeighbors: ImmutableList<MatrixUser>,
    showAvatarIndicator: Boolean,
    areSearchResultsDisplayed: Boolean,
    onToggleSearch: () -> Unit,
    onMenuActionClick: (RoomListMenuAction) -> Unit,
    onOpenSettings: () -> Unit,
    onAccountSwitch: (SessionId) -> Unit,
    onCreateChat: () -> Unit,
    onCreateChannel: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    canReportBug: Boolean,
    displayFilters: Boolean,
    filtersState: RoomListFiltersState,
    spaceFiltersState: SpaceFiltersState,
    modifier: Modifier = Modifier,
) {
    val contentPadding = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).asPaddingValues()
    Column(modifier) {
        TopAppBar(
            modifier = Modifier
                .backgroundVerticalGradient(
                    isVisible = !areSearchResultsDisplayed,
                )
                .statusBarsPadding()
                .padding(contentPadding),
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
            title = {
                // This top bar only renders on the Chats tab; Settings/Profile host their own.
                val displayTitle = when (spaceFiltersState) {
                    is SpaceFiltersState.Selected -> spaceFiltersState.selectedFilter.spaceRoom.displayName
                    else -> stringResource(HomeNavigationBarItem.Chats.labelRes)
                }
                Text(
                    modifier = Modifier.semantics {
                        heading()
                    },
                    style = ElementTheme.typography.aliasScreenTitle,
                    text = displayTitle,
                )
            },
            // Avatar removed from the top-left: the Profile tab covers it now (user request).
            // The title left-aligns to the default top-app-bar inset.
            actions = {
                if (selectedNavigationItem == HomeNavigationBarItem.Chats) {
                    RoomListMenuItems(
                        onToggleSearch = onToggleSearch,
                        onMenuActionClick = onMenuActionClick,
                        canReportBug = canReportBug,
                        onCreateChat = onCreateChat,
                        onCreateChannel = onCreateChannel,
                    )
                }
            },
            windowInsets = WindowInsets(left = 16.dp),
        )
        // Telegram-style folder row: only the user's folders (Matrix spaces). Like Telegram,
        // it appears once there is at least one folder; a fresh account has just "All chats"
        // and no strip. Element's own quick filters (Unread/People/Rooms) are intentionally
        // dropped here — folders replace them.
        val showFolderPills = selectedNavigationItem == HomeNavigationBarItem.Chats &&
            spaceFiltersState.availableFilters().isNotEmpty()
        if (showFolderPills) {
            TopAppBarScrollBehaviorLayout(scrollBehavior = scrollBehavior) {
                SpaceFolderPillsView(
                    state = spaceFiltersState,
                    modifier = Modifier.padding(bottom = 16.dp).padding(contentPadding)
                )
            }
        }
    }
}

@Composable
private fun RowScope.RoomListMenuItems(
    onToggleSearch: () -> Unit,
    onMenuActionClick: (RoomListMenuAction) -> Unit,
    canReportBug: Boolean,
    onCreateChat: () -> Unit,
    onCreateChannel: () -> Unit,
) {
    IconButton(
        onClick = onToggleSearch,
    ) {
        Icon(
            imageVector = CompoundIcons.Search(),
            contentDescription = stringResource(CommonStrings.action_search),
        )
    }
    // "New" menu (Telegram-style compose action): create a chat/group or a channel.
    var showCreateMenu by remember { mutableStateOf(false) }
    IconButton(onClick = { showCreateMenu = true }) {
        Icon(
            imageVector = CompoundIcons.Compose(),
            contentDescription = stringResource(R.string.screen_home_new_chat),
        )
    }
    DropdownMenu(
        expanded = showCreateMenu,
        onDismissRequest = { showCreateMenu = false },
    ) {
        DropdownMenuItem(
            onClick = {
                showCreateMenu = false
                onCreateChat()
            },
            text = { Text(stringResource(R.string.screen_home_new_chat)) },
            leadingIcon = {
                Icon(
                    imageVector = CompoundIcons.Compose(),
                    tint = ElementTheme.colors.iconSecondary,
                    contentDescription = null,
                )
            },
        )
        DropdownMenuItem(
            onClick = {
                showCreateMenu = false
                onCreateChannel()
            },
            text = { Text(stringResource(R.string.screen_home_new_channel)) },
            leadingIcon = {
                Icon(
                    // Placeholder channel icon; refined in the design pass.
                    imageVector = CompoundIcons.Public(),
                    tint = ElementTheme.colors.iconSecondary,
                    contentDescription = null,
                )
            },
        )
    }
    if (RoomListConfig.HAS_DROP_DOWN_MENU) {
        var showMenu by remember { mutableStateOf(false) }
        IconButton(
            onClick = { showMenu = !showMenu }
        ) {
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

@Composable
private fun NavigationIcon(
    currentUserAndNeighbors: ImmutableList<MatrixUser>,
    showAvatarIndicator: Boolean,
    onAccountSwitch: (SessionId) -> Unit,
    onClick: () -> Unit,
) {
    if (currentUserAndNeighbors.size == 1) {
        AccountIcon(
            matrixUser = currentUserAndNeighbors.single(),
            isCurrentAccount = true,
            showAvatarIndicator = showAvatarIndicator,
            onClick = onClick,
        )
    } else {
        // Render a vertical pager
        val pagerState = rememberPagerState(initialPage = 1) { currentUserAndNeighbors.size }
        // Listen to page changes and switch account if needed
        val latestOnAccountSwitch by rememberUpdatedState(onAccountSwitch)
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.settledPage }.collect { page ->
                latestOnAccountSwitch(SessionId(currentUserAndNeighbors[page].userId.value))
            }
        }
        VerticalPager(
            state = pagerState,
            modifier = Modifier.height(48.dp),
        ) { page ->
            AccountIcon(
                matrixUser = currentUserAndNeighbors[page],
                isCurrentAccount = page == 1,
                showAvatarIndicator = page == 1 && showAvatarIndicator,
                onClick = if (page == 1) {
                    onClick
                } else {
                    {}
                },
            )
        }
    }
}

@Composable
private fun AccountIcon(
    matrixUser: MatrixUser,
    isCurrentAccount: Boolean,
    showAvatarIndicator: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val testTag = if (isCurrentAccount) Modifier.testTag(TestTags.homeScreenSettings) else Modifier
    IconButton(
        modifier = modifier.then(testTag),
        onClick = onClick,
    ) {
        Box {
            val avatarData by remember(matrixUser) {
                derivedStateOf {
                    matrixUser.getAvatarData(size = AvatarSize.CurrentUserTopBar)
                }
            }
            Avatar(
                avatarData = avatarData,
                avatarType = AvatarType.User,
                contentDescription = if (isCurrentAccount) {
                    if (showAvatarIndicator) {
                        stringResource(CommonStrings.a11y_settings_with_required_action)
                    } else {
                        stringResource(CommonStrings.common_settings)
                    }
                } else {
                    null
                },
            )
            if (showAvatarIndicator) {
                RedIndicatorAtom(
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewsDayNight
@Composable
internal fun HomeTopBarPreview() = ElementPreview {
    HomeTopBar(
        selectedNavigationItem = HomeNavigationBarItem.Chats,
        currentUserAndNeighbors = persistentListOf(aMatrixUser(id = "@id:domain", displayName = USER_NAME_ALICE)),
        showAvatarIndicator = false,
        areSearchResultsDisplayed = false,
        scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState()),
        onOpenSettings = {},
        onAccountSwitch = {},
        onToggleSearch = {},
        onCreateChat = {},
        onCreateChannel = {},
        canReportBug = true,
        displayFilters = true,
        filtersState = aRoomListFiltersState(),
        spaceFiltersState = anUnselectedSpaceFiltersState(),
        onMenuActionClick = {},
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewsDayNight
@Composable
internal fun HomeTopBarSpaceFiltersSelectedPreview() = ElementPreview {
    HomeTopBar(
        selectedNavigationItem = HomeNavigationBarItem.Chats,
        currentUserAndNeighbors = persistentListOf(aMatrixUser(id = "@id:domain", displayName = USER_NAME_ALICE)),
        showAvatarIndicator = false,
        areSearchResultsDisplayed = false,
        scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState()),
        onOpenSettings = {},
        onAccountSwitch = {},
        onToggleSearch = {},
        onCreateChat = {},
        onCreateChannel = {},
        canReportBug = true,
        displayFilters = true,
        filtersState = aRoomListFiltersState(),
        spaceFiltersState = aSelectedSpaceFiltersState(),
        onMenuActionClick = {},
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewsDayNight
@Composable
internal fun HomeTopBarWithIndicatorPreview() = ElementPreview {
    HomeTopBar(
        selectedNavigationItem = HomeNavigationBarItem.Chats,
        currentUserAndNeighbors = persistentListOf(aMatrixUser(id = "@id:domain", displayName = USER_NAME_ALICE)),
        showAvatarIndicator = true,
        areSearchResultsDisplayed = false,
        scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState()),
        onOpenSettings = {},
        onAccountSwitch = {},
        onToggleSearch = {},
        onCreateChat = {},
        onCreateChannel = {},
        canReportBug = true,
        displayFilters = true,
        filtersState = aRoomListFiltersState(),
        spaceFiltersState = anUnselectedSpaceFiltersState(),
        onMenuActionClick = {},
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewsDayNight
@Composable
internal fun HomeTopBarMultiAccountPreview() = ElementPreview {
    HomeTopBar(
        selectedNavigationItem = HomeNavigationBarItem.Chats,
        currentUserAndNeighbors = aMatrixUserList().take(3).toImmutableList(),
        showAvatarIndicator = false,
        areSearchResultsDisplayed = false,
        scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState()),
        onOpenSettings = {},
        onAccountSwitch = {},
        onToggleSearch = {},
        onCreateChat = {},
        onCreateChannel = {},
        canReportBug = true,
        displayFilters = true,
        filtersState = aRoomListFiltersState(),
        spaceFiltersState = anUnselectedSpaceFiltersState(),
        onMenuActionClick = {},
    )
}
