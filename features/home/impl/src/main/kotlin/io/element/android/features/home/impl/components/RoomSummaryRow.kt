/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.Absolute.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.home.impl.R
import io.element.android.features.home.impl.model.ChatType
import io.element.android.features.home.impl.model.LatestEvent
import io.element.android.features.home.impl.model.RoomListRoomSummary
import io.element.android.features.home.impl.model.RoomListRoomSummaryProvider
import io.element.android.features.home.impl.model.RoomSummaryDisplayType
import io.element.android.features.home.impl.roomlist.RoomListEvent
import io.element.android.libraries.core.extensions.orEmpty
import io.element.android.libraries.core.extensions.toSafeLength
import io.element.android.libraries.designsystem.atomic.atoms.UnreadIndicatorAtom
import io.element.android.libraries.designsystem.atomic.molecules.InviteButtonsRowMolecule
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.components.messages.MessageDeliveryState
import io.element.android.libraries.designsystem.components.messages.MessageDeliveryTicks
import io.element.android.libraries.designsystem.modifiers.onKeyboardContextMenuAction
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.roomListRoomMessage
import io.element.android.libraries.designsystem.theme.roomListRoomMessageDate
import io.element.android.libraries.designsystem.theme.roomListRoomName
import io.element.android.libraries.designsystem.theme.unreadIndicator
import io.element.android.libraries.eventformatter.api.LATEST_EVENT_THUMBNAIL_ID
import io.element.android.libraries.matrix.api.media.MediaSource
import io.element.android.libraries.matrix.api.notification.CallIntent
import io.element.android.libraries.matrix.api.room.RoomNotificationMode
import io.element.android.libraries.matrix.api.user.DisplayedStatus
import io.element.android.libraries.matrix.ui.components.DisplayNameWithStatus
import io.element.android.libraries.matrix.ui.components.InviteSenderView
import io.element.android.libraries.matrix.ui.media.MediaRequestData
import io.element.android.libraries.matrix.ui.model.InviteSender
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import timber.log.Timber

// Правка форка. Замеры сперва брались с концепта 2023 года, а 2026-08-16 переснялись по
// живому клиенту (design/tg-ref/current/chats_list_dark.png): высоту строки концепт угадал,
// а аватар на деле крупнее и придвинут к краю. Текст в итоге начинается с 86dp.
internal val minHeight = 76.dp
private val ROW_HORIZONTAL_PADDING = 16.dp
private val AVATAR_TO_TEXT_GAP = 10.dp

// Правка форка: превью последнего сообщения в одну строку. Element держал ровно две
// (minLines = maxLines = 2), из-за чего строка списка не могла быть ниже ~84dp.
private const val PREVIEW_MAX_LINES = 1

// Правка форка: мини-превью медиа, замер по design/tg-ref/current/chats_list_dark.png.
private val PREVIEW_THUMBNAIL_SIZE = 18.dp
private val PREVIEW_THUMBNAIL_CORNER = 4.dp

// Просить у сервера ровно 18dp бессмысленно: на экране 3x это 54px, а миниатюры кэшируются
// по размеру запроса. Берём один размер с запасом на все плотности.
private const val PREVIEW_THUMBNAIL_REQUEST_PX = 64L

