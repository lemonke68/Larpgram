/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.roomlist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import dev.zacsweers.metro.Inject
import im.vector.app.features.analytics.plan.Interaction
import io.element.android.features.announcement.api.Announcement
import io.element.android.features.announcement.api.AnnouncementService
import io.element.android.features.home.impl.datasource.RoomListDataSource
import io.element.android.features.home.impl.filters.RoomListFiltersState
import io.element.android.features.home.impl.filters.into
import io.element.android.features.home.impl.search.RoomListSearchEvent
import io.element.android.features.home.impl.search.RoomListSearchState
import io.element.android.features.home.impl.spacefilters.SpaceFiltersState
import io.element.android.features.home.impl.spacefilters.into
import io.element.android.features.home.impl.spacefilters.selectedFilter
import io.element.android.features.invite.api.SeenInvitesStore
import io.element.android.features.invite.api.acceptdecline.AcceptDeclineInviteEvents.AcceptInvite
import io.element.android.features.invite.api.acceptdecline.AcceptDeclineInviteEvents.DeclineInvite
import io.element.android.features.invite.api.acceptdecline.AcceptDeclineInviteState
import io.element.android.features.leaveroom.api.LeaveRoomEvent
import io.element.android.features.leaveroom.api.LeaveRoomState
import io.element.android.features.preferences.impl.tasks.MarkRoomAsRead
import io.element.android.libraries.accountemail.api.AccountEmailStatus
import io.element.android.libraries.appupdate.api.UpdateChecker
import io.element.android.libraries.appupdate.api.UpdateStatus
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.featureflag.api.FeatureFlagService
import io.element.android.libraries.featureflag.api.FeatureFlags
import io.element.android.libraries.fullscreenintent.api.FullScreenIntentPermissionsState
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.encryption.RecoveryState
import io.element.android.libraries.matrix.api.oauth.AccountManagementAction
import io.element.android.libraries.matrix.api.roomlist.RoomList
import io.element.android.libraries.matrix.api.roomlist.RoomListFilter
import io.element.android.libraries.matrix.ui.safety.rememberHideInvitesAvatar
import io.element.android.libraries.push.api.battery.BatteryOptimizationState
import io.element.android.services.analytics.api.AnalyticsService
import io.element.android.services.analytics.api.watchers.AnalyticsColdStartWatcher
import io.element.android.services.analyticsproviders.api.trackers.captureInteraction
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch

