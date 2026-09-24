/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.topbars

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.messages.impl.MessagesEvent
import io.element.android.features.messages.impl.MessagesState
import io.element.android.features.messages.impl.R
import io.element.android.features.messages.impl.timeline.components.CallMenuItem
import io.element.android.features.roomcall.api.RoomCallState
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.components.glass.TgGlassDefaults
import io.element.android.libraries.designsystem.components.glass.tgGlass
import io.element.android.libraries.designsystem.components.glass.tgTopFade
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.ui.presence.UserPresence
import io.element.android.libraries.matrix.ui.presence.presenceText
import io.element.android.libraries.ui.strings.CommonStrings

/**
 * Правка форка: шапка чата Telegram 12 — стеклянные пилюли поверх обоев (`ChatActivity`,
 * `ChatAvatarContainer` в glass-режиме): круглая «назад», пилюля с аватаром 36dp, именем 17.5 и
 * подзаголовком 13.5, справа пилюля со звонком (в ЛС) и меню ⋮. Отступ сверху — статус-бар.
 */
@Composable
internal fun TgChatHeader(
    state: MessagesState,
    dmPresence: UserPresence?,
    onBackClick: () -> Unit,
    onRoomDetailsClick: () -> Unit,
    onJoinCallClick: (isAudioCall: Boolean) -> Unit,
    onThreadsListClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val subtitle = headerSubtitle(state = state, dmPresence = dmPresence)
    val pillShape = RoundedCornerShape(TgGlassDefaults.inputRadius)
    val iconTint = ElementTheme.colors.iconPrimary
    // Под статус-баром и шапкой — размытая подложка, сходящая на нет книзу, чтобы лента,
    // уходящая под шапку, не спорила с часами и пилюлями (как в TG).
    Row(
        modifier = modifier
            .fillMaxWidth()
            .tgTopFade()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(HEADER_HEIGHT)
                .tgGlass(CircleShape)
                .clickable(onClick = onBackClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = CompoundIcons.ArrowLeft(),
                contentDescription = stringResource(CommonStrings.action_back),
                tint = iconTint,
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Box(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier
                    .height(HEADER_HEIGHT)
                    .tgGlass(pillShape)
                    .clickable(onClick = onRoomDetailsClick)
                    .padding(start = 4.dp, end = 14.dp)
                    .semantics { heading() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(
                    avatarData = state.roomAvatar,
                    avatarType = AvatarType.Room(
                        heroes = state.heroes,
                        isTombstoned = state.isTombstoned,
                    ),
                    forcedAvatarSize = 36.dp,
                )
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(
                        text = state.roomName ?: stringResource(CommonStrings.common_no_room_name),
                        style = ElementTheme.typography.fontBodyLgMedium.copy(fontSize = 17.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
                        color = ElementTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle.text,
                            style = ElementTheme.typography.fontBodySmRegular.copy(fontSize = 13.sp, lineHeight = 16.sp),
                            color = if (subtitle.isAccent) ElementTheme.colors.textActionAccent else ElementTheme.colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.width(6.dp))
        Row(
            modifier = Modifier
                .height(HEADER_HEIGHT)
                .tgGlass(pillShape),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val callState = state.roomCallState
            // Звонок в шапке — как в TG: в ЛС трубка, при идущем звонке «Присоединиться».
            when {
                callState is RoomCallState.OnGoing -> CallMenuItem(
                    roomCallState = callState,
                    onJoinCallClick = onJoinCallClick,
                    modifier = Modifier.padding(start = 4.dp),
                )
                callState is RoomCallState.StandBy && callState.isDM -> IconButton(
                    onClick = { onJoinCallClick(true) },
                    enabled = callState.canStartCall,
                ) {
                    Icon(
                        imageVector = CompoundIcons.VoiceCall(),
                        contentDescription = stringResource(CommonStrings.a11y_start_voice_call),
                        tint = iconTint,
                    )
                }
            }
            HeaderMenu(
                state = state,
                onJoinCallClick = onJoinCallClick,
                onThreadsListClick = onThreadsListClick,
                onRoomDetailsClick = onRoomDetailsClick,
            )
        }
    }
}

@Composable
private fun HeaderMenu(
    state: MessagesState,
    onJoinCallClick: (isAudioCall: Boolean) -> Unit,
    onThreadsListClick: () -> Unit,
    onRoomDetailsClick: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = CompoundIcons.OverflowVertical(),
                contentDescription = stringResource(CommonStrings.action_open_context_menu),
                tint = ElementTheme.colors.iconPrimary,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(12.dp),
            containerColor = ElementTheme.colors.bgCanvasDefault,
        ) {
            val callState = state.roomCallState
            if (callState is RoomCallState.StandBy && callState.canStartCall) {
                HeaderMenuItem(
                    icon = CompoundIcons.VideoCall(),
                    text = stringResource(R.string.larpgram_header_menu_video_call),
                    onClick = {
                        expanded = false
                        onJoinCallClick(false)
                    },
                )
            }
            HeaderMenuItem(
                icon = if (state.isChannelMuted) CompoundIcons.Notifications() else CompoundIcons.NotificationsOff(),
                text = stringResource(
                    if (state.isChannelMuted) R.string.larpgram_header_menu_unmute else R.string.larpgram_header_menu_mute
                ),
                onClick = {
                    expanded = false
                    state.eventSink(MessagesEvent.ToggleChannelMute)
                },
            )
            if (state.threads.hasThreads) {
                HeaderMenuItem(
                    icon = CompoundIcons.Threads(),
                    text = stringResource(R.string.larpgram_header_menu_threads),
                    onClick = {
                        expanded = false
                        onThreadsListClick()
                    },
                )
            }
            HeaderMenuItem(
                icon = CompoundIcons.Info(),
                text = stringResource(R.string.larpgram_header_menu_info),
                onClick = {
                    expanded = false
                    onRoomDetailsClick()
                },
            )
        }
    }
}

@Composable
private fun HeaderMenuItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ElementTheme.colors.iconSecondary,
            )
        },
        text = {
            Text(
                text = text,
                style = ElementTheme.typography.fontBodyLgRegular,
                color = ElementTheme.colors.textPrimary,
            )
        },
        onClick = onClick,
    )
}

