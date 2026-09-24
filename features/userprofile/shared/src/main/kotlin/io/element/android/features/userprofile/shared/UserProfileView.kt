/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.userprofile.shared

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.startchat.api.ConfirmingStartDmWithMatrixUser
import io.element.android.features.userprofile.api.UserProfileEvent
import io.element.android.features.userprofile.api.UserProfileState
import io.element.android.features.userprofile.api.UserProfileVerificationState
import io.element.android.features.userprofile.shared.blockuser.BlockUserDialogs
import io.element.android.features.userprofile.shared.tg.TgProfileAction
import io.element.android.features.userprofile.shared.tg.TgProfileActions
import io.element.android.features.userprofile.shared.tg.TgProfileDefaults
import io.element.android.features.userprofile.shared.tg.TgProfileHeader
import io.element.android.features.userprofile.shared.tg.TgProfileMenu
import io.element.android.features.userprofile.shared.tg.TgProfileMenuItem
import io.element.android.features.userprofile.shared.tg.TgProfileTopBar
import io.element.android.features.userprofile.shared.tg.rememberTgAvatarExpandState
import io.element.android.libraries.designsystem.components.async.AsyncActionView
import io.element.android.libraries.designsystem.components.async.AsyncActionViewDefaults
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.ButtonSize
import io.element.android.libraries.designsystem.theme.components.OutlinedButton
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarHost
import io.element.android.libraries.designsystem.utils.snackbar.rememberSnackbarHostState
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.notification.CallIntent
import io.element.android.libraries.matrix.ui.components.CreateDmConfirmationBottomSheet
import io.element.android.libraries.matrix.ui.presence.UserPresence
import io.element.android.libraries.matrix.ui.presence.presenceText
import io.element.android.libraries.testtags.TestTags
import io.element.android.libraries.testtags.testTag
import io.element.android.libraries.ui.strings.CommonStrings

