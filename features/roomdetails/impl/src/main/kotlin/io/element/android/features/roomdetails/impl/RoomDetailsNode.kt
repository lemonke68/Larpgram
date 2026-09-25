/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.roomdetails.impl

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import com.bumble.appyx.core.lifecycle.subscribe
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import im.vector.app.features.analytics.plan.MobileScreen
import io.element.android.annotations.ContributesNode
import io.element.android.features.leaveroom.api.LeaveRoomRenderer
import io.element.android.libraries.androidutils.system.startSharePlainTextIntent
import io.element.android.libraries.architecture.appyx.launchMolecule
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.di.RoomScope
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.notification.CallIntent
import io.element.android.libraries.matrix.api.room.BaseRoom
import io.element.android.libraries.matrix.ui.presence.UserPresenceFetcher
import io.element.android.libraries.matrix.ui.presence.rememberPresence
import io.element.android.libraries.matrix.ui.saved.SavedMessages
import io.element.android.libraries.mediaviewer.api.MediaViewerEntryPoint
import io.element.android.libraries.mediaviewer.api.ProfileSharedMedia
import io.element.android.services.analytics.api.AnalyticsService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import io.element.android.libraries.androidutils.R as AndroidUtilsR

@ContributesNode(RoomScope::class)
@AssistedInject
class RoomDetailsNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    presenterFactory: RoomDetailsPresenter.Factory,
    private val room: BaseRoom,
    private val analyticsService: AnalyticsService,
    private val leaveRoomRenderer: LeaveRoomRenderer,
    // Правка форка: TG-профиль — вкладки общих медиа и присутствие собеседника ЛС.
    private val profileSharedMedia: ProfileSharedMedia,
    private val userPresenceFetcher: UserPresenceFetcher,
    private val savedMessages: SavedMessages,
) : Node(buildContext, plugins = plugins), RoomDetailsNavigator {
    interface Callback : Plugin {
        fun navigateBack()
        fun navigateToRoomMemberList()
        fun navigateToInviteMembers()
        fun navigateToRoomDetailsEdit()
        fun navigateToRoomNotificationSettings()
        fun navigateToAvatarPreview(name: String, url: String)
        fun navigateToPollHistory()
        fun navigateToMediaGallery()
        fun navigateToAdminSettings()
        fun navigateToPinnedMessagesList()
        fun navigateToKnockRequestsList()
        fun navigateToSecurityAndPrivacy()
        fun navigateToRoomMemberDetails(userId: UserId)
        fun navigateToRoomCall(callIntent: CallIntent)
        fun navigateToReportRoom()
        fun navigateToSelectNewOwnersWhenLeaving()
        fun navigateToSharedMedia(params: MediaViewerEntryPoint.Params)
    }

    private val presenter = presenterFactory.create(this)
    private val callback: Callback = callback()

    init {
        lifecycle.subscribe(
            onResume = {
                analyticsService.screen(MobileScreen(screenName = MobileScreen.ScreenName.RoomDetails))
            }
        )
    }

    private fun CoroutineScope.onShareRoom(context: Context) = launch {
        room.getPermalink()
            .onSuccess { permalink ->
                context.startSharePlainTextIntent(
                    activityResultLauncher = null,
                    chooserTitle = context.getString(R.string.screen_room_details_share_room_title),
                    text = permalink,
                    noActivityFoundMessage = context.getString(AndroidUtilsR.string.error_no_compatible_app_found)
                )
            }
            .onFailure {
                Timber.e(it)
            }
    }

    private val stateFlow = launchMolecule { presenter.present() }

    fun onNewOwnersSelected() {
        stateFlow.value.eventSink(RoomDetailsEvent.LeaveRoom(needsConfirmation = false))
    }

    @Composable
    override fun View(modifier: Modifier) {
        val context = LocalContext.current
        val state by stateFlow.collectAsState()

        fun onShareRoom() {
            lifecycleScope.onShareRoom(context)
        }

        fun onActionClick(action: RoomDetailsAction) {
            when (action) {
                RoomDetailsAction.Edit -> {
                    callback.navigateToRoomDetailsEdit()
                }
                RoomDetailsAction.AddTopic -> {
                    callback.navigateToRoomDetailsEdit()
                }
            }
        }

        // Правка форка: профиль в стиле TG вместо элементовского RoomDetailsView.
        val sharedMedia = profileSharedMedia.rememberSection(onOpenMedia = callback::navigateToSharedMedia)
        val dmUserId = (state.roomType as? RoomDetailsType.Dm)?.otherMember?.userId
        val savedMessagesRoomId by savedMessages.roomId.collectAsState()
        TgRoomDetailsView(
            state = state,
            isSavedMessages = state.roomId == savedMessagesRoomId,
            sharedMedia = sharedMedia,
            presence = userPresenceFetcher.rememberPresence(dmUserId),
            modifier = modifier,
            goBack = ::navigateUp,
            onEditClick = { onActionClick(RoomDetailsAction.Edit) },
            onShareRoom = ::onShareRoom,
            openRoomMemberList = callback::navigateToRoomMemberList,
            openRoomNotificationSettings = callback::navigateToRoomNotificationSettings,
            invitePeople = callback::navigateToInviteMembers,
            openAvatarPreview = callback::navigateToAvatarPreview,
            openAdminSettings = callback::navigateToAdminSettings,
            onJoinCallClick = callback::navigateToRoomCall,
            onKnockRequestsClick = callback::navigateToKnockRequestsList,
            onSecurityAndPrivacyClick = callback::navigateToSecurityAndPrivacy,
            onMemberClick = callback::navigateToRoomMemberDetails,
            onReportRoomClick = callback::navigateToReportRoom,
            leaveRoomView = {
                leaveRoomRenderer.Render(
                    state = state.leaveRoomState,
                    onSelectNewOwners = { callback.navigateToSelectNewOwnersWhenLeaving() },
                    modifier = Modifier
                )
            }
        )
    }

    override fun onDone() {
        callback.navigateBack()
    }
}
