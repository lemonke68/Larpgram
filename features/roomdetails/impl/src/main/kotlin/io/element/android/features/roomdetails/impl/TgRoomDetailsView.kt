/*
 * Правка форка: профиль чата в стиле Telegram 12 (`ProfileActivity`) — ЛС подаётся как профиль
 * собеседника, группа и канал — как их профили. Заменяет элементовский `RoomDetailsView`.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.roomdetails.impl

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.roomcall.api.hasPermissionToJoin
import io.element.android.features.userprofile.api.UserProfileEvent
import io.element.android.features.userprofile.shared.CollapsingAvatar
import io.element.android.features.userprofile.shared.blockuser.BlockUserDialogs
import io.element.android.features.userprofile.shared.tg.TgProfileAction
import io.element.android.features.userprofile.shared.tg.TgProfileActions
import io.element.android.features.userprofile.shared.tg.TgProfileCard
import io.element.android.features.userprofile.shared.tg.TgProfileCardItem
import io.element.android.features.userprofile.shared.tg.TgProfileCardSegment
import io.element.android.features.userprofile.shared.tg.TgProfileDefaults
import io.element.android.features.userprofile.shared.tg.TgProfileHeader
import io.element.android.features.userprofile.shared.tg.TgProfileInfoRow
import io.element.android.features.userprofile.shared.tg.TgProfileMenu
import io.element.android.features.userprofile.shared.tg.TgProfileMenuItem
import io.element.android.features.userprofile.shared.tg.TgProfileTabs
import io.element.android.features.userprofile.shared.tg.TgProfileTopBar
import io.element.android.features.userprofile.shared.tg.rememberTgAvatarExpandState
import io.element.android.libraries.androidutils.system.copyToClipboard
import io.element.android.libraries.architecture.coverage.ExcludeFromCoverage
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.preview.ElementPreviewDark
import io.element.android.libraries.designsystem.preview.ElementPreviewLight
import io.element.android.libraries.designsystem.preview.PreviewWithExtraLargeHeight
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarHost
import io.element.android.libraries.designsystem.utils.snackbar.rememberSnackbarHostState
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.notification.CallIntent
import io.element.android.libraries.matrix.api.room.RoomMember
import io.element.android.libraries.matrix.api.room.RoomNotificationMode
import io.element.android.libraries.matrix.ui.model.getAvatarData
import io.element.android.libraries.matrix.ui.presence.UserPresence
import io.element.android.libraries.matrix.ui.presence.presenceText
import io.element.android.libraries.mediaviewer.api.NoOpProfileSharedMediaSection
import io.element.android.libraries.mediaviewer.api.ProfileSharedMediaSection
import io.element.android.libraries.mediaviewer.api.ProfileSharedMediaTab
import io.element.android.libraries.testtags.TestTags
import io.element.android.libraries.testtags.testTag
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.collections.immutable.toImmutableList

/** Вкладки профиля: «Участники» только у группы, дальше — общие медиа. */
private enum class TgRoomTab { Members, Media, Files, Links, Voice }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TgRoomDetailsView(
    state: RoomDetailsState,
    sharedMedia: ProfileSharedMediaSection,
    presence: UserPresence?,
    goBack: () -> Unit,
    onEditClick: () -> Unit,
    onShareRoom: () -> Unit,
    openRoomMemberList: () -> Unit,
    openRoomNotificationSettings: () -> Unit,
    invitePeople: () -> Unit,
    openAvatarPreview: (name: String, url: String) -> Unit,
    openAdminSettings: () -> Unit,
    onJoinCallClick: (CallIntent) -> Unit,
    onKnockRequestsClick: () -> Unit,
    onSecurityAndPrivacyClick: () -> Unit,
    onMemberClick: (UserId) -> Unit,
    onReportRoomClick: () -> Unit,
    modifier: Modifier = Modifier,
    // Правка форка: профиль «Избранного» в TG — только общие медиа, без участников и действий.
    isSavedMessages: Boolean = false,
    leaveRoomView: @Composable () -> Unit = {},
) {
    val snackbarHostState = rememberSnackbarHostState(snackbarMessage = state.snackbarMessage)
    val dm = state.roomType as? RoomDetailsType.Dm
    val isGroup = dm == null && !state.isChannel && !isSavedMessages
    val tabs = remember(isGroup) {
        if (isGroup) TgRoomTab.entries.toList() else TgRoomTab.entries.filter { it != TgRoomTab.Members }
    }
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val selectedTab = tabs[selectedTabIndex.coerceIn(0, tabs.lastIndex)]
    val listState = rememberLazyListState()
    val showTitle by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val avatarExpand = rememberTgAvatarExpandState()

    val subtitle = if (isSavedMessages) null else roomSubtitle(state = state, presence = presence)

    Scaffold(
        modifier = modifier,
        containerColor = TgProfileDefaults.pageColor,
        topBar = {
            TgProfileTopBar(
                title = state.roomName,
                subtitle = subtitle?.first,
                showTitle = showTitle,
                onBackClick = goBack,
                actions = {
                    // «Избранное» не переименовывают и не меняют ему аватар — как в TG.
                    if (state.canEdit && !isSavedMessages) {
                        IconButton(onClick = onEditClick) {
                            Icon(
                                imageVector = CompoundIcons.Edit(),
                                contentDescription = stringResource(CommonStrings.action_edit),
                                tint = ElementTheme.colors.iconPrimary,
                            )
                        }
                    }
                    TgProfileMenu(
                        items = menuItems(
                            state = state,
                            onShareRoom = onShareRoom,
                            openRoomNotificationSettings = openRoomNotificationSettings,
                            onReportRoomClick = onReportRoomClick,
                        )
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(padding)
                .nestedScroll(avatarExpand.connection),
        ) {
            item(key = "header") {
                leaveRoomView()
                dm?.otherMember?.let { BlockUserDialogsFor(state) }
                TgProfileHeader(
                    title = state.roomName,
                    subtitle = subtitle?.first,
                    subtitleIsAccent = subtitle?.second == true,
                ) {
                    RoomAvatar(state = state, expandFraction = avatarExpand.fraction, openAvatarPreview = openAvatarPreview)
                }
            }
            if (!isSavedMessages) item(key = "actions") {
                TgProfileActions(
                    actions = actions(
                        state = state,
                        goBack = goBack,
                        onShareRoom = onShareRoom,
                        onJoinCallClick = onJoinCallClick,
                    )
                )
            }
            if (!isSavedMessages) infoCard(state = state)
            if (!isSavedMessages) manageCard(
                state = state,
                openRoomMemberList = openRoomMemberList,
                invitePeople = invitePeople,
                onKnockRequestsClick = onKnockRequestsClick,
                openAdminSettings = openAdminSettings,
                onSecurityAndPrivacyClick = onSecurityAndPrivacyClick,
            )
            item(key = "tabs_gap") { Spacer(Modifier.height(TgProfileDefaults.cardGap - 8.dp)) }
            stickyHeader(key = "tabs") {
                TgProfileTabs(
                    titles = tabs.map { stringResource(it.titleRes) },
                    selectedIndex = tabs.indexOf(selectedTab),
                    onSelect = { selectedTabIndex = it },
                )
            }
            when (selectedTab) {
                TgRoomTab.Members -> membersTab(
                    members = state.members,
                    canInvite = state.canInvite,
                    invitePeople = invitePeople,
                    onMemberClick = onMemberClick,
                )
                TgRoomTab.Media -> sharedMedia.content(this, ProfileSharedMediaTab.Media)
                TgRoomTab.Files -> sharedMedia.content(this, ProfileSharedMediaTab.Files)
                TgRoomTab.Links -> sharedMedia.content(this, ProfileSharedMediaTab.Links)
                TgRoomTab.Voice -> sharedMedia.content(this, ProfileSharedMediaTab.Voice)
            }
            item(key = "bottom_inset") {
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

private val TgRoomTab.titleRes: Int
    get() = when (this) {
        TgRoomTab.Members -> CommonStrings.larpgram_profile_tab_members
        TgRoomTab.Media -> CommonStrings.larpgram_profile_tab_media
        TgRoomTab.Files -> CommonStrings.larpgram_profile_tab_files
        TgRoomTab.Links -> CommonStrings.larpgram_profile_tab_links
        TgRoomTab.Voice -> CommonStrings.larpgram_profile_tab_voice
    }

/** Подзаголовок и признак «цветом акцента»: ЛС — присутствие, канал — подписчики, группа — участники. */
@Composable
private fun roomSubtitle(state: RoomDetailsState, presence: UserPresence?): Pair<String, Boolean>? {
    val count = state.memberCount.toInt()
    return when {
        state.roomType is RoomDetailsType.Dm -> presenceText(presence).let { it.text to it.isOnline }
        state.isChannel -> pluralStringResource(R.plurals.screen_room_details_subscriber_count, count, count) to false
        else -> pluralStringResource(R.plurals.screen_room_details_member_count, count, count) to false
    }
}

@Composable
private fun RoomAvatar(
    state: RoomDetailsState,
    expandFraction: Float,
    openAvatarPreview: (name: String, url: String) -> Unit,
) {
    val dm = state.roomType as? RoomDetailsType.Dm
    if (dm != null) {
        val member = dm.otherMember
        CollapsingAvatar(
            avatarData = AvatarData(member.userId.value, state.roomName, member.avatarUrl, AvatarSize.UserHeader),
            userName = state.roomName,
            expandFraction = expandFraction,
            onClick = { member.avatarUrl?.let { openAvatarPreview(state.roomName, it) } },
            modifier = Modifier.testTag(TestTags.roomDetailAvatar),
        )
    } else {
        CollapsingAvatar(
            avatarData = AvatarData(state.roomId.value, state.roomName, state.roomAvatarUrl, AvatarSize.RoomDetailsHeader),
            userName = state.roomName,
            expandFraction = expandFraction,
            onClick = { state.roomAvatarUrl?.let { openAvatarPreview(state.roomName, it) } },
            avatarType = AvatarType.Room(
                heroes = state.heroes.map { it.getAvatarData(size = AvatarSize.RoomDetailsHeader) }.toImmutableList(),
                isTombstoned = state.isTombstoned,
            ),
            modifier = Modifier.testTag(TestTags.roomDetailAvatar),
        )
    }
}

/** Плитки как в TG: ЛС — Чат/Звук/Звонок/Видео, группа — Чат/Звук/Видео/Покинуть, канал — Звук/Поделиться/Покинуть. */
@Composable
private fun actions(
    state: RoomDetailsState,
    goBack: () -> Unit,
    onShareRoom: () -> Unit,
    onJoinCallClick: (CallIntent) -> Unit,
): List<TgProfileAction> {
    val isDm = state.roomType is RoomDetailsType.Dm
    val isMuted = state.roomNotificationSettings?.mode == RoomNotificationMode.MUTE
    val canCall = state.roomCallState.hasPermissionToJoin()
    val chat = TgProfileAction(stringResource(CommonStrings.larpgram_profile_action_chat), CompoundIcons.ChatSolid(), goBack)
    val sound = state.roomNotificationSettings?.let {
        if (isMuted) {
            TgProfileAction(stringResource(CommonStrings.larpgram_profile_action_unmute), CompoundIcons.NotificationsOffSolid()) {
                state.eventSink(RoomDetailsEvent.UnmuteNotification)
            }
        } else {
            TgProfileAction(stringResource(CommonStrings.larpgram_profile_action_mute), CompoundIcons.NotificationsSolid()) {
                state.eventSink(RoomDetailsEvent.MuteNotification)
            }
        }
    }
    val call = TgProfileAction(stringResource(CommonStrings.larpgram_profile_action_call), CompoundIcons.VoiceCallSolid()) {
        onJoinCallClick(CallIntent.AUDIO)
    }
    val video = TgProfileAction(stringResource(CommonStrings.common_video), CompoundIcons.VideoCallSolid()) {
        onJoinCallClick(CallIntent.VIDEO)
    }
    val share = TgProfileAction(stringResource(CommonStrings.action_share), CompoundIcons.ShareAndroid(), onShareRoom)
    val leave = TgProfileAction(stringResource(CommonStrings.larpgram_profile_action_leave), CompoundIcons.Leave()) {
        state.eventSink(RoomDetailsEvent.LeaveRoom(needsConfirmation = true))
    }
    return when {
        isDm -> listOfNotNull(chat, sound, call.takeIf { canCall }, video.takeIf { canCall })
        state.isChannel -> listOfNotNull(sound, share, leave)
        else -> listOfNotNull(chat, sound, video.takeIf { canCall }, leave)
    }
}

@Composable
private fun menuItems(
    state: RoomDetailsState,
    onShareRoom: () -> Unit,
    openRoomNotificationSettings: () -> Unit,
    onReportRoomClick: () -> Unit,
): List<TgProfileMenuItem> {
    val context = LocalContext.current
    val copiedMessage = stringResource(CommonStrings.common_copied_to_clipboard)
    val dmMemberState = state.dmOtherMemberDetailsState
    return buildList {
        if (state.roomNotificationSettings != null) {
            add(
                TgProfileMenuItem(
                    title = stringResource(R.string.screen_room_details_notification_title),
                    icon = CompoundIcons.Notifications(),
                    onClick = openRoomNotificationSettings,
                )
            )
        }
        if (state.roomType is RoomDetailsType.Room) {
            add(TgProfileMenuItem(stringResource(CommonStrings.action_share), CompoundIcons.ShareAndroid(), onShareRoom))
        }
        if (dmMemberState != null) {
            val isBlocked = dmMemberState.isBlocked.dataOrNull() == true
            add(
                TgProfileMenuItem(
                    title = stringResource(
                        if (isBlocked) CommonStrings.larpgram_profile_menu_unblock else CommonStrings.larpgram_profile_menu_block
                    ),
                    icon = CompoundIcons.Block(),
                    onClick = {
                        dmMemberState.eventSink(
                            if (isBlocked) UserProfileEvent.UnblockUser(needsConfirmation = true) else UserProfileEvent.BlockUser(needsConfirmation = true)
                        )
                    },
                )
            )
        }
        if (state.canReportRoom) {
            add(TgProfileMenuItem(stringResource(CommonStrings.action_report_room), CompoundIcons.ChatProblem(), onReportRoomClick))
        }
        if (state.showDebugInfo) {
            add(
                TgProfileMenuItem(
                    title = stringResource(CommonStrings.larpgram_profile_copy_room_id),
                    icon = CompoundIcons.Code(),
                    onClick = { context.copyToClipboard(text = state.roomId.value, toastMessage = copiedMessage) },
                )
            )
        }
        val leaveTitle = when {
            state.roomType is RoomDetailsType.Dm -> R.string.screen_room_details_delete_chat
            state.isChannel -> R.string.screen_room_details_leave_channel
            else -> R.string.screen_room_details_leave_group
        }
        add(
            TgProfileMenuItem(
                title = stringResource(leaveTitle),
                icon = if (state.roomType is RoomDetailsType.Dm) CompoundIcons.Delete() else CompoundIcons.Leave(),
                destructive = true,
                onClick = { state.eventSink(RoomDetailsEvent.LeaveRoom(needsConfirmation = true)) },
            )
        )
    }
}

@Composable
private fun BlockUserDialogsFor(state: RoomDetailsState) {
    state.dmOtherMemberDetailsState?.let { BlockUserDialogs(it) }
}

/** Инфо-карточка: у ЛС — @имя, у группы и канала — описание и ссылка. Тап копирует. */
private fun LazyListScope.infoCard(state: RoomDetailsState) {
    val dm = state.roomType as? RoomDetailsType.Dm
    val topic = (state.roomTopic as? RoomTopicState.ExistingTopic)?.topic
    val alias = state.roomAlias?.value
    if (dm == null && topic == null && alias == null) return
    item(key = "info") {
        Spacer(Modifier.height(TgProfileDefaults.cardGap))
        TgProfileCard {
            if (dm != null) {
                // «О себе» собеседника — публичное поле профиля (см. PublicBio).
                state.dmOtherMemberDetailsState?.about?.takeIf { it.isNotBlank() }?.let { about ->
                    TgProfileInfoRow(
                        value = about,
                        label = stringResource(CommonStrings.larpgram_profile_about_label),
                        onLongClick = { state.eventSink(RoomDetailsEvent.CopyToClipboard(about)) },
                    )
                }
                val userId = dm.otherMember.userId.value
                TgProfileInfoRow(
                    value = userId.substringBefore(":"),
                    label = stringResource(CommonStrings.common_username),
                    onClick = { state.eventSink(RoomDetailsEvent.CopyToClipboard(userId)) },
                )
            }
            if (topic != null) {
                TgProfileInfoRow(
                    value = topic,
                    label = stringResource(CommonStrings.larpgram_profile_description_label),
                    onLongClick = { state.eventSink(RoomDetailsEvent.CopyToClipboard(topic)) },
                )
            }
            if (alias != null) {
                TgProfileInfoRow(
                    value = alias,
                    label = stringResource(CommonStrings.larpgram_profile_link_label),
                    onClick = { state.eventSink(RoomDetailsEvent.CopyToClipboard(alias)) },
                )
            }
        }
    }
}

/** Карточка управления (группа, канал): подписчики, заявки, роли, доступ. */
private fun LazyListScope.manageCard(
    state: RoomDetailsState,
    openRoomMemberList: () -> Unit,
    invitePeople: () -> Unit,
    onKnockRequestsClick: () -> Unit,
    openAdminSettings: () -> Unit,
    onSecurityAndPrivacyClick: () -> Unit,
) {
    if (state.roomType !is RoomDetailsType.Room) return
    val showSubscribers = state.isChannel
    val showInvite = state.isChannel && state.canInvite
    val hasRows = showSubscribers || showInvite || state.canShowKnockRequests ||
        state.displayRolesAndPermissionsSettings || state.canShowSecurityAndPrivacy
    if (!hasRows) return
    item(key = "manage") {
        Spacer(Modifier.height(TgProfileDefaults.cardGap))
        TgProfileCard {
            if (showSubscribers) {
                TgProfileCardItem(
                    title = stringResource(R.string.screen_room_details_subscribers),
                    icon = CompoundIcons.User(),
                    value = state.memberCount.toString(),
                    onClick = openRoomMemberList,
                )
            }
            if (showInvite) {
                TgProfileCardItem(
                    title = stringResource(CommonStrings.larpgram_profile_add_subscribers),
                    icon = CompoundIcons.UserAdd(),
                    accent = true,
                    onClick = invitePeople,
                )
            }
            if (state.canShowKnockRequests) {
                TgProfileCardItem(
                    title = stringResource(R.string.screen_room_details_requests_to_join_title),
                    icon = CompoundIcons.AskToJoin(),
                    value = state.knockRequestsCount?.takeIf { it > 0 }?.toString(),
                    onClick = onKnockRequestsClick,
                )
            }
            if (state.displayRolesAndPermissionsSettings) {
                TgProfileCardItem(
                    title = stringResource(R.string.screen_room_details_roles_and_permissions),
                    icon = CompoundIcons.Admin(),
                    onClick = openAdminSettings,
                )
            }
            if (state.canShowSecurityAndPrivacy) {
                TgProfileCardItem(
                    title = stringResource(R.string.screen_room_details_security_and_privacy_title),
                    icon = CompoundIcons.Lock(),
                    onClick = onSecurityAndPrivacyClick,
                )
            }
        }
    }
}

/** Вкладка «Участники» группы: «Добавить участников» и список, сначала владельцы и админы. */
private fun LazyListScope.membersTab(
    members: List<RoomMember>,
    canInvite: Boolean,
    invitePeople: () -> Unit,
    onMemberClick: (UserId) -> Unit,
) {
    if (canInvite) {
        item(key = "members_invite") {
            TgProfileCardSegment(isFirst = true, isLast = members.isEmpty()) {
                TgProfileCardItem(
                    title = stringResource(CommonStrings.larpgram_profile_add_members),
                    icon = CompoundIcons.UserAdd(),
                    accent = true,
                    onClick = invitePeople,
                )
            }
        }
    }
    members.forEachIndexed { index, member ->
        item(key = "member_${member.userId.value}", contentType = "member") {
            TgProfileCardSegment(isFirst = index == 0 && !canInvite, isLast = index == members.lastIndex) {
                MemberRow(member = member, onClick = { onMemberClick(member.userId) })
            }
        }
    }
}

@Composable
private fun MemberRow(member: RoomMember, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            avatarData = member.getAvatarData(size = AvatarSize.ProfileMember),
            avatarType = AvatarType.User,
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = member.displayNameOrDefault,
                style = ElementTheme.typography.fontBodyLgMedium,
                color = ElementTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = member.userId.value.substringBefore(":"),
                style = ElementTheme.typography.fontBodyMdRegular,
                color = ElementTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val role = when (member.role) {
            is RoomMember.Role.Owner -> CommonStrings.larpgram_profile_role_owner
            RoomMember.Role.Admin -> CommonStrings.larpgram_profile_role_admin
            RoomMember.Role.Moderator -> CommonStrings.larpgram_profile_role_moderator
            RoomMember.Role.User -> null
        }
        if (role != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(role),
                style = ElementTheme.typography.fontBodyMdRegular,
                color = ElementTheme.colors.textSecondary,
            )
        }
    }
}

@PreviewWithExtraLargeHeight
@Composable
internal fun TgRoomDetailsPreview(@PreviewParameter(RoomDetailsStatePreviewParam::class) state: RoomDetailsState) =
    ElementPreviewLight { ContentToPreview(state) }

@PreviewWithExtraLargeHeight
@Composable
internal fun TgRoomDetailsDarkPreview(@PreviewParameter(RoomDetailsStatePreviewParam::class) state: RoomDetailsState) =
    ElementPreviewDark { ContentToPreview(state) }

@ExcludeFromCoverage
@Composable
private fun ContentToPreview(state: RoomDetailsState) {
    TgRoomDetailsView(
        state = state,
        sharedMedia = NoOpProfileSharedMediaSection,
        presence = UserPresence(isOnline = true, lastActiveAtMillis = null),
        goBack = {},
        onEditClick = {},
        onShareRoom = {},
        openRoomMemberList = {},
        openRoomNotificationSettings = {},
        invitePeople = {},
        openAvatarPreview = { _, _ -> },
        openAdminSettings = {},
        onJoinCallClick = {},
        onKnockRequestsClick = {},
        onSecurityAndPrivacyClick = {},
        onMemberClick = {},
        onReportRoomClick = {},
    )
}