/**
 * Правка форка: профиль человека в стиле Telegram 12 (`ProfileActivity`). Чужой профиль — шапка с
 * присутствием, плитки Чат / Звонок / Видео, инфо-карточка, остальное в меню ⋮. Свой профиль
 * (вкладка нижней навигации) — плитки Выбрать фото / Изменить / Настройки, без «назад».
 */
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
    presence: UserPresence? = null,
    // Свой профиль открыт вкладкой нижней навигации — возвращаться некуда. Из списка участников
    // группы свой профиль открывается поверх, там «назад» нужна, а плитки своего профиля — нет.
    isNavigationTab: Boolean = state.isCurrentUser,
) {
    val snackbarHostState = rememberSnackbarHostState(snackbarMessage = state.snackbarMessage)
    val avatarExpand = rememberTgAvatarExpandState()
    val scrollState = rememberScrollState()
    val headerHeightPx = with(LocalDensity.current) { HEADER_COLLAPSE_OFFSET.toPx() }
    val showTitle by remember { derivedStateOf { scrollState.value > headerHeightPx } }
    val title = state.userName ?: state.userId.value
    val subtitle = if (state.isCurrentUser) null else presenceText(presence)
    Scaffold(
        modifier = modifier,
        containerColor = TgProfileDefaults.pageColor,
        topBar = {
            TgProfileTopBar(
                title = title,
                subtitle = subtitle?.text,
                showTitle = showTitle,
                onBackClick = goBack.takeIf { !isNavigationTab },
                actions = {
                    if (!state.isCurrentUser) {
                        TgProfileMenu(items = otherUserMenuItems(state, onShareUser, onVerifyClick))
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
                .nestedScroll(avatarExpand.connection)
                .verticalScroll(scrollState)
        ) {
            TgProfileHeader(
                title = title,
                subtitle = subtitle?.text,
                subtitleIsAccent = subtitle?.isOnline == true,
            ) {
                CollapsingAvatar(
                    avatarData = AvatarData(state.userId.value, state.userName, state.avatarUrl, AvatarSize.UserHeader),
                    userName = state.userName,
                    expandFraction = avatarExpand.fraction,
                    onClick = { state.avatarUrl?.let { openAvatarPreview(title, it) } },
                    modifier = Modifier.testTag(TestTags.memberDetailAvatar),
                )
            }
            if (state.verificationState == UserProfileVerificationState.VERIFICATION_VIOLATION) {
                VerificationViolation(state)
            }
            if (state.isCurrentUser && isNavigationTab) {
                TgProfileActions(
                    actions = listOf(
                        TgProfileAction(stringResource(CommonStrings.larpgram_action_set_photo), CompoundIcons.TakePhotoSolid(), onEditProfile),
                        TgProfileAction(stringResource(CommonStrings.larpgram_action_edit_profile), CompoundIcons.EditSolid(), onEditProfile),
                        TgProfileAction(stringResource(CommonStrings.common_settings), CompoundIcons.SettingsSolid(), onOpenSettings),
                    )
                )
            } else if (!state.isCurrentUser) {
                TgProfileActions(
                    actions = listOfNotNull(
                        TgProfileAction(stringResource(CommonStrings.larpgram_profile_action_chat), CompoundIcons.ChatSolid()) {
                            state.eventSink(UserProfileEvent.StartDM)
                        },
                        TgProfileAction(stringResource(CommonStrings.larpgram_profile_action_call), CompoundIcons.VoiceCallSolid()) {
                            state.dmRoomId?.let { onStartCall(it, CallIntent.AUDIO) }
                        }.takeIf { state.canCall },
                        TgProfileAction(stringResource(CommonStrings.common_video), CompoundIcons.VideoCallSolid()) {
                            state.dmRoomId?.let { onStartCall(it, CallIntent.VIDEO) }
                        }.takeIf { state.canCall },
                    )
                )
            }
            Spacer(modifier = Modifier.height(TgProfileDefaults.cardGap))
            UserProfileInfoCard(
                userId = state.userId,
                about = state.about,
                onHandleClick = {
                    state.eventSink(UserProfileEvent.CopyToClipboard(state.userId.value))
                },
            )
            Spacer(modifier = Modifier.height(24.dp))
            if (!state.isCurrentUser) {
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
                    onRetry = { state.eventSink(UserProfileEvent.StartDM) },
                    onErrorDismiss = { state.eventSink(UserProfileEvent.ClearStartDMState) },
                    confirmationDialog = { data ->
                        if (data is ConfirmingStartDmWithMatrixUser) {
                            CreateDmConfirmationBottomSheet(
                                matrixUser = data.matrixUser,
                                isUserIdentityUnknown = data.isUserIdentityUnknown,
                                onSendInvite = {
                                    state.eventSink(UserProfileEvent.StartDM)
                                },
                                onDismiss = {
                                    state.eventSink(UserProfileEvent.ClearStartDMState)
                                },
                            )
                        }
                    },
                )
            }
        }
    }
}

/** Когда шапка уехала вверх на столько, на панели проявляются имя и статус. */
private val HEADER_COLLAPSE_OFFSET = 150.dp

@Composable
private fun otherUserMenuItems(
    state: UserProfileState,
    onShareUser: () -> Unit,
    onVerifyClick: (UserId) -> Unit,
): List<TgProfileMenuItem> {
    val isBlocked = state.isBlocked.dataOrNull() == true
    return buildList {
        add(TgProfileMenuItem(stringResource(CommonStrings.action_share), CompoundIcons.ShareAndroid(), onShareUser))
        // Подтверждение личности — возможность Matrix, в TG её нет: убрана с экрана в меню.
        if (state.verificationState == UserProfileVerificationState.UNVERIFIED) {
            add(TgProfileMenuItem(stringResource(CommonStrings.common_verify_user), CompoundIcons.Lock(), onClick = { onVerifyClick(state.userId) }))
        }
        add(
            TgProfileMenuItem(
                title = stringResource(if (isBlocked) CommonStrings.larpgram_profile_menu_unblock else CommonStrings.larpgram_profile_menu_block),
                icon = CompoundIcons.Block(),
                destructive = !isBlocked,
                onClick = {
                    state.eventSink(
                        if (isBlocked) UserProfileEvent.UnblockUser(needsConfirmation = true) else UserProfileEvent.BlockUser(needsConfirmation = true)
                    )
                },
            )
        )
    }
}

/** Ключи собеседника сменились: предупреждение Element оставляем, это про безопасность. */
@Composable
private fun VerificationViolation(state: UserProfileState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
    ) {
        Text(
            text = stringResource(CommonStrings.crypto_identity_change_profile_pin_violation, state.userName ?: state.userId.value),
            color = ElementTheme.colors.textCriticalPrimary,
            style = ElementTheme.typography.fontBodyMdMedium,
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            size = ButtonSize.MediumLowPadding,
            text = stringResource(CommonStrings.crypto_identity_change_withdraw_verification_action),
            onClick = { state.eventSink(UserProfileEvent.WithdrawVerification) },
        )
    }
}

@PreviewsDayNight
@Composable
internal fun UserProfileViewPreview(
    @PreviewParameter(UserProfileStatePreviewParam::class) state: UserProfileState
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
