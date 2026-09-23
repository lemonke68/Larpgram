/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagecomposer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.Text

/** Правка форка: вкладки панели Telegram под полем ввода. */
enum class TgMediaPanelTab(val title: String) {
    Emoji("Эмодзи"),
    Gif("GIF"),
    Stickers("Стикеры"),
}

/**
 * Правка форка: состояние панели эмодзи/GIF/стикеров, которая в Telegram встаёт на место
 * экранной клавиатуры (`ChatActivityEnterView` + `EmojiView`).
 *
 * Высота панели = высота клавиатуры (последняя виденная, включая полосу навигации). Пока
 * клавиатура уезжает или выезжает, видимая часть панели = высота клавиатуры − текущий отступ
 * клавиатуры, поэтому поле ввода не прыгает ни при открытии панели, ни при возврате клавиатуры.
 */
@Stable
class TgMediaPanelController internal constructor(initialKeyboardHeightPx: Int) {
    var isVisible by mutableStateOf(false)
        internal set
    var tab by mutableStateOf(TgMediaPanelTab.Emoji)

    /** Пока в панели в фокусе поиск (GIF), клавиатура её не закрывает. */
    var isSearchFocused by mutableStateOf(false)

    internal var keyboardHeightPx by mutableIntStateOf(initialKeyboardHeightPx)

    internal fun open() {
        isVisible = true
    }

    fun close() {
        isVisible = false
        isSearchFocused = false
    }
}

/** Последняя высота клавиатуры за жизнь процесса, чтобы не начинать каждый чат с догадки. */
private var lastKeyboardHeightPx = 0

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun rememberTgMediaPanelController(): TgMediaPanelController {
    val density = LocalDensity.current
    val navBar = WindowInsets.navigationBars.getBottom(density)
    val fallback = lastKeyboardHeightPx.takeIf { it > 0 } ?: (navBar + with(density) { DEFAULT_KEYBOARD_HEIGHT.roundToPx() })
    val controller = remember { TgMediaPanelController(fallback) }

    // Высота клавиатуры: цель анимации IME, как только она больше нуля.
    val imeTarget = WindowInsets.imeAnimationTarget.getBottom(density)
    LaunchedEffect(imeTarget) {
        if (imeTarget > navBar + with(density) { MIN_KEYBOARD_HEIGHT.roundToPx() }) {
            controller.keyboardHeightPx = imeTarget
            lastKeyboardHeightPx = imeTarget
        }
    }

    // Клавиатура выехала обратно (тап в поле, кнопка клавиатуры) — панель уходит. Считаем только
    // подъём после того, как клавиатура уже опускалась при открытой панели, иначе панель закрылась
    // бы в тот же момент, когда её открыли поверх ещё не уехавшей клавиатуры.
    val imeInsets = WindowInsets.ime
    LaunchedEffect(controller.isVisible) {
        if (!controller.isVisible) return@LaunchedEffect
        var wentDown = false
        snapshotFlow { imeInsets.getBottom(density) to controller.isSearchFocused }.collect { (ime, isSearchFocused) ->
            if (isSearchFocused) {
                // Клавиатура поднялась ради поиска; когда поиск отпустят, закрывать по ней можно.
                wentDown = true
                return@collect
            }
            val keyboard = controller.keyboardHeightPx
            if (ime < keyboard / 2) {
                wentDown = true
            } else if (wentDown && ime >= keyboard * 9 / 10) {
                controller.close()
            }
        }
    }

    BackHandler(enabled = controller.isVisible) { controller.close() }
    return controller
}

/** Сколько места панель занимает сейчас (0, если закрыта). */
@Composable
fun TgMediaPanelController.visibleHeight(): Dp {
    if (!isVisible) return 0.dp
    val density = LocalDensity.current
    if (isSearchFocused) return with(density) { keyboardHeightPx.toDp() }
    val ime = WindowInsets.ime.getBottom(density)
    return with(density) { (keyboardHeightPx - ime).coerceAtLeast(0).toDp() }
}

/**
 * Панель под полем ввода: содержимое вкладки, снизу пилюля вкладок «Эмодзи / GIF / Стикеры» и
 * стирание на вкладке эмодзи. Цвета — `chat_emojiPanelBackground` / `chat_emojiPanelIcon*`
 * Telegram, верхние углы 29dp (`ChatInputViewsContainer.INPUT_KEYBOARD_RADIUS`).
 */
@Composable
fun TgMediaPanel(
    controller: TgMediaPanelController,
    onBackspace: () -> Unit,
    emojiContent: @Composable (Modifier) -> Unit,
    gifContent: (@Composable (Modifier) -> Unit)?,
    stickerContent: (@Composable (Modifier) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val height = controller.visibleHeight()
    if (height <= 0.dp) return
    val isLight = ElementTheme.isLightTheme
    val background = if (isLight) Color(0xFFF0F2F5) else Color(0xFF1E1E1F)
    val tabs = buildList {
        add(TgMediaPanelTab.Emoji)
        if (gifContent != null) add(TgMediaPanelTab.Gif)
        if (stickerContent != null) add(TgMediaPanelTab.Stickers)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(topStart = PANEL_RADIUS, topEnd = PANEL_RADIUS))
            .background(background),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 6.dp),
        ) {
            val contentModifier = Modifier.fillMaxSize()
            when (controller.tab) {
                TgMediaPanelTab.Emoji -> emojiContent(contentModifier)
                TgMediaPanelTab.Gif -> gifContent?.invoke(contentModifier)
                TgMediaPanelTab.Stickers -> stickerContent?.invoke(contentModifier)
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(if (isLight) Color(0x0F000000) else Color(0x14FFFFFF))
                    .padding(3.dp),
            ) {
                tabs.forEach { tab ->
                    val isSelected = tab == controller.tab
                    Text(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isSelected) (if (isLight) Color.White else Color(0x28FFFFFF)) else Color.Transparent)
                            .clickable {
                                controller.tab = tab
                                controller.isSearchFocused = false
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        text = tab.title,
                        style = ElementTheme.typography.fontBodyMdMedium,
                        color = if (isSelected) ElementTheme.colors.textPrimary else ElementTheme.colors.textSecondary,
                    )
                }
            }
            if (controller.tab == TgMediaPanelTab.Emoji) {
                IconButton(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 4.dp),
                    onClick = onBackspace,
                ) {
                    Icon(
                        modifier = Modifier.size(24.dp),
                        imageVector = CompoundIcons.Backspace(),
                        contentDescription = "Стереть",
                        tint = if (isLight) Color(0xFF8C9197) else Color(0xFF7B8187),
                    )
                }
            }
        }
        Box(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

private val PANEL_RADIUS = 29.dp
private val DEFAULT_KEYBOARD_HEIGHT = 290.dp
private val MIN_KEYBOARD_HEIGHT = 120.dp