internal data class HeaderSubtitle(val text: String, val isAccent: Boolean)

/**
 * Подзаголовок как в Telegram: кто печатает (цветом акцента) важнее всего; дальше канал —
 * подписчики, ЛС — «в сети» / «был(а) …», группа — участники.
 */
@Composable
private fun headerSubtitle(state: MessagesState, dmPresence: UserPresence?): HeaderSubtitle? {
    val typing = state.timelineState.timelineRoomInfo.typingNotificationState
    if (typing.renderTypingNotifications && typing.typingMembers.isNotEmpty()) {
        val names = typing.typingMembers.map { it.disambiguatedDisplayName }
        val text = when {
            state.dmUserId != null -> stringResource(R.string.larpgram_header_typing)
            names.size == 1 -> stringResource(R.string.larpgram_header_typing_one, names[0])
            names.size == 2 -> stringResource(R.string.larpgram_header_typing_two, names[0], names[1])
            else -> pluralStringResource(R.plurals.larpgram_header_typing_many, names.size, names.size)
        }
        return HeaderSubtitle(text, isAccent = true)
    }
    if (state.isChannel) {
        val count = state.channelSubscriberCount ?: return null
        return HeaderSubtitle(pluralStringResource(R.plurals.channel_subscriber_count, count.toInt(), count.toInt()), isAccent = false)
    }
    if (state.dmUserId != null) {
        return presenceText(dmPresence).let { HeaderSubtitle(it.text, isAccent = it.isOnline) }
    }
    val members = state.memberCount ?: return null
    return HeaderSubtitle(pluralStringResource(R.plurals.larpgram_header_members, members.toInt(), members.toInt()), isAccent = false)
}

/** Высота пилюль шапки, как у поля ввода. */
private val HEADER_HEIGHT = 44.dp
