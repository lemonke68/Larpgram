/*
 * Copyright (c) 2026 Larpgram.
 * Правка форка: меню долгого нажатия, привязанное к сообщению (фаза 2, переделка 2026-10-01).
 *
 * Вместо шторки снизу — как в Telegram: нажатое сообщение остаётся на месте чётким, фон под
 * ним размыт, над сообщением плашка реакций, под ним меню действий.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.actionlist

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.emojibasebindings.Emoji
import io.element.android.features.messages.impl.actionlist.model.TimelineItemAction
import io.element.android.features.messages.impl.timeline.a11y.a11yReactionAction
import io.element.android.features.messages.impl.timeline.components.customreaction.CustomReactionState
import io.element.android.features.messages.impl.timeline.model.TimelineItem
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.utils.captureBlurredBackdrop
import io.element.android.libraries.emoji.api.picker.EmojiPickerRenderer
import io.element.android.libraries.matrix.api.timeline.item.event.EventOrTransactionId
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// Замеры по chat_menu_*.png: меню и плашка — одна скруглённая карточка, ширина меню около 250,
// зазор от сообщения 8. Реакции сидят в такой же скруглённой плашке над сообщением.
private val MENU_CORNER = 14.dp
private val MENU_WIDTH = 280.dp
private val MENU_MIN_WIDTH = 200.dp

// Пункт меню — `ActionBarMenuSubItem` Telegram: высота 48, отступы 18, иконка 24, текст 16sp с
// отступом 43 от начала пункта, в одну строку.
private val MENU_ITEM_HEIGHT = 48.dp
private val MENU_ITEM_PADDING = 18.dp
private val MENU_ICON_SIZE = 24.dp
private val MENU_TEXT_START = 43.dp
private val GAP = 8.dp
private val PILL_CORNER = 24.dp
private val SCREEN_EDGE_PADDING = 8.dp

// Пока пузырь сдвигается (прячется клавиатура), ждём, чтобы его координаты не менялись столько
// кадров подряд, но не дольше тайм-аута.
private const val STABLE_FRAMES = 3
private const val SETTLE_TIMEOUT_MS = 600L

// Появление и уход меню: у Telegram меню проявляется за 150 мс и гаснет за 220.
private const val FADE_IN_MS = 150
private const val FADE_OUT_MS = 180

/**
 * Меню долгого нажатия вокруг сообщения, как в Telegram (`ChatActivity.createMenu`).
 *
 * Сообщение рисуется целиком поверх размытого фона, обрезанное только полосой ленты между
 * шапкой и полем ввода. Это не снимок, а собственный слой пузыря ([MessageActionsAnchor.layerFor]),
 * нарисованный второй раз в том же окне: форма и хвост те же, кружки, гифки и стикеры играют. Плашка реакций — над
 * видимой частью пузыря, меню — под ней. Если меню под пузырём не влезает, всё вместе поднимается
 * на свободное место сверху, а остаток меню наезжает на пузырь (пузырь не сжимается, меню
 * листается). Под полупрозрачным меню и плашкой виден размытый фон, а не чёткий пузырь.
 */
