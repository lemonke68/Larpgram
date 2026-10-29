/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 * Правка форка: главный экран Larpgram (редизайн V1 final): вкладки «Чаты» и «Профиль» с отдельной кнопкой поиска, стеклянная шапка с папками, переключатель аккаунтов по долгому нажатию на «Профиль». Заменяет элементовский `HomeView`, он оставлен как в апстриме (аудит C-009).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.home.impl.components.RoomListContentView
import io.element.android.features.home.impl.components.RoomListMenuAction
import io.element.android.features.home.impl.components.RoomListNotices
import io.element.android.features.home.impl.components.TgAccountSwitcher
import io.element.android.features.home.impl.components.TgHomeBarDefaults
import io.element.android.features.home.impl.components.TgHomeTabBar
import io.element.android.features.home.impl.components.TgHomeTopBar
import io.element.android.features.home.impl.model.RoomListRoomSummary
import io.element.android.features.home.impl.roomlist.RoomListContentState
import io.element.android.features.home.impl.roomlist.RoomListContextMenu
import io.element.android.features.home.impl.roomlist.RoomListDeclineInviteMenu
import io.element.android.features.home.impl.roomlist.RoomListEvent
import io.element.android.features.home.impl.roomlist.RoomListState
import io.element.android.features.home.impl.roomlist.TgChatPreviewOverlay
import io.element.android.features.home.impl.search.GlobalSearchEvent
import io.element.android.features.home.impl.search.GlobalSearchView
import io.element.android.features.home.impl.search.RoomListSearchView
import io.element.android.features.home.impl.spacefilters.SpaceFiltersEvent
import io.element.android.features.home.impl.spacefilters.SpaceFiltersState
import io.element.android.features.home.impl.spacefilters.SpaceFiltersView
import io.element.android.features.home.impl.spacefilters.SpaceFolderSwipe
import io.element.android.features.home.impl.spacefilters.availableFilters
import io.element.android.features.home.impl.spacefilters.rememberSpaceFolderPagerState
import io.element.android.libraries.androidutils.throttler.FirstThrottler
import io.element.android.libraries.designsystem.animation.TgMotion
import io.element.android.libraries.designsystem.components.glass.LocalChatGlassState
import io.element.android.libraries.designsystem.components.glass.tgGlowBackground
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarHost
import io.element.android.libraries.designsystem.utils.snackbar.rememberSnackbarHostState
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch

@Composable
fun TgHomeView(
    homeState: HomeState,
    onRoomClick: (RoomId, EventId?) -> Unit,
    // Правка форка (Larpgram): Element v26.10 переключает аккаунт навигацией (HomeEvent.SwitchToAccount удалён).
    onSwitchToAccount: (SessionId) -> Unit,
    onSetUpRecoveryClick: () -> Unit,
    onConfirmRecoveryKeyClick: () -> Unit,
    onStartChatClick: () -> Unit,
    onRoomSettingsClick: (roomId: RoomId) -> Unit,
    onMenuActionClick: (RoomListMenuAction) -> Unit,
    onReportRoomClick: (roomId: RoomId) -> Unit,
    onDeclineInviteAndBlockUser: (roomSummary: RoomListRoomSummary) -> Unit,
    acceptDeclineInviteView: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    leaveRoomView: @Composable () -> Unit,
    // Accounts for the long-press switcher on «Профиль».
    accounts: ImmutableList<TgAccount> = persistentListOf(),
    onAddAccountClick: () -> Unit = {},
    // false когда вкладка профиля ушла во вложенный экран настроек: тогда прячем нижний бар.
    profileTabAtRoot: Boolean = true,
    // Content of the Profile tab (profile + settings), hosted as a child node by HomeFlowNode.
    // Null in previews, where a placeholder is shown instead.
    profileContent: (@Composable () -> Unit)? = null,
    initialTab: TgHomeTab = TgHomeTab.Chats,
    // Лента чата для превью по долгому нажатию. Null — показывается шторка действий.
    chatPreview: (@Composable (roomId: RoomId, onClick: () -> Unit, modifier: Modifier) -> Unit)? = null,
    // Правка форка (Larpgram): мини-плеер музыки под шапкой списка чатов, как в Telegram.
    musicMiniPlayer: (@Composable (Modifier) -> Unit)? = null,
) {
    val state: RoomListState = homeState.roomListState
    val coroutineScope = rememberCoroutineScope()
    val firstThrottler = remember { FirstThrottler(300, coroutineScope) }
    Box(modifier) {
        // Без рендерера превью (превью-композиции, тесты) — прежняя шторка действий.
        if (state.contextMenu is RoomListState.ContextMenu.Shown && chatPreview == null) {
            RoomListContextMenu(
                contextMenu = state.contextMenu,
                canReportRoom = state.canReportRoom,
                eventSink = state.eventSink,
                onRoomSettingsClick = onRoomSettingsClick,
                onReportRoomClick = onReportRoomClick,
            )
        }
        if (state.declineInviteMenu is RoomListState.DeclineInviteMenu.Shown) {
            RoomListDeclineInviteMenu(
                menu = state.declineInviteMenu,
                canReportRoom = state.canReportRoom,
                eventSink = state.eventSink,
                onDeclineAndBlockClick = onDeclineInviteAndBlockUser,
            )
        }

        leaveRoomView()

        HomeScaffold(
            state = homeState,
            onSetUpRecoveryClick = onSetUpRecoveryClick,
            onConfirmRecoveryKeyClick = onConfirmRecoveryKeyClick,
            onRoomClick = { roomId -> if (firstThrottler.canHandle()) onRoomClick(roomId, null) },
            onSwitchToAccount = onSwitchToAccount,
            onAddAccountClick = onAddAccountClick,
            onStartChatClick = { if (firstThrottler.canHandle()) onStartChatClick() },
            onMenuActionClick = onMenuActionClick,
            accounts = accounts,
            profileTabAtRoot = profileTabAtRoot,
            profileContent = profileContent,
            initialTab = initialTab,
            musicMiniPlayer = musicMiniPlayer,
        )

        if (state.globalSearchState.isEnabled) {
            GlobalSearchView(
                state = state.globalSearchState,
                onSelectSearchResult = { roomId, eventId -> if (firstThrottler.canHandle()) onRoomClick(roomId, eventId) },
                modifier = Modifier
                    .fillMaxSize()
                    .background(ElementTheme.colors.bgCanvasDefault),
            )
        } else {
            // This overlaid view will only be visible when state.displaySearchResults is true
            RoomListSearchView(
                state = state.searchState,
                eventSink = state.eventSink,
                hideInvitesAvatars = state.hideInvitesAvatars,
                onRoomClick = { roomId -> if (firstThrottler.canHandle()) onRoomClick(roomId, null) },
                modifier = Modifier
                    .fillMaxSize()
                    .background(ElementTheme.colors.bgCanvasDefault)
            )
        }

        acceptDeclineInviteView()

        // Правка форка: превью чата по долгому нажатию, как в Telegram. Верхним слоем на весь экран.
        val contextMenu = state.contextMenu
        if (contextMenu is RoomListState.ContextMenu.Shown && chatPreview != null) {
            TgChatPreviewOverlay(
                contextMenu = contextMenu,
                canReportRoom = state.canReportRoom,
                eventSink = state.eventSink,
                onRoomSettingsClick = onRoomSettingsClick,
                onReportRoomClick = onReportRoomClick,
                onOpenChat = { roomId -> onRoomClick(roomId, null) },
                chatPreview = chatPreview,
            )
        }
    }
}

