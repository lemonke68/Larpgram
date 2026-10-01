/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.roomlist

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.home.impl.R
import io.element.android.features.home.impl.model.ChatType
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.components.tg.TgPopupMenu
import io.element.android.libraries.designsystem.components.tg.TgPopupMenuItem
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.utils.captureBlurredBackdrop
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.coroutines.launch

// Геометрия и анимация — `ActionBarLayout.presentFragment(preview)` и `startLayoutAnimation` Telegram:
// поля окна 8, радиусы 29 сверху и 12 снизу (чат с меню), меню на 8 ниже окна, затемнение 0x2e,
// открытие 190 мс из масштаба 0.7 с лёгким перелётом, закрытие 150 мс.
private val WINDOW_SIDE_MARGIN = 8.dp
private val WINDOW_TOP_RADIUS = 29.dp
private val WINDOW_BOTTOM_RADIUS = 12.dp
private val MENU_GAP = 8.dp
private val MENU_BOTTOM_MARGIN = 18.dp
private val HEADER_HEIGHT = 56.dp
private val HEADER_AVATAR_SIZE = 40.dp
private val DIM_COLOR = Color(0x2E000000)
private const val OPEN_MS = 190
private const val CLOSE_MS = 150
private const val START_SCALE = 0.7f
private val OvershootEasing = CubicBezierEasing(0.34f, 1.2f, 0.64f, 1f)

const val TAG_CHAT_PREVIEW = "chat_preview"

/**
 * Превью чата по долгому нажатию в списке, как в Telegram (`DialogsActivity.showChatPreview`):
 * уменьшенное окно чата, которое можно листать, и меню действий под ним. Тап по окну открывает
 * чат, тап мимо или «назад» — закрывает.
 */
@Composable
fun TgChatPreviewOverlay(
    contextMenu: RoomListState.ContextMenu.Shown,
    canReportRoom: Boolean,
    eventSink: (RoomListEvent.ContextMenuEvent) -> Unit,
    onRoomSettingsClick: (roomId: RoomId) -> Unit,
    onReportRoomClick: (roomId: RoomId) -> Unit,
    onOpenChat: (roomId: RoomId) -> Unit,
    chatPreview: @Composable (roomId: RoomId, onClick: () -> Unit, modifier: Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    var backdrop by remember { mutableStateOf<ImageBitmap?>(null) }
    var isReady by remember { mutableStateOf(false) }
    var isClosing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Оверлей в том же окне, что и список: до снимка фона ничего не рисуем, иначе попадём в размытие.
        backdrop = captureBlurredBackdrop(context)
        isReady = true
        progress.animateTo(1f, tween(OPEN_MS, easing = OvershootEasing))
    }

    fun close(then: () -> Unit = {}) {
        if (isClosing) return
        isClosing = true
        scope.launch {
            progress.animateTo(0f, tween(CLOSE_MS))
            eventSink(RoomListEvent.HideContextMenu)
            then()
        }
    }
    BackHandler { close() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { close() } },
    ) {
        if (!isReady) return@Box
        val fade = progress.value.coerceIn(0f, 1f)
        backdrop?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = fade },
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = fade }
                .background(DIM_COLOR)
        )
        val density = LocalDensity.current
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(bottom = MENU_BOTTOM_MARGIN),
        ) {
            val shape = RoundedCornerShape(
                topStart = WINDOW_TOP_RADIUS,
                topEnd = WINDOW_TOP_RADIUS,
                bottomStart = WINDOW_BOTTOM_RADIUS,
                bottomEnd = WINDOW_BOTTOM_RADIUS,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = WINDOW_SIDE_MARGIN)
                    .graphicsLayer {
                        val scale = START_SCALE + (1f - START_SCALE) * progress.value
                        scaleX = scale
                        scaleY = scale
                        alpha = fade
                        translationY = with(density) { 40.dp.toPx() } * (1f - progress.value)
                    }
                    .shadow(4.dp, shape)
                    .clip(shape)
                    .background(ElementTheme.colors.bgCanvasDefault)
                    .testTag(TAG_CHAT_PREVIEW),
            ) {
                PreviewHeader(contextMenu = contextMenu)
                chatPreview(
                    contextMenu.roomId,
                    { close { onOpenChat(contextMenu.roomId) } },
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            }
            Spacer(modifier = Modifier.height(MENU_GAP))
            PreviewMenu(
                contextMenu = contextMenu,
                canReportRoom = canReportRoom,
                onAction = { action -> close(action) },
                eventSink = eventSink,
                onRoomSettingsClick = onRoomSettingsClick,
                onReportRoomClick = onReportRoomClick,
                modifier = Modifier
                    .padding(horizontal = WINDOW_SIDE_MARGIN)
                    .graphicsLayer {
                        alpha = fade
                        val scale = 0.95f + 0.05f * progress.value
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0f)
                        translationY = -with(density) { 70.dp.toPx() } * (1f - progress.value)
                    },
            )
        }
    }
}