@Composable
fun MessageActionsOverlay(
    target: ActionListState.Target.Success,
    anchor: MessageActionsAnchor,
    onSelectAction: (TimelineItemAction, TimelineItem.Event) -> Unit,
    onEmojiReactionClick: (String, TimelineItem.Event) -> Unit,
    onCustomReactionClick: (TimelineItem.Event) -> Unit,
    // Правка форка (фаза 3): пикер эмодзи разворачивается прямо в оверлее по «+», а не
    // шторкой снизу. Состояние и рендерер прокинуты из MessagesView.
    customReactionState: CustomReactionState,
    emojiPickerRenderer: EmojiPickerRenderer,
    onSelectEmoji: (EventOrTransactionId, Emoji) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val event = target.event
    val eventKey = event.id.value
    var bubbleRect by remember { mutableStateOf(anchor.unclippedBoundsFor(eventKey)) }
    var isSettled by remember { mutableStateOf(false) }
    // Оверлей живёт в окне чата, а не в попапе: только так слой пузыря можно нарисовать живым.
    // Поэтому до снимка фона он не рисует ничего, иначе сам попал бы в размытие. Снимаем фон,
    // когда лента встала на место: иначе в размытие попадала уезжающая клавиатура.
    var backdrop by remember { mutableStateOf<ImageBitmap?>(null) }
    val context = LocalContext.current
    val alpha = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var isClosing by remember { mutableStateOf(false) }
    LaunchedEffect(eventKey) {
        // Клавиатура могла ещё уезжать, и лента вместе с ней: берём место пузыря, когда оно
        // перестало меняться.
        val start = System.currentTimeMillis()
        var stableFrames = 0
        var last = anchor.unclippedBoundsFor(eventKey)
        while (stableFrames < STABLE_FRAMES && System.currentTimeMillis() - start < SETTLE_TIMEOUT_MS) {
            withFrameNanos { }
            val current = anchor.unclippedBoundsFor(eventKey)
            if (current == last) stableFrames++ else stableFrames = 0
            last = current
        }
        bubbleRect = last
        backdrop = captureBlurredBackdrop(context)
        isSettled = true
        alpha.animateTo(1f, tween(FADE_IN_MS))
    }

    fun dismiss() {
        if (isClosing) return
        isClosing = true
        scope.launch {
            alpha.animateTo(0f, tween(FADE_OUT_MS))
            onDismiss()
        }
    }
    BackHandler(onBack = ::dismiss)

    run {
        // Всё считаем в координатах экрана: корень оверлея может начинаться не в его углу.
        var popupOrigin by remember { mutableStateOf<Offset?>(null) }
        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .onGloballyPositioned { popupOrigin = it.positionOnScreen() }
                // Перехватывает и жесты: лента под меню не листается (в Telegram она заморожена).
                .pointerInput(Unit) { detectTapGestures { dismiss() } }
                .graphicsLayer { this.alpha = alpha.value },
        ) {
            val backdropBitmap = backdrop
            if (backdropBitmap != null) {
                Image(
                    bitmap = backdropBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (isSettled) {
                // Снимок не получился (до Android 8 его нет): обычное затемнение.
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
            }
            if (!isSettled) return@BoxWithConstraints
            val origin = popupOrigin ?: return@BoxWithConstraints
            val rect = bubbleRect?.translate(-origin.x, -origin.y) ?: return@BoxWithConstraints
            val bandTop = anchor.visibleTopPx - origin.y
            val bandBottom = anchor.visibleBottomPx - origin.y

            // Пикер активен, только если его состояние загружено ИМЕННО для этого сообщения:
            // иначе стухшее состояние от прошлого «+» развернуло бы пикер сразу при открытии.
            val pickerTarget = (customReactionState.target as? CustomReactionState.Target.Success)
                ?.takeIf { it.event.eventOrTransactionId == event.eventOrTransactionId }

            val density = LocalDensity.current
            val statusBarTop = WindowInsets.statusBars.getTop(density)
            val navBarBottom = WindowInsets.navigationBars.getBottom(density)
            val edgePx = with(density) { SCREEN_EDGE_PADDING.roundToPx() }
            val gapPx = with(density) { GAP.roundToPx() }
            val cornerPx = with(density) { MENU_CORNER.toPx() }
            val visibleTop = rect.top.coerceAtLeast(bandTop).roundToInt()
            val visibleBottom = rect.bottom.coerceAtMost(bandBottom).roundToInt().coerceAtLeast(visibleTop)
            val bubbleLeft = rect.left.roundToInt()
            val bubbleRight = rect.right.roundToInt()
            // Где легли плашка и меню (в координатах пузыря): там вместо чёткого пузыря — фон.
            var coveredRects by remember { mutableStateOf(emptyList<Rect>()) }

            Layout(
                content = {
                    Box(
                        modifier = Modifier.drawBehind {
                            // Слой берём на каждой отрисовке: строка ленты могла пересоздать его.
                            val bubbleLayer = anchor.layerFor(eventKey) ?: return@drawBehind
                            val holes = Path().apply {
                                coveredRects.forEach { addRoundRect(RoundRect(it, CornerRadius(cornerPx))) }
                            }
                            clipRect(top = visibleTop - rect.top, bottom = visibleBottom - rect.top) {
                                clipPath(holes, clipOp = ClipOp.Difference) {
                                    drawLayer(bubbleLayer)
                                }
                            }
                        },
                    )
                    if (pickerTarget != null) {
                        // Развёрнутый пикер эмодзи вместо плашки и меню: над сообщением, как в Telegram.
                        EmojiPickerPanel(
                            target = pickerTarget,
                            selectedEmoji = customReactionState.selectedEmoji,
                            emojiPickerRenderer = emojiPickerRenderer,
                            onSelectEmoji = { emoji ->
                                onSelectEmoji(pickerTarget.event.eventOrTransactionId, emoji)
                                dismiss()
                            },
                        )
                    } else {
                        if (target.displayEmojiReactions) {
                            ReactionPill(
                                recentEmojis = target.recentEmojis,
                                onEmojiClick = { emoji ->
                                    onEmojiReactionClick(emoji, event)
                                    dismiss()
                                },
                                // «+» не закрывает оверлей: он грузит состояние пикера, после чего
                                // тот разворачивается прямо здесь (ветка pickerTarget выше).
                                onCustomReactionClick = { onCustomReactionClick(event) },
                            )
                        } else {
                            Spacer(modifier = Modifier)
                        }
                        ActionsMenu(
                            target = target,
                            onActionClick = { action ->
                                onSelectAction(action, event)
                                onDismiss()
                            },
                        )
                    }
                },
                modifier = Modifier.fillMaxSize(),
            ) { measurables, constraints ->
                val width = constraints.maxWidth
                val height = constraints.maxHeight
                val minY = statusBarTop + edgePx
                val maxBottom = height - navBarBottom - edgePx
                val bubblePlaceable = measurables[0].measure(
                    Constraints.fixed(rect.width.roundToInt().coerceAtLeast(0), rect.height.roundToInt().coerceAtLeast(0))
                )
                val childConstraints = Constraints(maxWidth = width - 2 * edgePx, maxHeight = (maxBottom - minY).coerceAtLeast(0))

                fun xFor(childWidth: Int): Int {
                    val x = if (event.isMine) bubbleRight - childWidth else bubbleLeft
                    return x.coerceIn(edgePx, (width - edgePx - childWidth).coerceAtLeast(edgePx))
                }

                fun Placeable.rectAt(x: Int, y: Int, shift: Int) =
                    Rect(x - rect.left, y - (rect.top - shift), x - rect.left + width, y - (rect.top - shift) + this.height)

                if (measurables.size == 2) {
                    // Пикер: над сообщением; не влезает — под ним; и там нет места — от верха экрана.
                    val picker = measurables[1].measure(childConstraints)
                    val above = visibleTop - gapPx - picker.height
                    val below = visibleBottom + gapPx
                    val y = when {
                        above >= minY -> above
                        below + picker.height <= maxBottom -> below
                        else -> minY
                    }
                    val x = (width - picker.width) / 2
                    coveredRects = listOf(picker.rectAt(x, y, 0))
                    layout(width, height) {
                        bubblePlaceable.place(bubbleLeft, rect.top.roundToInt())
                        picker.place(x, y)
                    }
                } else {
                    val pill = measurables[1].measure(childConstraints)
                    val pillBlock = if (pill.height > 0) pill.height + gapPx else 0
                    val menu = measurables[2].measure(
                        childConstraints.copy(maxHeight = (maxBottom - minY - pillBlock).coerceAtLeast(0))
                    )
                    // Меню под пузырём не влезает — поднимаем всё на свободное место сверху.
                    val overflow = visibleBottom + gapPx + menu.height - maxBottom
                    val roomAbove = visibleTop - pillBlock - minY
                    val shift = overflow.coerceIn(0, roomAbove.coerceAtLeast(0))
                    val pillY = (visibleTop - shift - pillBlock).coerceAtLeast(minY)
                    val menuMinY = pillY + pillBlock
                    val below = visibleBottom - shift + gapPx
                    val menuY = if (below + menu.height <= maxBottom) below else (maxBottom - menu.height).coerceAtLeast(menuMinY)
                    val pillX = xFor(pill.width)
                    val menuX = xFor(menu.width)
                    coveredRects = listOf(pill.rectAt(pillX, pillY, shift), menu.rectAt(menuX, menuY, shift))
                    layout(width, height) {
                        bubblePlaceable.place(bubbleLeft, rect.top.roundToInt() - shift)
                        pill.place(pillX, pillY)
                        menu.place(menuX, menuY)
                    }
                }
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

// Развёрнутый пикер эмодзи (та же сетка, что в шторке реакций, только инлайн в оверлее).
// Высота ограничена, чтобы над сообщением осталось место; внутри сетка скроллится сама.
@Composable
private fun EmojiPickerPanel(
    target: CustomReactionState.Target.Success,
    selectedEmoji: ImmutableSet<String>,
    emojiPickerRenderer: EmojiPickerRenderer,
    onSelectEmoji: (Emoji) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 360.dp)
            .clip(RoundedCornerShape(MENU_CORNER))
            .background(larpgramActionSheetColor()),
    ) {
        emojiPickerRenderer.Render(
            state = target.emojiPickerState,
            onSelectEmoji = onSelectEmoji,
            selectedEmojis = selectedEmoji,
            modifier = Modifier.fillMaxSize(),
            contentDescription = { emoji, isSelected ->
                a11yReactionAction(emoji = emoji.unicode, userAlreadyReacted = isSelected)
            },
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
            .width(IntrinsicSize.Max)
            .widthIn(min = MENU_MIN_WIDTH, max = MENU_WIDTH)
            .clip(RoundedCornerShape(MENU_CORNER))
            .background(larpgramActionSheetColor())
            // Высоту ограничивает оверлей: всё, что не влезло между плашкой и низом экрана, листается.
            .verticalScroll(rememberScrollState()),
    ) {
        val deliveryState = event.deliveryStateForMenu()
        if (event.isMine && deliveryState != null) {
            DeliveryStatusRow(state = deliveryState, sentTime = target.sentTimeFull)
            HorizontalDivider()
        }
        target.actions.forEach { action ->
            TgMenuItem(
                action = action,
                onClick = { onActionClick(action) },
            )
        }
    }
}

@Composable
private fun TgMenuItem(
    action: TimelineItemAction,
    onClick: () -> Unit,
) {
    val color = if (action.destructive) ElementTheme.colors.textCriticalPrimary else ElementTheme.colors.textPrimary
    val iconColor = if (action.destructive) ElementTheme.colors.iconCriticalPrimary else ElementTheme.colors.iconSecondary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(MENU_ITEM_HEIGHT)
            .clickable(onClick = onClick)
            .padding(horizontal = MENU_ITEM_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            resourceId = action.icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(MENU_ICON_SIZE),
        )
        Spacer(modifier = Modifier.width(MENU_TEXT_START - MENU_ICON_SIZE))
        Text(
            text = stringResource(id = action.titleRes),
            color = color,
            style = ElementTheme.typography.fontBodyLgRegular,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
