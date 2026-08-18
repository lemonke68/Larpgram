/*
 * Правка форка: меню долгого нажатия, привязанное к сообщению (фаза 2).
 *
 * Вместо шторки снизу — как в Telegram: нажатое сообщение остаётся на месте чётким, фон под
 * ним размыт, над сообщением плашка реакций, под ним меню действий.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.messages.impl.actionlist

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.messages.impl.actionlist.model.TimelineItemAction
import io.element.android.features.messages.impl.timeline.model.TimelineItem
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.ListItemStyle
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.utils.rememberBlurredBackdrop
import io.element.android.libraries.designsystem.utils.rememberSharpRegion

// Замеры по chat_menu_*.png: меню и плашка — одна скруглённая карточка, ширина меню около 250,
// зазор от сообщения 8. Реакции сидят в такой же скруглённой плашке над сообщением.
private val MENU_CORNER = 14.dp
private val MENU_WIDTH = 250.dp
private val GAP = 8.dp
private val PILL_CORNER = 24.dp

// Плашку реакций надо разместить над сообщением. Точную высоту знать неоткуда до раскладки,
// поэтому резервируем оценку: одна перекомпоновка ради идеального пикселя не стоит сложности.
private val PILL_RESERVE = 60.dp
private val SCREEN_EDGE_PADDING = 8.dp

// Радиус скругления снимка пузыря: близко к телу пузыря форка (20dp).
private val BUBBLE_SNAPSHOT_CORNER = 18.dp

/**
 * Меню долгого нажатия вокруг сообщения.
 *
 * Фон уже размыт отдельным слоем в [io.element.android.features.messages.impl.MessagesView];
 * этот оверлей прозрачный и лежит поверх, рисуя чёткую копию пузыря, плашку реакций и меню.
 *
 * @param bubbleLeft/Top/Right/Bottom экранные координаты пузыря в пикселях (из boundsInWindow).
 */
