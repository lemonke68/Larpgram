/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.userprofile.shared

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import kotlinx.coroutines.launch
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.components.button.MainActionButton
import io.element.android.features.startchat.api.ConfirmingStartDmWithMatrixUser
import io.element.android.features.userprofile.api.UserProfileEvents
import io.element.android.features.userprofile.api.UserProfileState
import io.element.android.features.userprofile.api.UserProfileVerificationState
import io.element.android.features.userprofile.shared.blockuser.BlockUserDialogs
import io.element.android.features.userprofile.shared.blockuser.BlockUserSection
import io.element.android.libraries.designsystem.components.async.AsyncActionView
import io.element.android.libraries.designsystem.components.async.AsyncActionViewDefaults
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarHost
import io.element.android.libraries.designsystem.utils.snackbar.rememberSnackbarHostState
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.notification.CallIntent
import io.element.android.libraries.matrix.ui.components.CreateDmConfirmationBottomSheet
import io.element.android.libraries.ui.strings.CommonStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileView(
    state: UserProfileState,
    onShareUser: () -> Unit,
    onOpenDm: (RoomId) -> Unit,
    onStartCall: (RoomId, CallIntent) -> Unit,
    goBack: () -> Unit,
    openAvatarPreview: (username: String, url: String) -> Unit,
    onVerifyClick: (UserId) -> Unit,
    modifier: Modifier = Modifier,
    // Self-profile (TG-style) actions. Default no-op so other-user previews/callers
    // don't have to provide them.
    onOpenSettings: () -> Unit = {},
    onEditProfile: () -> Unit = {},
) {
    val snackbarHostState = rememberSnackbarHostState(snackbarMessage = state.snackbarMessage)
    // Pull-down-to-expand avatar (TG-style). Fraction 0 = circle, 1 = full-width square.
    val expandFraction = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val maxDragPx = remember(density, screenWidthDp) {
        with(density) { (screenWidthDp.dp - AvatarSize.UserHeader.dp).toPx() }.coerceAtLeast(1f)
    }
    val avatarNestedScroll = remember(maxDragPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Finger moving up: collapse the avatar before the content scrolls.
                val delta = available.y
                if (delta < 0f && expandFraction.value > 0f) {
                    val newFraction = (expandFraction.value + delta / maxDragPx).coerceIn(0f, 1f)
                    val consumed = (newFraction - expandFraction.value) * maxDragPx
                    scope.launch { expandFraction.snapTo(newFraction) }
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // Content already at top and finger still moving down: pull the avatar open.
                val delta = available.y
                if (delta > 0f) {
                    val newFraction = (expandFraction.value + delta / maxDragPx).coerceIn(0f, 1f)
                    scope.launch { expandFraction.snapTo(newFraction) }
                    return Offset(0f, delta)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                // Settle to the nearer end so the avatar never rests half-open.
                if (expandFraction.value > 0f && expandFraction.value < 1f) {
                    val target = if (expandFraction.value > 0.5f) 1f else 0f
                    expandFraction.animateTo(target)
                }
                return Velocity.Zero
            }
        }
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { },
                // Self-profile is a bottom-nav tab (no back stack to pop), so no back arrow.
                navigationIcon = {
                    if (!state.isCurrentUser) {
                        BackButton(onClick = goBack)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .nestedScroll(avatarNestedScroll)
                    .verticalScroll(rememberScrollState())
        ) {
            UserProfileHeaderSection(
                avatarUrl = state.avatarUrl,
                userId = state.userId,
                userName = state.userName,
                verificationState = state.verificationState,
                displayedStatus = state.displayedStatus,
                avatarExpandFraction = expandFraction.value,
                showId = !state.isCurrentUser,
                openAvatarPreview = { avatarUrl ->
                    openAvatarPreview(state.userName ?: state.userId.value, avatarUrl)
                },
                onUserIdClick = {
                    state.eventSink(UserProfileEvents.CopyToClipboard(state.userId.value))
                },
                withdrawVerificationClick = { state.eventSink(UserProfileEvents.WithdrawVerification) },
            )
            if (state.isCurrentUser) {
                UserProfileSelfActionsSection(
                    onSetPhoto = onEditProfile,
                    onEdit = onEditProfile,
                    onSettings = onOpenSettings,
                )
                Spacer(modifier = Modifier.height(24.dp))
                UserProfileInfoCard(
                    userId = state.userId,
                    about = state.about,
                    onHandleClick = {
                        state.eventSink(UserProfileEvents.CopyToClipboard(state.userId.value))
                    },
                )
            } else {
                UserProfileMainActionsSection(
                    isCurrentUser = false,
                    canCall = state.canCall,
                    onShareUser = onShareUser,
                    onStartDM = { state.eventSink(UserProfileEvents.StartDM) },
                    onCall = { intent -> state.dmRoomId?.let { onStartCall(it, intent) } }
                )
                Spacer(modifier = Modifier.height(24.dp))
                UserProfileInfoCard(
                    userId = state.userId,
                    about = state.about,
                    onHandleClick = {
                        state.eventSink(UserProfileEvents.CopyToClipboard(state.userId.value))
                    },
                )
                Spacer(modifier = Modifier.height(26.dp))
                VerifyUserSection(state, onVerifyClick = { onVerifyClick(state.userId) })
                BlockUserSection(state)
                BlockUserDialogs(state)
                AsyncActionView(
                    async = state.startDmActionState,
                    progressDialog = {
                        AsyncActionViewDefaults.ProgressDialog(
                            progressText = stringResource(CommonStrings.common_starting_chat),
                        )
                    },
                    onSuccess = onOpenDm,
                    errorMessage = { stringResource(R.string.screen_start_chat_error_starting_chat) },
                    onRetry = { state.eventSink(UserProfileEvents.StartDM) },
                    onErrorDismiss = { state.eventSink(UserProfileEvents.ClearStartDMState) },
                    confirmationDialog = { data ->
                        if (data is ConfirmingStartDmWithMatrixUser) {
                            CreateDmConfirmationBottomSheet(
                                matrixUser = data.matrixUser,
                                isUserIdentityUnknown = data.isUserIdentityUnknown,
                                onSendInvite = {
                                    state.eventSink(UserProfileEvents.StartDM)
                                },
                                onDismiss = {
                                    state.eventSink(UserProfileEvents.ClearStartDMState)
                                },
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun VerifyUserSection(
    state: UserProfileState,
    onVerifyClick: () -> Unit,
) {
    if (state.verificationState == UserProfileVerificationState.UNVERIFIED) {
        ListItem(
            headlineContent = { Text(stringResource(CommonStrings.common_verify_user)) },
            leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Lock())),
            onClick = onVerifyClick,
        )
    }
}

/**
 * TG-style row of three actions on your own profile: set photo, edit, settings.
 * No dedicated edit-profile/avatar screens yet, so set-photo and edit both route
 * to [onEdit] (Settings) for now.
 */
@Composable
private fun UserProfileSelfActionsSection(
    onSetPhoto: () -> Unit,
    onEdit: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        MainActionButton(
            title = stringResource(CommonStrings.larpgram_action_set_photo),
            imageVector = CompoundIcons.TakePhoto(),
            onClick = onSetPhoto,
        )
        MainActionButton(
            title = stringResource(CommonStrings.action_edit),
            imageVector = CompoundIcons.Edit(),
            onClick = onEdit,
        )
        MainActionButton(
            title = stringResource(CommonStrings.common_settings),
            imageVector = CompoundIcons.Settings(),
            onClick = onSettings,
        )
    }
}

@PreviewsDayNight
@Composable
internal fun UserProfileViewPreview(
    @PreviewParameter(UserProfileStateProvider::class) state: UserProfileState
) = ElementPreview {
    UserProfileView(
        state = state,
        onShareUser = {},
        goBack = {},
        onOpenDm = {},
        onStartCall = { _, _ -> },
        openAvatarPreview = { _, _ -> },
        onVerifyClick = {},
    )
}