@Inject
class RoomListPresenter(
    private val client: MatrixClient,
    private val leaveRoomPresenter: Presenter<LeaveRoomState>,
    private val roomListDataSource: RoomListDataSource,
    private val filtersPresenter: Presenter<RoomListFiltersState>,
    private val searchPresenter: Presenter<RoomListSearchState>,
    private val analyticsService: AnalyticsService,
    private val acceptDeclineInvitePresenter: Presenter<AcceptDeclineInviteState>,
    private val fullScreenIntentPermissionsPresenter: Presenter<FullScreenIntentPermissionsState>,
    private val batteryOptimizationPresenter: Presenter<BatteryOptimizationState>,
    private val markRoomAsRead: MarkRoomAsRead,
    private val seenInvitesStore: SeenInvitesStore,
    private val announcementService: AnnouncementService,
    private val coldStartWatcher: AnalyticsColdStartWatcher,
    private val spaceFiltersPresenter: Presenter<SpaceFiltersState>,
    private val featureFlagService: FeatureFlagService,
    // Правка форка: баннер с напоминанием привязать почту.
    private val accountEmailStatus: AccountEmailStatus,
    // Правка форка: баннер с предложением обновиться.
    private val updateChecker: UpdateChecker,
) : Presenter<RoomListState> {
    private val encryptionService = client.encryptionService

    @Composable
    override fun present(): RoomListState {
        val coroutineScope = rememberCoroutineScope()
        val leaveRoomState = leaveRoomPresenter.present()
        val filtersState = filtersPresenter.present()
        val searchState = searchPresenter.present()
        val spaceFiltersState = spaceFiltersPresenter.present()
        val acceptDeclineInviteState = acceptDeclineInvitePresenter.present()

        LaunchedEffect(Unit) {
            roomListDataSource.launchIn(this)
        }

        var securityBannerDismissed by rememberSaveable { mutableStateOf(false) }

        // Правка форка: баннер про почту.
        //
        // Спрашиваем один раз за показ экрана, а не подпиской: адрес сам собой не
        // появляется и не исчезает, а привязка происходит в браузере, после чего человек
        // всё равно возвращается в приложение заново.
        //
        // hasEmail() == false, а не != true: null означает «не дозвонились до сервера», и
        // на нём баннер показывать нельзя, иначе он выскочит в самолётном режиме у того,
        // у кого почта давно привязана.
        var connectEmailBannerDismissed by rememberSaveable { mutableStateOf(false) }
        val accountNeedsEmail by produceState(false) {
            value = !accountEmailStatus.isBannerHidden() && accountEmailStatus.hasEmail() == false
        }
        // Адрес спрашиваем только когда баннер и правда нужен: у тех, кто почту уже
        // привязал, это лишний поход в сеть на каждом открытии списка чатов.
        val accountManagementUrl by produceState<String?>(null, accountNeedsEmail) {
            value = if (accountNeedsEmail) {
                client.getAccountManagementUrl(AccountManagementAction.Profile).getOrNull()
            } else {
                null
            }
        }
        // Правка форка: баннер про очистку старых сессий. isLastDevice == false означает, что
        // у аккаунта есть другие сессии — обычно брошенная старая после переустановки без
        // разлогина. Дважды не спорим с флагом: реальное значение приходит быстрее сетевого
        // запроса URL ниже, поэтому ложного мелькания на одном устройстве нет.
        var cleanUpSessionsBannerDismissed by rememberSaveable { mutableStateOf(false) }
        val isLastDevice by encryptionService.isLastDevice.collectAsState()
        val hasOtherSessions = !isLastDevice
        val manageSessionsUrl by produceState<String?>(null, hasOtherSessions, cleanUpSessionsBannerDismissed) {
            value = if (hasOtherSessions && !cleanUpSessionsBannerDismissed) {
                client.getAccountManagementUrl(AccountManagementAction.DevicesList).getOrNull()
            } else {
                null
            }
        }

        // Правка форка: баннер обновления. Спрашиваем манифест один раз за показ экрана,
        // как и почту: версия сама собой не меняется, а обновление всё равно уводит человека
        // в браузер и назад. check() уже отсекает старые и отклонённые версии, поэтому здесь
        // достаточно проверить, что вернулось Available.
        var updateBannerDismissed by rememberSaveable { mutableStateOf(false) }
        val updateStatus by produceState<UpdateStatus>(UpdateStatus.Unknown) {
            value = updateChecker.check()
        }

        val showNewNotificationSoundBanner by remember {
            announcementService.announcementsToShowFlow().map { announcements ->
                announcements.contains(Announcement.NewNotificationSound)
            }
        }.collectAsState(false)

        // Avatar indicator
        val hideInvitesAvatar by client.rememberHideInvitesAvatar()

        val contextMenu = remember { mutableStateOf<RoomListState.ContextMenu>(RoomListState.ContextMenu.Hidden) }
        val declineInviteMenu = remember { mutableStateOf<RoomListState.DeclineInviteMenu>(RoomListState.DeclineInviteMenu.Hidden) }

        fun handleEvent(event: RoomListEvent) {
            when (event) {
                is RoomListEvent.UpdateVisibleRange -> coroutineScope.launch {
                    roomListDataSource.updateVisibleRange(event.range)
                }
                RoomListEvent.DismissRequestVerificationPrompt -> securityBannerDismissed = true
                RoomListEvent.DismissBanner -> securityBannerDismissed = true
                RoomListEvent.DismissNewNotificationSoundBanner -> coroutineScope.launch {
                    announcementService.onAnnouncementDismissed(Announcement.NewNotificationSound)
                }
                RoomListEvent.DismissConnectEmailBanner -> {
                    connectEmailBannerDismissed = true
                    coroutineScope.launch { accountEmailStatus.hideBanner() }
                }
                RoomListEvent.DismissCleanUpSessionsBanner -> {
                    cleanUpSessionsBannerDismissed = true
                }
                RoomListEvent.DismissUpdateBanner -> {
                    updateBannerDismissed = true
                    // Запоминаем именно эту версию, чтобы следующая, ещё более свежая, снова
                    // показала баннер. Если статус ещё не подъехал — прячем только на сессию.
                    (updateStatus as? UpdateStatus.Available)?.let { available ->
                        coroutineScope.launch { updateChecker.dismiss(available.versionCode) }
                    }
                }
                RoomListEvent.ToggleSearchResults -> searchState.eventSink(RoomListSearchEvent.ToggleSearchVisibility)
                is RoomListEvent.ShowContextMenu -> {
                    coroutineScope.showContextMenu(event, contextMenu)
                }
                is RoomListEvent.HideContextMenu -> {
                    contextMenu.value = RoomListState.ContextMenu.Hidden
                }
                is RoomListEvent.LeaveRoom -> {
                    leaveRoomState.eventSink(LeaveRoomEvent.LeaveRoom(event.roomId, needsConfirmation = event.needsConfirmation))
                }
                is RoomListEvent.SetRoomIsFavorite -> coroutineScope.setRoomIsFavorite(event.roomId, event.isFavorite)
                is RoomListEvent.SetRoomIsMuted -> coroutineScope.setRoomIsMuted(event.roomId, event.isMuted)
                is RoomListEvent.MarkAsRead -> coroutineScope.markAsRead(event.roomId)
                is RoomListEvent.MarkAsUnread -> coroutineScope.markAsUnread(event.roomId)
                is RoomListEvent.AcceptInvite -> {
                    acceptDeclineInviteState.eventSink(
                        AcceptInvite(event.roomSummary.toInviteData())
                    )
                }
                is RoomListEvent.DeclineInvite -> {
                    acceptDeclineInviteState.eventSink(
                        DeclineInvite(event.roomSummary.toInviteData(), blockUser = event.blockUser, shouldConfirm = false)
                    )
                }
                is RoomListEvent.ShowDeclineInviteMenu -> declineInviteMenu.value = RoomListState.DeclineInviteMenu.Shown(event.roomSummary)
                RoomListEvent.HideDeclineInviteMenu -> declineInviteMenu.value = RoomListState.DeclineInviteMenu.Hidden
            }
        }

        LaunchedEffect(filtersState.filterSelectionStates, spaceFiltersState.selectedFilter()) {
            val selectedFilters = filtersState.selectedFilters().map { filter -> filter.into() }
            val selectedSpaceFilter = spaceFiltersState.selectedFilter().into()
            val allFilters = RoomListFilter.All(selectedFilters + listOfNotNull(selectedSpaceFilter))
            roomListDataSource.updateFilter(allFilters)
        }

        val canReportRoom by produceState(false) { value = client.canReportRoom() }
        val showUnreadCount by produceState(false) {
            value = featureFlagService.isFeatureEnabled(FeatureFlags.UnreadIndicatorCount)
        }

        val contentState = roomListContentState(
            securityBannerDismissed,
            // Баннер про почту показываем, только если знаем, куда вести человека.
            showConnectEmailBanner = accountNeedsEmail && !connectEmailBannerDismissed && accountManagementUrl != null,
            accountManagementUrl = accountManagementUrl,
            // То же и с сессиями: без адреса страницы управления вести некуда.
            showCleanUpSessionsBanner = hasOtherSessions && !cleanUpSessionsBannerDismissed && manageSessionsUrl != null,
            manageSessionsUrl = manageSessionsUrl,
            showUpdateBanner = updateStatus is UpdateStatus.Available && !updateBannerDismissed,
            showNewNotificationSoundBanner,
            showUnreadCount,
        )

        return RoomListState(
            contextMenu = contextMenu.value,
            declineInviteMenu = declineInviteMenu.value,
            leaveRoomState = leaveRoomState,
            filtersState = filtersState,
            searchState = searchState,
            spaceFiltersState = spaceFiltersState,
            contentState = contentState,
            acceptDeclineInviteState = acceptDeclineInviteState,
            hideInvitesAvatars = hideInvitesAvatar,
            canReportRoom = canReportRoom,
            eventSink = ::handleEvent,
        )
    }

    @Composable
    private fun rememberSecurityBannerState(
        securityBannerDismissed: Boolean,
        showConnectEmailBanner: Boolean,
        showCleanUpSessionsBanner: Boolean,
        showUpdateBanner: Boolean,
    ): State<SecurityBannerState> {
        val currentSecurityBannerDismissed by rememberUpdatedState(securityBannerDismissed)
        val currentShowConnectEmailBanner by rememberUpdatedState(showConnectEmailBanner)
        val currentShowCleanUpSessionsBanner by rememberUpdatedState(showCleanUpSessionsBanner)
        val currentShowUpdateBanner by rememberUpdatedState(showUpdateBanner)
        val recoveryState by encryptionService.recoveryStateStateFlow.collectAsState()
        return remember {
            derivedStateOf {
                calculateBannerState(
                    securityBannerDismissed = currentSecurityBannerDismissed,
                    showConnectEmailBanner = currentShowConnectEmailBanner,
                    showCleanUpSessionsBanner = currentShowCleanUpSessionsBanner,
                    showUpdateBanner = currentShowUpdateBanner,
                    recoveryState = recoveryState,
                )
            }
        }
    }

    private fun calculateBannerState(
        securityBannerDismissed: Boolean,
        showConnectEmailBanner: Boolean,
        showCleanUpSessionsBanner: Boolean,
        showUpdateBanner: Boolean,
        recoveryState: RecoveryState,
    ): SecurityBannerState {
        if (!securityBannerDismissed) {
            when (recoveryState) {
                RecoveryState.DISABLED -> return SecurityBannerState.SetUpRecovery
                RecoveryState.INCOMPLETE -> return SecurityBannerState.RecoveryKeyConfirmation
                RecoveryState.UNKNOWN,
                RecoveryState.WAITING_FOR_SYNC,
                RecoveryState.ENABLED -> Unit
            }
        }

        // Правка форка: почта живёт своей жизнью и своим «скрыть». Закрытый баннер про
        // ключи не должен заодно прятать напоминание про почту, это разные проблемы.
        // Показываем вторым: ключи важнее, а два баннера разом это уже свалка.
        if (showConnectEmailBanner) {
            return SecurityBannerState.ConnectEmail
        }

        if (showCleanUpSessionsBanner) {
            return SecurityBannerState.CleanUpSessions
        }

        // Правка форка: обновление — самый низкий приоритет. Безопасность аккаунта важнее,
        // а обновиться человек успеет и после того, как разберётся с ключами и сессиями.
        if (showUpdateBanner) {
            return SecurityBannerState.UpdateAvailable
        }

        return SecurityBannerState.None
    }

    @Composable
    private fun roomListContentState(
        securityBannerDismissed: Boolean,
        showConnectEmailBanner: Boolean,
        accountManagementUrl: String?,
        showCleanUpSessionsBanner: Boolean,
        manageSessionsUrl: String?,
        showUpdateBanner: Boolean,
        showNewNotificationSoundBanner: Boolean,
        showUnreadCount: Boolean,
    ): RoomListContentState {
        val roomSummaries by produceState(initialValue = AsyncData.Loading()) {
            roomListDataSource.roomSummariesFlow.collect { value = AsyncData.Success(it) }
        }
        val loadingState by roomListDataSource.loadingState.collectAsState()
        val showEmpty by remember {
            derivedStateOf {
                (loadingState as? RoomList.LoadingState.Loaded)?.numberOfRooms == 0
            }
        }
        val showSkeleton by remember {
            derivedStateOf {
                loadingState == RoomList.LoadingState.NotLoaded || roomSummaries is AsyncData.Loading
            }
        }
        val seenRoomInvites by remember { seenInvitesStore.seenRoomIds() }.collectAsState(emptySet())
        val securityBannerState by rememberSecurityBannerState(securityBannerDismissed, showConnectEmailBanner, showCleanUpSessionsBanner, showUpdateBanner)
        return when {
            showEmpty -> RoomListContentState.Empty(
                securityBannerState = securityBannerState,
                accountManagementUrl = accountManagementUrl,
                manageSessionsUrl = manageSessionsUrl,
            )
            showSkeleton -> RoomListContentState.Skeleton(count = 16)
            else -> {
                coldStartWatcher.onRoomListVisible()

                RoomListContentState.Rooms(
                    securityBannerState = securityBannerState,
                    accountManagementUrl = accountManagementUrl,
                    manageSessionsUrl = manageSessionsUrl,
                    showNewNotificationSoundBanner = showNewNotificationSoundBanner,
                    showUnreadCount = showUnreadCount,
                    fullScreenIntentPermissionsState = fullScreenIntentPermissionsPresenter.present(),
                    batteryOptimizationState = batteryOptimizationPresenter.present(),
                    summaries = roomSummaries.dataOrNull().orEmpty().toImmutableList(),
                    seenRoomInvites = seenRoomInvites.toImmutableSet(),
                )
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun CoroutineScope.showContextMenu(event: RoomListEvent.ShowContextMenu, contextMenuState: MutableState<RoomListState.ContextMenu>) = launch {
        val initialState = RoomListState.ContextMenu.Shown(
            roomId = event.roomSummary.roomId,
            roomName = event.roomSummary.name,
            isDm = event.roomSummary.isDm,
            isFavorite = event.roomSummary.isFavorite,
            hasNewContent = event.roomSummary.hasNewContent,
        )
        contextMenuState.value = initialState

        client.getRoom(event.roomSummary.roomId)?.use { room ->

            val isShowingContextMenuFlow = snapshotFlow { contextMenuState.value is RoomListState.ContextMenu.Shown }
                .distinctUntilChanged()

            val isFavoriteFlow = room.roomInfoFlow
                .map { it.isFavorite }
                .distinctUntilChanged()

            isFavoriteFlow
                .onEach { isFavorite ->
                    contextMenuState.value = initialState.copy(isFavorite = isFavorite)
                }
                .flatMapLatest { isShowingContextMenuFlow }
                .takeWhile { isShowingContextMenu -> isShowingContextMenu }
                .collect()
        }
    }

    private fun CoroutineScope.setRoomIsFavorite(roomId: RoomId, isFavorite: Boolean) = launch {
        client.getRoom(roomId)?.use { room ->
            room.setIsFavorite(isFavorite)
                .onSuccess {
                    analyticsService.captureInteraction(name = Interaction.Name.MobileRoomListRoomContextMenuFavouriteToggle)
                }
        }
    }

    private fun CoroutineScope.setRoomIsMuted(roomId: RoomId, isMuted: Boolean) = launch {
        val notificationSettings = client.notificationSettingsService
        if (isMuted) {
            notificationSettings.muteRoom(roomId)
        } else {
            client.getRoom(roomId)?.use { room ->
                val info = room.info()
                notificationSettings.unmuteRoom(
                    roomId = roomId,
                    isEncrypted = info.isEncrypted == true,
                    isOneToOne = info.isDm,
                )
            }
        }
    }

    private fun CoroutineScope.markAsRead(roomId: RoomId) = launch {
        markRoomAsRead(roomId)
            .onSuccess {
                analyticsService.captureInteraction(name = Interaction.Name.MobileRoomListRoomContextMenuUnreadToggle)
            }
    }

    private fun CoroutineScope.markAsUnread(roomId: RoomId) = launch {
        client.getRoom(roomId)?.use { room ->
            room.setUnreadFlag(isUnread = true)
                .onSuccess {
                    analyticsService.captureInteraction(name = Interaction.Name.MobileRoomListRoomContextMenuUnreadToggle)
                }
        }
    }
}