@Composable
fun MessageActionsOverlay(
    target: ActionListState.Target.Success,
    bubbleLeft: Int,
    bubbleTop: Int,
    bubbleRight: Int,
    bubbleBottom: Int,
    onSelectAction: (TimelineItemAction, TimelineItem.Event) -> Unit,
    onEmojiReactionClick: (String, TimelineItem.Event) -> Unit,
    onCustomReactionClick: (TimelineItem.Event) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val event = target.event
    val bubble = rememberSharpRegion(bubbleLeft, bubbleTop, bubbleRight, bubbleBottom, enabled = true)
    // Блюр рисуется здесь же, внутри окна оверлея, а не отдельным попапом: отдельное окно
    // приезжало асинхронно и ложилось поверх меню (гонка порядка окон).
    val backdrop = rememberBlurredBackdrop(enabled = true)

    Popup(
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        ) {
            if (backdrop != null) {
                Image(
                    bitmap = backdrop,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            val density = LocalDensity.current
            val bubbleWidthDp = with(density) { (bubbleRight - bubbleLeft).toDp() }
            val bubbleHeightDp = with(density) { (bubbleBottom - bubbleTop).toDp() }
            val bubbleLeftDp = with(density) { bubbleLeft.toDp() }
            val bubbleRightGapDp = with(density) { (constraints.maxWidth - bubbleRight).toDp() }

            // Группу двигаем по вертикали так, чтобы она целиком влезла на экран: держим пузырь
            // близко к его исходному Y, но не даём меню уехать за нижний край (замечание юзера,
            // как в Telegram). Для этого нужна измеренная высота группы, отсюда onSizeChanged.
            val screenHeightPx = constraints.maxHeight
            val minTopPx = with(density) { SCREEN_EDGE_PADDING.roundToPx() }
            val bottomMarginPx = with(density) { 24.dp.roundToPx() }
            val pillReservePx = with(density) { (PILL_RESERVE + GAP).roundToPx() }
            var groupHeightPx by remember { mutableStateOf(0) }
            val desiredTopPx = bubbleTop - pillReservePx
            val yOffsetPx = if (groupHeightPx == 0) {
                desiredTopPx.coerceAtLeast(minTopPx)
            } else {
                val maxTop = (screenHeightPx - groupHeightPx - bottomMarginPx).coerceAtLeast(minTopPx)
                desiredTopPx.coerceIn(minTopPx, maxTop)
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset { IntOffset(0, yOffsetPx) }
                    .onSizeChanged { groupHeightPx = it.height }
                    .fillMaxWidth()
                    .padding(
                        start = if (event.isMine) SCREEN_EDGE_PADDING else bubbleLeftDp,
                        end = if (event.isMine) bubbleRightGapDp else SCREEN_EDGE_PADDING,
                    ),
                horizontalAlignment = if (event.isMine) Alignment.End else Alignment.Start,
            ) {
                if (target.displayEmojiReactions) {
                    ReactionPill(
                        recentEmojis = target.recentEmojis,
                        onEmojiClick = { emoji ->
                            onEmojiReactionClick(emoji, event)
                            onDismiss()
                        },
                        onCustomReactionClick = {
                            onCustomReactionClick(event)
                            onDismiss()
                        },
                    )
                    Spacer(modifier = Modifier.height(GAP))
                }

                // Чёткая копия пузыря на своём месте, скруглённая под форму пузыря: иначе углы
                // прямоугольного снимка показывают обои, а на размытом фоне это читается рамкой.
                // Пока снимок готовится (доли кадра) — отступ его размера, чтобы ничего не прыгало.
                if (bubble != null) {
                    Image(
                        bitmap = bubble,
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier
                            .size(bubbleWidthDp, bubbleHeightDp)
                            .clip(RoundedCornerShape(BUBBLE_SNAPSHOT_CORNER)),
                    )
                } else {
                    Spacer(modifier = Modifier.size(bubbleWidthDp, bubbleHeightDp))
                }

                Spacer(modifier = Modifier.height(GAP))

                ActionsMenu(
                    target = target,
                    onActionClick = { action ->
                        onSelectAction(action, event)
                        onDismiss()
                    },
                )
            }
        }
    }
}

@Composable
private fun ReactionPill(
    recentEmojis: List<String>,
    onEmojiClick: (String) -> Unit,
    onCustomReactionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(PILL_CORNER))
            .background(larpgramActionSheetColor())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        recentEmojis.forEach { emoji ->
            Text(
                text = emoji,
                style = ElementTheme.typography.fontHeadingMdRegular,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { onEmojiClick(emoji) }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
        Text(
            text = "+",
            textAlign = TextAlign.Center,
            style = ElementTheme.typography.fontHeadingMdRegular,
            color = ElementTheme.colors.iconSecondary,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable { onCustomReactionClick() }
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun ActionsMenu(
    target: ActionListState.Target.Success,
    onActionClick: (TimelineItemAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val event = target.event
    Column(
        modifier = modifier
            .widthIn(max = MENU_WIDTH)
            .clip(RoundedCornerShape(MENU_CORNER))
            .background(larpgramActionSheetColor()),
    ) {
        val deliveryState = event.deliveryStateForMenu()
        if (event.isMine && deliveryState != null) {
            DeliveryStatusRow(state = deliveryState, sentTime = target.sentTimeFull)
            HorizontalDivider()
        }
        target.actions.forEach { action ->
            // Контейнер у designsystem ListItem прозрачный по умолчанию, поэтому единый грей
            // карточки не рвётся.
            ListItem(
                headlineContent = { Text(text = stringResource(id = action.titleRes)) },
                leadingContent = ListItemContent.Icon(IconSource.Resource(action.icon)),
                style = if (action.destructive) ListItemStyle.Destructive else ListItemStyle.Default,
                onClick = { onActionClick(action) },
            )
        }
    }
}