@Composable
private fun PreviewHeader(contextMenu: RoomListState.ContextMenu.Shown) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(HEADER_HEIGHT)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        contextMenu.avatarData?.let { avatarData ->
            // Аватар рисуется в размере строки списка и уменьшается целиком: у ЛС без своей картинки
            // это «кластер» из собеседника, а он принудительный размер не учитывает.
            Box(modifier = Modifier.size(HEADER_AVATAR_SIZE), contentAlignment = Alignment.Center) {
                Avatar(
                    avatarData = avatarData,
                    avatarType = AvatarType.Room(heroes = contextMenu.heroes),
                    modifier = Modifier
                        .wrapContentSize(unbounded = true)
                        .scale(HEADER_AVATAR_SIZE.value / avatarData.size.dp.value),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
        }
        Text(
            text = contextMenu.roomName ?: stringResource(id = CommonStrings.common_no_room_name),
            style = ElementTheme.typography.fontBodyLgMedium,
            fontStyle = FontStyle.Italic.takeIf { contextMenu.roomName == null },
            color = ElementTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PreviewMenu(
    contextMenu: RoomListState.ContextMenu.Shown,
    canReportRoom: Boolean,
    onAction: (() -> Unit) -> Unit,
    eventSink: (RoomListEvent.ContextMenuEvent) -> Unit,
    onRoomSettingsClick: (roomId: RoomId) -> Unit,
    onReportRoomClick: (roomId: RoomId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val roomId = contextMenu.roomId
    // Порядок — как в превью Telegram: прочитано, закрепить, звук, …, удалить.
    // Шире меню сообщения: «Отметить как непрочитанное» должно влезать целиком.
    TgPopupMenu(modifier = modifier, maxWidth = 340.dp) {
        if (contextMenu.hasNewContent) {
            TgPopupMenuItem(
                text = stringResource(CommonStrings.action_mark_as_read),
                icon = CompoundIcons.MarkAsRead(),
                onClick = { onAction { eventSink(RoomListEvent.MarkAsRead(roomId)) } },
            )
        } else {
            TgPopupMenuItem(
                text = stringResource(R.string.screen_roomlist_mark_as_unread),
                icon = CompoundIcons.MarkAsUnread(),
                onClick = { onAction { eventSink(RoomListEvent.MarkAsUnread(roomId)) } },
            )
        }
        TgPopupMenuItem(
            text = stringResource(if (contextMenu.isPinned) R.string.screen_roomlist_unpin else R.string.screen_roomlist_pin),
            icon = if (contextMenu.isPinned) CompoundIcons.Unpin() else CompoundIcons.Pin(),
            onClick = { onAction { eventSink(RoomListEvent.SetRoomIsPinned(roomId, !contextMenu.isPinned)) } },
        )
        TgPopupMenuItem(
            text = stringResource(if (contextMenu.isMuted) R.string.screen_roomlist_unmute else R.string.screen_roomlist_mute),
            icon = if (contextMenu.isMuted) CompoundIcons.Notifications() else CompoundIcons.NotificationsOffSolid(),
            onClick = { onAction { eventSink(RoomListEvent.SetRoomIsMuted(roomId, !contextMenu.isMuted)) } },
        )
        TgPopupMenuItem(
            text = stringResource(CommonStrings.common_settings),
            icon = CompoundIcons.Settings(),
            onClick = { onAction { onRoomSettingsClick(roomId) } },
        )
        if (canReportRoom) {
            TgPopupMenuItem(
                text = stringResource(CommonStrings.action_report_room),
                icon = CompoundIcons.ChatProblem(),
                destructive = true,
                onClick = { onAction { onReportRoomClick(roomId) } },
            )
        }
        if (contextMenu.isDm) {
            TgPopupMenuItem(
                text = stringResource(R.string.screen_roomlist_block_user),
                icon = CompoundIcons.Block(),
                destructive = true,
                onClick = { onAction { eventSink(RoomListEvent.BlockUser(roomId, contextMenu.dmUserId)) } },
            )
        }
        val leaveTextResId = when (contextMenu.chatType) {
            ChatType.Dm -> R.string.screen_roomlist_delete_chat
            ChatType.Group -> R.string.screen_roomlist_leave_group
            ChatType.Channel -> R.string.screen_roomlist_leave_channel
        }
        TgPopupMenuItem(
            text = stringResource(leaveTextResId),
            icon = if (contextMenu.isDm) CompoundIcons.Delete() else CompoundIcons.Leave(),
            destructive = true,
            onClick = {
                onAction {
                    if (contextMenu.isDm) {
                        eventSink(RoomListEvent.DeleteRoom(roomId))
                    } else {
                        eventSink(RoomListEvent.LeaveRoom(roomId, needsConfirmation = true))
                    }
                }
            },
        )
    }
}