@Composable
private fun HomeScaffold(
    state: HomeState,
    onSetUpRecoveryClick: () -> Unit,
    onConfirmRecoveryKeyClick: () -> Unit,
    onRoomClick: (RoomId) -> Unit,
    onSwitchToAccount: (SessionId) -> Unit,
    onAddAccountClick: () -> Unit,
    onStartChatClick: () -> Unit,
    onMenuActionClick: (RoomListMenuAction) -> Unit,
    accounts: ImmutableList<TgAccount>,
    modifier: Modifier = Modifier,
    profileTabAtRoot: Boolean = true,
    profileContent: (@Composable () -> Unit)? = null,
    initialTab: TgHomeTab = TgHomeTab.Chats,
    musicMiniPlayer: (@Composable (Modifier) -> Unit)? = null,
) {
    // The selected tab lives here: Element's HomePresenter only knows its own Chats/Spaces items.
    var currentItem by rememberSaveable { mutableStateOf(initialTab) }
    var showAccountSwitcher by remember { mutableStateOf(false) }
    val isChatsTab = currentItem == TgHomeTab.Chats
    // Бар видим на списке чатов и на корне профиля. На вложенных экранах настроек прячем,
    // иначе плавающая плашка перекрывает контент.
    val isBottomBarVisible = isChatsTab || profileTabAtRoot
    val snackbarHostState = rememberSnackbarHostState(snackbarMessage = state.snackbarMessage)
    val roomListState: RoomListState = state.roomListState

    BackHandler(enabled = !isChatsTab || state.roomListState.spaceFiltersState is SpaceFiltersState.Selected) {
        if (!isChatsTab) {
            currentItem = TgHomeTab.Chats
        } else {
            val spaceFiltersState = state.roomListState.spaceFiltersState
            if (spaceFiltersState is SpaceFiltersState.Selected) {
                spaceFiltersState.eventSink(SpaceFiltersEvent.Selected.ClearSelection)
            }
        }
    }

    val hazeState = rememberHazeState()
    val roomsLazyListState = rememberLazyListState()
    // Folder pages seen before, by folder: they outlive the Chats tab, so switching tabs keeps them.
    val folderPageCache = remember { mutableStateMapOf<String, RoomListContentState>() }
    val coroutineScope = rememberCoroutineScope()

    CompositionLocalProvider(LocalChatGlassState provides hazeState) {
        Box(modifier = modifier.fillMaxSize()) {
            // Plan 9.3: the tabs cross-fade with a 24 dp shift towards the tab being opened.
            val tabShiftPx = with(LocalDensity.current) { TAB_SHIFT.roundToPx() }
            AnimatedContent(
                targetState = currentItem,
                transitionSpec = {
                    val toRight = targetState.ordinal > initialState.ordinal
                    val enterFrom = if (toRight) tabShiftPx else -tabShiftPx
                    (
                        fadeIn(TgMotion.tween(TgMotion.TAB_MS)) +
                            slideInHorizontally(TgMotion.tween(TgMotion.TAB_MS)) { enterFrom }
                        ) togetherWith (
                        fadeOut(TgMotion.tween(TgMotion.TAB_MS)) +
                            slideOutHorizontally(TgMotion.tween(TgMotion.TAB_MS)) { -enterFrom }
                        )
                },
                label = "homeTabs",
            ) { tab ->
                when (tab) {
                    TgHomeTab.Chats -> ChatsTab(
                        state = state,
                        hazeState = hazeState,
                        lazyListState = roomsLazyListState,
                        onSetUpRecoveryClick = onSetUpRecoveryClick,
                        onConfirmRecoveryKeyClick = onConfirmRecoveryKeyClick,
                        onRoomClick = onRoomClick,
                        onStartChatClick = onStartChatClick,
                        onMenuActionClick = onMenuActionClick,
                        musicMiniPlayer = musicMiniPlayer,
                        folderPageCache = folderPageCache,
                    )
                    // The Profile tab renders the merged profile + settings screen, hosted as a child
                    // node by HomeFlowNode (persistent bottom bar, Telegram-style in-place switch).
                    TgHomeTab.Profile -> HomeTabContent(
                        content = profileContent,
                        placeholderLabel = stringResource(TgHomeTab.Profile.labelRes),
                        modifier = Modifier.hazeSource(state = hazeState),
                    )
                }
            }

            TgAccountSwitcher(
                isVisible = showAccountSwitcher,
                accounts = accounts,
                hazeState = hazeState,
                onAccountClick = { sessionId ->
                    showAccountSwitcher = false
                    state.eventSink(HomeEvent.DismissSwipeAvatarAnnouncement)
                    onSwitchToAccount(sessionId)
                },
                onAddAccountClick = {
                    showAccountSwitcher = false
                    onAddAccountClick()
                },
                onDismiss = { showAccountSwitcher = false },
            )

            if (isBottomBarVisible) {
                TgHomeTabBar(
                    selectedItem = currentItem,
                    currentUser = state.currentUserAndNeighbors.let { users ->
                        if (users.size == 3) users[1] else users.firstOrNull()
                    },
                    unreadCount = state.totalUnreadCount,
                    isProfilePressed = showAccountSwitcher,
                    onItemClick = { item ->
                        showAccountSwitcher = false
                        if (item == currentItem) {
                            // Scroll to top when selecting the Chats tab while already on it.
                            if (item == TgHomeTab.Chats) {
                                coroutineScope.launch {
                                    if (roomsLazyListState.firstVisibleItemIndex > 10) {
                                        roomsLazyListState.scrollToItem(10)
                                    }
                                    roomsLazyListState.animateScrollToItem(0)
                                }
                            }
                        } else {
                            currentItem = item
                        }
                    },
                    // With one account the long-press does nothing (no popover with only «Добавить аккаунт»).
                    onProfileLongClick = if (accounts.size > 1) {
                        { showAccountSwitcher = true }
                    } else {
                        null
                    },
                    onSearchClick = {
                        showAccountSwitcher = false
                        if (roomListState.globalSearchState.isEnabled) {
                            roomListState.globalSearchState.eventSink(GlobalSearchEvent.ToggleSearchVisibility)
                        } else {
                            roomListState.eventSink(RoomListEvent.ToggleSearchResults)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(bottom = TgHomeBarDefaults.bottomMargin),
                )
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = TgHomeBarDefaults.contentBottomPadding),
            )
        }
    }
}

/**
 * The Chats tab: the room list fills the screen and scrolls under the floating header (wordmark +
 * glass folder card) and the bottom bar, so the glass has something to blur.
 */
@Composable
private fun ChatsTab(
    state: HomeState,
    hazeState: HazeState,
    lazyListState: LazyListState,
    onSetUpRecoveryClick: () -> Unit,
    onConfirmRecoveryKeyClick: () -> Unit,
    onRoomClick: (RoomId) -> Unit,
    onStartChatClick: () -> Unit,
    onMenuActionClick: (RoomListMenuAction) -> Unit,
    musicMiniPlayer: (@Composable (Modifier) -> Unit)?,
    folderPageCache: SnapshotStateMap<String, RoomListContentState> = remember { mutableStateMapOf() },
) {
    val roomListState = state.roomListState
    val density = LocalDensity.current
    var headerHeight by remember { mutableStateOf(140.dp) }
    val horizontal = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).asPaddingValues()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val folderPager = rememberSpaceFolderPagerState()
    val contentPadding = PaddingValues(
        start = horizontal.calculateLeftPadding(LayoutDirection.Ltr),
        end = horizontal.calculateRightPadding(LayoutDirection.Ltr),
        top = headerHeight + 6.dp,
        bottom = navBottom + TgHomeBarDefaults.contentBottomPadding,
    )
    // Remember the list of the folder the presenter shows for sure, so a later move to that
    // folder slides in a ready page instead of waiting for the presenter.
    val spaceFiltersState = roomListState.spaceFiltersState
    val folderKeys = listOf(ALL_FOLDERS_KEY) + spaceFiltersState.availableFilters().map { it.spaceRoom.roomId.value }
    val freshFolder = folderPager.freshIndex
    LaunchedEffect(roomListState.contentState, freshFolder) {
        val content = roomListState.contentState
        val key = freshFolder?.let(folderKeys::getOrNull) ?: return@LaunchedEffect
        if (content is RoomListContentState.Rooms || content is RoomListContentState.Empty) folderPageCache[key] = content
    }
    // A remembered page must not tell the presenter which rooms are on screen: that is the live list's job.
    val cachedPageEventSink = remember(roomListState.eventSink) {
        { event: RoomListEvent -> if (event !is RoomListEvent.UpdateVisibleRange) roomListState.eventSink(event) }
    }
    Box(Modifier.fillMaxSize()) {
        SpaceFolderSwipe(
            state = spaceFiltersState,
            pagerState = folderPager,
            // A folder switch goes through an empty list first: only a list with rooms counts.
            contentKey = (roomListState.contentState as? RoomListContentState.Rooms)?.summaries?.takeIf { it.isNotEmpty() },
            hasCachedPage = { index -> folderKeys.getOrNull(index)?.let { it in folderPageCache } == true },
            cachedPage = { index ->
                val cached = folderKeys.getOrNull(index)?.let { folderPageCache[it] }
                if (cached != null) {
                    RoomListContentView(
                        contentState = cached,
                        filtersState = roomListState.filtersState,
                        spaceFiltersState = spaceFiltersState,
                        lazyListState = rememberLazyListState(),
                        hideInvitesAvatars = roomListState.hideInvitesAvatars,
                        eventSink = cachedPageEventSink,
                        onSetUpRecoveryClick = onSetUpRecoveryClick,
                        onConfirmRecoveryKeyClick = onConfirmRecoveryKeyClick,
                        onRoomClick = { room -> onRoomClick(room.roomId) },
                        onCreateRoomClick = onStartChatClick,
                        contentPadding = contentPadding,
                        showBannersInList = false,
                    )
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState)
                .tgGlowBackground(),
        ) {
            RoomListContentView(
                contentState = roomListState.contentState,
                filtersState = roomListState.filtersState,
                spaceFiltersState = roomListState.spaceFiltersState,
                lazyListState = lazyListState,
                hideInvitesAvatars = roomListState.hideInvitesAvatars,
                eventSink = roomListState.eventSink,
                onSetUpRecoveryClick = onSetUpRecoveryClick,
                onConfirmRecoveryKeyClick = onConfirmRecoveryKeyClick,
                onRoomClick = { room -> onRoomClick(room.roomId) },
                onCreateRoomClick = onStartChatClick,
                contentPadding = contentPadding,
                showBannersInList = false,
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .onSizeChanged { headerHeight = with(density) { it.height.toDp() } }
        ) {
            TgHomeTopBar(
                spaceFiltersState = roomListState.spaceFiltersState,
                allUnreadCount = state.totalUnreadCount,
                onStartChatClick = onStartChatClick,
                onMenuActionClick = onMenuActionClick,
                canReportBug = state.canReportBug,
                pagerState = folderPager,
                notices = {
                    RoomListNotices(
                        contentState = roomListState.contentState,
                        eventSink = roomListState.eventSink,
                        onSetUpRecoveryClick = onSetUpRecoveryClick,
                        onConfirmRecoveryKeyClick = onConfirmRecoveryKeyClick,
                    )
                },
            )
            // Правка форка (Larpgram): мини-плеер — стеклянная пилюля под карточкой папок.
            musicMiniPlayer?.invoke(Modifier.fillMaxWidth().padding(top = 6.dp))
        }
        SpaceFiltersView(roomListState.spaceFiltersState)
    }
}

/**
 * Renders the Profile tab: the real hosted feature screen when a content slot is provided (in the
 * running app), or a labelled placeholder otherwise (composable previews).
 */
@Composable
private fun HomeTabContent(
    content: (@Composable () -> Unit)?,
    placeholderLabel: String,
    modifier: Modifier = Modifier,
) {
    if (content != null) {
        // The hosted node brings its own header and manages its own insets.
        Box(modifier = modifier.fillMaxSize()) {
            content()
        }
    } else {
        Box(
            modifier = modifier
                .fillMaxSize()
                .tgGlowBackground(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = placeholderLabel,
                style = ElementTheme.typography.fontHeadingMdBold,
                color = ElementTheme.colors.textSecondary,
            )
        }
    }
}

private const val ALL_FOLDERS_KEY = "all"
private val TAB_SHIFT = 24.dp

@PreviewsDayNight
@Composable
internal fun TgHomeViewPreview(@PreviewParameter(HomeStatePreviewParam::class) state: HomeState) = ElementPreview {
    TgHomeView(
        homeState = state,
        onRoomClick = { _, _ -> },
        onSwitchToAccount = {},
        onSetUpRecoveryClick = {},
        onConfirmRecoveryKeyClick = {},
        onStartChatClick = {},
        onRoomSettingsClick = {},
        onReportRoomClick = {},
        onMenuActionClick = {},
        onDeclineInviteAndBlockUser = {},
        acceptDeclineInviteView = {},
        leaveRoomView = {}
    )
}

@Preview
@Composable
internal fun TgHomeViewA11yPreview() = ElementPreview {
    TgHomeView(
        homeState = aHomeState(),
        onRoomClick = { _, _ -> },
        onSwitchToAccount = {},
        onSetUpRecoveryClick = {},
        onConfirmRecoveryKeyClick = {},
        onStartChatClick = {},
        onRoomSettingsClick = {},
        onReportRoomClick = {},
        onMenuActionClick = {},
        onDeclineInviteAndBlockUser = {},
        acceptDeclineInviteView = {},
        leaveRoomView = {}
    )
}
