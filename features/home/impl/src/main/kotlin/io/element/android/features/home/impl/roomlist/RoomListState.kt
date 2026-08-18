/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.roomlist

import androidx.compose.runtime.Immutable
import io.element.android.features.home.impl.filters.RoomListFiltersState
import io.element.android.features.home.impl.model.RoomListRoomSummary
import io.element.android.features.home.impl.search.RoomListSearchState
import io.element.android.features.home.impl.spacefilters.SpaceFiltersState
import io.element.android.features.invite.api.acceptdecline.AcceptDeclineInviteState
import io.element.android.features.leaveroom.api.LeaveRoomState
import io.element.android.libraries.fullscreenintent.api.FullScreenIntentPermissionsState
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.push.api.battery.BatteryOptimizationState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet

data class RoomListState(
    val contextMenu: ContextMenu,
    val declineInviteMenu: DeclineInviteMenu,
    val leaveRoomState: LeaveRoomState,
    val filtersState: RoomListFiltersState,
    val searchState: RoomListSearchState,
    val spaceFiltersState: SpaceFiltersState,
    val contentState: RoomListContentState,
    val acceptDeclineInviteState: AcceptDeclineInviteState,
    val hideInvitesAvatars: Boolean,
    val canReportRoom: Boolean,
    val eventSink: (RoomListEvent) -> Unit,
) {
    val displayFilters = contentState is RoomListContentState.Rooms

    sealed interface ContextMenu {
        data object Hidden : ContextMenu
        data class Shown(
            val roomId: RoomId,
            val roomName: String?,
            val isDm: Boolean,
            val isFavorite: Boolean,
            val hasNewContent: Boolean,
        ) : ContextMenu
    }

    sealed interface DeclineInviteMenu {
        data object Hidden : DeclineInviteMenu
        data class Shown(val roomSummary: RoomListRoomSummary) : DeclineInviteMenu
    }
}

enum class SecurityBannerState {
    None,
    SetUpRecovery,
    RecoveryKeyConfirmation,

    /** Правка форка: у аккаунта нет почты, а значит нечем восстановить доступ. */
    ConnectEmail,

    /**
     * Правка форка: у аккаунта есть другие сессии (`isLastDevice == false`). Обычно это
     * брошенная старая сессия после переустановки без разлогина. Предлагаем её убрать: с
     * ONLY_TRUSTED_DEVICES она молча не получает новые сообщения, а висит зря.
     */
    CleanUpSessions,

    /**
     * Правка форка: на сайте раздачи лежит версия свежее установленной. Larpgram раздаётся
     * файлом, магазин за обновлениями не следит, поэтому предлагаем обновиться сами. Самый
     * низкий приоритет: сначала ключи, почта и сессии.
     */
    UpdateAvailable,
}

@Immutable
sealed interface RoomListContentState {
    data class Skeleton(val count: Int) : RoomListContentState
    data class Empty(
        val securityBannerState: SecurityBannerState,
        // Правка форка: адрес страницы аккаунта в MAS, туда ведёт баннер про почту.
        // null значит, что сервер её не отдал, и тогда баннер не показываем.
        val accountManagementUrl: String? = null,
        // Правка форка: адрес страницы управления сессиями (MAS DevicesList), туда ведёт
        // баннер про очистку старых сессий.
        val manageSessionsUrl: String? = null,
    ) : RoomListContentState

    data class Rooms(
        val securityBannerState: SecurityBannerState,
        // Правка форка: см. комменты у Empty выше.
        val accountManagementUrl: String? = null,
        val manageSessionsUrl: String? = null,
        val fullScreenIntentPermissionsState: FullScreenIntentPermissionsState,
        val batteryOptimizationState: BatteryOptimizationState,
        val showNewNotificationSoundBanner: Boolean,
        val showUnreadCount: Boolean,
        val summaries: ImmutableList<RoomListRoomSummary>,
        val seenRoomInvites: ImmutableSet<RoomId>,
    ) : RoomListContentState
}
