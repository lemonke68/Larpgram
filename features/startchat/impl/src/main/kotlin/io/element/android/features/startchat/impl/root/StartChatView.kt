/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.startchat.impl.root

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.startchat.api.ConfirmingStartDmWithMatrixUser
import io.element.android.features.startchat.impl.R
import io.element.android.features.startchat.impl.components.UserListView
import io.element.android.libraries.androidutils.ui.hideKeyboardAndAwaitAnimation
import io.element.android.libraries.designsystem.atomic.atoms.RoundedIconAtom
import io.element.android.libraries.designsystem.atomic.atoms.RoundedIconAtomSize
import io.element.android.libraries.designsystem.components.async.AsyncActionView
import io.element.android.libraries.designsystem.components.async.AsyncActionViewDefaults
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.ListSectionHeader
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.designsystem.utils.lazyColumnContentPadding
import io.element.android.libraries.designsystem.utils.scaffoldScrollableContentInsets
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.ui.components.CreateDmConfirmationBottomSheet
import io.element.android.libraries.matrix.ui.components.MatrixUserRow
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch

@Composable
fun StartChatView(
    state: StartChatState,
    onCloseClick: () -> Unit,
    onNewRoomClick: () -> Unit,
    onNewChannelClick: () -> Unit,
    onOpenDM: (RoomId) -> Unit,
    onInviteFriendsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier.fillMaxWidth(),
        topBar = {
            if (!state.userListState.isSearchActive) {
                CreateRoomRootViewTopBar(onCloseClick = onCloseClick)
            }
        },
        contentWindowInsets = scaffoldScrollableContentInsets,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val view = LocalView.current

            UserListView(
                modifier = Modifier.fillMaxWidth(),
                // Do not render suggestions in this case, the suggestion will be rendered
                // by CreateRoomActionButtonsList
                state = state.userListState.copy(
                    recentDirectRooms = persistentListOf(),
                ),
                onSelectUser = {
                    coroutineScope.launch {
                        view.hideKeyboardAndAwaitAnimation()
                        state.eventSink(StartChatEvent.StartDM(it))
                    }
                },
                onDeselectUser = { },
            )

            if (!state.userListState.isSearchActive) {
                CreateRoomActionButtonsList(
                    state = state,
                    onNewRoomClick = onNewRoomClick,
                    onNewChannelClick = onNewChannelClick,
                    onInvitePeopleClick = onInviteFriendsClick,
                    onDmClick = onOpenDM,
                )
            }
        }
    }

    AsyncActionView(
        async = state.startDmAction,
        progressDialog = {
            AsyncActionViewDefaults.ProgressDialog(
                progressText = stringResource(CommonStrings.common_starting_chat),
            )
        },
        onSuccess = { onOpenDM(it) },
        errorMessage = { stringResource(R.string.screen_start_chat_error_starting_chat) },
        onRetry = {
            state.userListState.selectedUsers.firstOrNull()
                ?.let { state.eventSink(StartChatEvent.StartDM(it)) }
            // Cancel start DM if there is no more selected user (should not happen)
                ?: state.eventSink(StartChatEvent.CancelStartDM)
        },
        onErrorDismiss = { state.eventSink(StartChatEvent.CancelStartDM) },
        confirmationDialog = { data ->
            if (data is ConfirmingStartDmWithMatrixUser) {
                CreateDmConfirmationBottomSheet(
                    matrixUser = data.matrixUser,
                    isUserIdentityUnknown = data.isUserIdentityUnknown,
                    onSendInvite = {
                        state.eventSink(StartChatEvent.StartDM(data.matrixUser))
                    },
                    onDismiss = {
                        state.eventSink(StartChatEvent.CancelStartDM)
                    },
                )
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateRoomRootViewTopBar(
    onCloseClick: () -> Unit,
) {
    TopAppBar(
        // Правка форка: «Новое сообщение» со стрелкой назад, как в TG.
        titleStr = stringResource(id = CommonStrings.larpgram_new_message),
        navigationIcon = {
            BackButton(onClick = onCloseClick)
        }
    )
}

/**
 * Правка форка: экран «Новое сообщение» TG (`ContactsAdapter`): сверху «Новая группа» и «Новый
 * канал» с цветными иконками, ниже недавние собеседники, в конце — пригласить друзей. Каталог
 * комнат и вход по адресу — понятия Matrix, в TG их нет.
 */
@Composable
private fun CreateRoomActionButtonsList(
    state: StartChatState,
    onNewRoomClick: () -> Unit,
    onNewChannelClick: () -> Unit,
    onInvitePeopleClick: () -> Unit,
    onDmClick: (RoomId) -> Unit,
) {
    LazyColumn(
        contentPadding = lazyColumnContentPadding,
    ) {
        item {
            NewMessageActionRow(
                imageVector = CompoundIcons.Group(),
                color = NEW_GROUP_COLOR,
                text = stringResource(id = CommonStrings.larpgram_new_group),
                onClick = onNewRoomClick,
            )
        }
        item {
            NewMessageActionRow(
                imageVector = CompoundIcons.Public(),
                color = NEW_CHANNEL_COLOR,
                text = stringResource(id = CommonStrings.larpgram_new_channel),
                onClick = onNewChannelClick,
            )
        }
        if (state.userListState.recentDirectRooms.isNotEmpty()) {
            item {
                ListSectionHeader(
                    title = stringResource(id = CommonStrings.larpgram_recent_chats),
                    hasDivider = false,
                )
            }
            state.userListState.recentDirectRooms.forEach { recentDirectRoom ->
                item {
                    MatrixUserRow(
                        modifier = Modifier.clickable(
                            onClick = {
                                onDmClick(recentDirectRoom.roomId)
                            }
                        ),
                        matrixUser = recentDirectRoom.matrixUser,
                    )
                }
            }
        }
        item {
            NewMessageActionRow(
                imageVector = CompoundIcons.ShareAndroid(),
                color = INVITE_COLOR,
                text = stringResource(id = CommonStrings.action_invite_friends_to_app, state.applicationName),
                onClick = onInvitePeopleClick,
            )
        }
    }
}

@Composable
private fun NewMessageActionRow(
    imageVector: ImageVector,
    color: Color,
    text: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundedIconAtom(
            size = RoundedIconAtomSize.Medium,
            imageVector = imageVector,
            tint = Color.White,
            backgroundTint = color,
        )
        Text(
            text = text,
            style = ElementTheme.typography.fontBodyLgRegular,
        )
    }
}

// Цвета иконок из TG (`setTextAndValueAndColorfulIcon`): группа — синяя, канал — зелёный.
private val NEW_GROUP_COLOR = Color(0xFF1CA5ED)
private val NEW_CHANNEL_COLOR = Color(0xFF55CA47)
private val INVITE_COLOR = Color(0xFFF3A33B)

@PreviewsDayNight
@Composable
internal fun StartChatViewPreview(@PreviewParameter(StartChatStatePreviewParam::class) state: StartChatState) =
    ElementPreview {
        StartChatView(
            state = state,
            onCloseClick = {},
            onNewRoomClick = {},
            onNewChannelClick = {},
            onOpenDM = {},
            onInviteFriendsClick = {},
        )
    }