@Composable
internal fun RoomSummaryRow(
    room: RoomListRoomSummary,
    hideInviteAvatars: Boolean,
    isInviteSeen: Boolean,
    onClick: (RoomListRoomSummary) -> Unit,
    modifier: Modifier = Modifier,
    showUnreadCount: Boolean = false,
    eventSink: (RoomListEvent) -> Unit,
) {
    Box(modifier = modifier) {
        when (room.displayType) {
            RoomSummaryDisplayType.PLACEHOLDER -> {
                RoomSummaryPlaceholderRow()
            }
            RoomSummaryDisplayType.INVITE -> {
                RoomSummaryScaffoldRow(
                    room = room,
                    hideAvatarImage = hideInviteAvatars,
                    onClick = onClick,
                    onLongClick = {
                        Timber.d("Long click on invite room")
                    },
                ) {
                    InviteNameAndIndicatorRow(name = room.name, isInviteSeen = isInviteSeen)
                    InviteSubtitle(isDm = room.isDm, inviteSender = room.inviteSender)
                    if (!room.isDm && room.inviteSender != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        InviteSenderView(
                            modifier = Modifier.fillMaxWidth(),
                            inviteSender = room.inviteSender,
                            hideAvatarImage = hideInviteAvatars
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    InviteButtonsRowMolecule(
                        onAcceptClick = {
                            eventSink(RoomListEvent.AcceptInvite(room))
                        },
                        onDeclineClick = {
                            eventSink(RoomListEvent.ShowDeclineInviteMenu(room))
                        }
                    )
                }
            }
            RoomSummaryDisplayType.ROOM -> {
                RoomSummaryScaffoldRow(
                    room = room,
                    onClick = onClick,
                    onLongClick = {
                        eventSink(RoomListEvent.ShowContextMenu(room))
                    },
                ) {
                    NameAndTimestampRow(
                        name = room.name,
                        timestamp = room.timestamp,
                        isHighlighted = room.isHighlighted,
                        dmUserStatus = room.dmUserStatus,
                        chatType = room.chatType,
                        deliveryState = room.latestEvent.deliveryState(),
                        isMuted = room.userDefinedNotificationMode == RoomNotificationMode.MUTE,
                    )
                    MessagePreviewAndIndicatorRow(room = room, showUnreadCount = showUnreadCount)
                }
            }
            RoomSummaryDisplayType.KNOCKED -> {
                RoomSummaryScaffoldRow(
                    room = room,
                    onClick = onClick,
                    onLongClick = {
                        Timber.d("Long click on knocked room")
                    },
                ) {
                    NameAndTimestampRow(
                        name = room.name,
                        timestamp = null,
                        isHighlighted = room.isHighlighted,
                        dmUserStatus = null,
                    )
                    if (room.canonicalAlias != null) {
                        Text(
                            text = room.canonicalAlias.value,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = ElementTheme.typography.fontBodyMdRegular,
                            color = ElementTheme.colors.textSecondary,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Text(
                        text = stringResource(id = R.string.screen_roomlist_knock_event_sent_description),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = ElementTheme.typography.fontBodyMdRegular,
                        color = ElementTheme.colors.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun RoomSummaryScaffoldRow(
    room: RoomListRoomSummary,
    onClick: (RoomListRoomSummary) -> Unit,
    onLongClick: (RoomListRoomSummary) -> Unit,
    modifier: Modifier = Modifier,
    hideAvatarImage: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val clickModifier = Modifier
        .combinedClickable(
            onClick = { onClick(room) },
            onLongClick = { onLongClick(room) },
            onLongClickLabel = stringResource(CommonStrings.action_open_context_menu),
            indication = ripple(),
            interactionSource = remember { MutableInteractionSource() }
        )
        .onKeyboardContextMenuAction { onLongClick(room) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .then(clickModifier)
            .padding(horizontal = ROW_HORIZONTAL_PADDING, vertical = 10.dp)
            .height(IntrinsicSize.Min),
    ) {
        Avatar(
            avatarData = room.avatarData,
            avatarType = if (room.isSpace) {
                AvatarType.Space(isTombstoned = room.isTombstoned)
            } else {
                AvatarType.Room(
                    heroes = room.heroes,
                    isTombstoned = room.isTombstoned,
                )
            },
            hideImage = hideAvatarImage,
        )
        Spacer(modifier = Modifier.width(AVATAR_TO_TEXT_GAP))
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content,
        )
    }
}

@Composable
private fun NameAndTimestampRow(
    name: String?,
    timestamp: String?,
    isHighlighted: Boolean,
    dmUserStatus: DisplayedStatus?,
    modifier: Modifier = Modifier,
    chatType: ChatType = ChatType.Group,
    deliveryState: MessageDeliveryState? = null,
    isMuted: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = spacedBy(16.dp)
    ) {
        val displayName = name?.toSafeLength(ellipsize = true) ?: stringResource(id = CommonStrings.common_no_room_name)
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Правка форка (роумлесс): маленькая иконка типа перед именем. Канал — вещание
            // (Public), группа — Group; в ЛС метки нет (аватар собеседника и так говорит сам).
            ChatTypeIcon(chatType)
            DisplayNameWithStatus(
                name = displayName,
                status = dmUserStatus,
                modifier = Modifier.weight(1f, fill = false),
                style = ElementTheme.typography.fontBodyLgMedium,
                nameColor = ElementTheme.colors.roomListRoomName,
                nameFontStyle = FontStyle.Italic.takeIf { name == null },
            )
            // Правка форка: значок «без звука» стоит сразу после имени, а не справа у бейджа.
            // Проверено по живому клиенту, у Element он был в правом блоке.
            if (isMuted) {
                NotificationOffIndicatorAtom()
            }
        }
        Row(
            horizontalArrangement = spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Правка форка: галочки перед временем, как в Telegram. В личном чате префикса
            // «Вы:» нет, и своё сообщение отличается только ими.
            if (deliveryState != null) {
                MessageDeliveryTicks(state = deliveryState)
            }
            // Timestamp
            Text(
                text = timestamp ?: "",
                style = ElementTheme.typography.fontBodySmMedium,
                color = if (isHighlighted) {
                    ElementTheme.colors.unreadIndicator
                } else {
                    ElementTheme.colors.roomListRoomMessageDate
                },
            )
        }
    }
}

@Composable
private fun InviteSubtitle(
    isDm: Boolean,
    inviteSender: InviteSender?,
    modifier: Modifier = Modifier
) {
    val subtitle = if (isDm) {
        inviteSender?.userId?.value
    } else {
        null
    }
    if (subtitle != null) {
        Text(
            modifier = modifier.clipToBounds(),
            text = subtitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = ElementTheme.typography.fontBodyMdRegular,
            color = ElementTheme.colors.roomListRoomMessage,
        )
    }
}

@Composable
private fun MessagePreviewAndIndicatorRow(
    room: RoomListRoomSummary,
    showUnreadCount: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
    ) {
        if (room.isTombstoned) {
            Text(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.screen_roomlist_tombstoned_room_description),
                color = ElementTheme.colors.roomListRoomMessage,
                style = ElementTheme.typography.fontBodyMdRegular,
                maxLines = PREVIEW_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            if (room.latestEvent is LatestEvent.Error) {
                Icon(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(16.dp),
                    imageVector = CompoundIcons.ErrorSolid(),
                    // The last message contains the error.
                    contentDescription = null,
                    tint = ElementTheme.colors.iconCriticalPrimary,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    modifier = Modifier.weight(1f),
                    text = stringResource(CommonStrings.common_message_failed_to_send),
                    color = ElementTheme.colors.textCriticalPrimary,
                    style = ElementTheme.typography.fontBodyMdRegular,
                    maxLines = PREVIEW_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                // Правка форка: часиков перед текстом больше нет, отправку показывают галочки
                // рядом со временем, а два индикатора одного и того же это шум.
                val messagePreview = room.latestEvent.content()
                val annotatedMessagePreview = messagePreview as? AnnotatedString ?: AnnotatedString(text = messagePreview.orEmpty().toString())
                Text(
                    modifier = Modifier
                        .weight(1f)
                        .clipToBounds(),
                    text = annotatedMessagePreview,
                    color = ElementTheme.colors.roomListRoomMessage,
                    style = ElementTheme.typography.fontBodyMdRegular,
                    maxLines = PREVIEW_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                    inlineContent = latestEventThumbnailContent(room.latestEvent.thumbnail()),
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        // Call and unread
        Row(
            modifier = Modifier
                .height(16.dp)
                // Used to force this line to be read aloud earlier than the latest event when using Talkback
                .zIndex(-1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tint = if (room.isHighlighted) ElementTheme.colors.unreadIndicator else ElementTheme.colors.iconQuaternary
            if (room.hasRoomCall) {
                OnGoingCallIcon(
                    color = tint,
                    isAudio = room.activeCallIntent == CallIntent.AUDIO
                )
            }
            // Правка форка: значок «без звука» переехал к имени, см. NameAndTimestampRow.
            if (room.numberOfUnreadMentions > 0) {
                MentionIndicatorAtom()
            }
            if (room.hasNewContent) {
                val contentDescription = stringResource(CommonStrings.a11y_notifications_new_messages)
                val count = if (showUnreadCount) {
                    if (room.userDefinedNotificationMode == RoomNotificationMode.MUTE) {
                        room.numberOfUnreadMessages
                    } else {
                        room.numberOfUnreadNotifications
                    }
                } else {
                    null
                }
                UnreadIndicatorAtom(
                    color = tint,
                    count = count,
                    contentDescription = contentDescription,
                )
            }
            // Правка форка (роумлесс): пин показываем, когда нет бейджа непрочитанных (как в TG).
            if (room.isPinned && !room.hasNewContent) {
                PinIndicatorAtom(tint = ElementTheme.colors.iconQuaternary)
            }
        }
    }
}

/**
 * Правка форка: мини-превью медиа внутри текста последнего сообщения.
 *
 * Замер по живому клиенту: квадрат 18dp со скруглением 4. Место под него оставляет
 * форматтер (`withThumbnailSlot`), поэтому картинка встаёт после имени отправителя и не
 * ломает многоточие. Нет картинки — нет и слота, `inlineContent` тогда пустой.
 *
 * Размер задаётся в sp, а не в dp: это метка внутри текста, и при увеличенном системном
 * шрифте она должна расти вместе со строкой, иначе поедет базовая линия.
 */
@Composable
private fun latestEventThumbnailContent(thumbnail: MediaSource?): ImmutableMap<String, InlineTextContent> {
    if (thumbnail == null) return persistentMapOf()
    val size = with(LocalDensity.current) { PREVIEW_THUMBNAIL_SIZE.toSp() }
    return persistentMapOf(
        LATEST_EVENT_THUMBNAIL_ID to InlineTextContent(
            placeholder = Placeholder(
                width = size,
                height = size,
                placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
            ),
        ) {
            AsyncImage(
                model = MediaRequestData(
                    source = thumbnail,
                    kind = MediaRequestData.Kind.Thumbnail(PREVIEW_THUMBNAIL_REQUEST_PX),
                ),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(PREVIEW_THUMBNAIL_CORNER)),
            )
        }
    )
}

@Composable
private fun InviteNameAndIndicatorRow(
    name: String?,
    isInviteSeen: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier
                .weight(1f)
                .clipToBounds(),
            style = ElementTheme.typography.fontBodyLgMedium,
            text = name?.toSafeLength(ellipsize = true) ?: stringResource(id = CommonStrings.common_no_room_name),
            fontStyle = FontStyle.Italic.takeIf { name == null },
            color = ElementTheme.colors.roomListRoomName,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!isInviteSeen) {
            UnreadIndicatorAtom(
                color = ElementTheme.colors.unreadIndicator
            )
        }
    }
}

@Composable
private fun OnGoingCallIcon(
    color: Color,
    isAudio: Boolean
) {
    Icon(
        modifier = Modifier.size(16.dp),
        imageVector = if (isAudio) CompoundIcons.VoiceCallSolid() else CompoundIcons.VideoCallSolid(),
        contentDescription = stringResource(CommonStrings.a11y_notifications_ongoing_call),
        tint = color,
    )
}

@Composable
private fun NotificationOffIndicatorAtom() {
    Icon(
        modifier = Modifier.size(16.dp),
        contentDescription = stringResource(CommonStrings.a11y_notifications_muted),
        imageVector = CompoundIcons.NotificationsOffSolid(),
        tint = ElementTheme.colors.iconQuaternary,
    )
}

@Composable
private fun MentionIndicatorAtom() {
    Icon(
        modifier = Modifier.size(16.dp),
        contentDescription = stringResource(CommonStrings.a11y_notifications_new_mentions),
        imageVector = CompoundIcons.Mention(),
        tint = ElementTheme.colors.unreadIndicator,
    )
}

// Правка форка (роумлесс): значок типа перед именем. ЛС метки не несёт.
@Composable
private fun ChatTypeIcon(chatType: ChatType) {
    val icon = when (chatType) {
        ChatType.Channel -> CompoundIcons.Public()
        ChatType.Group -> CompoundIcons.Group()
        ChatType.Dm -> return
    }
    Icon(
        modifier = Modifier.size(16.dp),
        imageVector = icon,
        contentDescription = null,
        tint = ElementTheme.colors.iconSecondary,
    )
}

// Правка форка (роумлесс): значок закрепления в строке (когда нет бейджа непрочитанных).
@Composable
private fun PinIndicatorAtom(tint: Color) {
    Icon(
        modifier = Modifier.size(16.dp),
        imageVector = CompoundIcons.PinSolid(),
        contentDescription = null,
        tint = tint,
    )
}

@PreviewsDayNight
@Composable
internal fun RoomSummaryRowPreview(@PreviewParameter(RoomListRoomSummaryProvider::class) data: RoomListRoomSummary) = ElementPreview {
    RoomSummaryRow(
        room = data,
        hideInviteAvatars = false,
        // Set isInviteSeen to true for the preview when the room has name "Bob"
        isInviteSeen = data.name == "Bob",
        onClick = {},
        eventSink = {},
    )
}
