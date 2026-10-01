/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.components.tg

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme

private val MENU_CORNER = 14.dp
private val MENU_WIDTH = 280.dp
private val MENU_MIN_WIDTH = 200.dp

/**
 * Полупрозрачный фон всплывающего меню Telegram: сквозь него угадывается размытый фон, но текст
 * читается.
 */
@Composable
@ReadOnlyComposable
fun tgPopupMenuColor(): Color = if (ElementTheme.isLightTheme) {
    Color(0xFFFFFFFF).copy(alpha = 0.72f)
} else {
    Color(0xFF1C1C1E).copy(alpha = 0.70f)
}

/** Карточка всплывающего меню Telegram (`ActionBarPopupWindowLayout`): скруглённая, листается, если не влезла. */
@Composable
fun TgPopupMenu(
    modifier: Modifier = Modifier,
    maxWidth: Dp = MENU_WIDTH,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .width(IntrinsicSize.Max)
            .widthIn(min = MENU_MIN_WIDTH, max = maxWidth)
            .clip(RoundedCornerShape(MENU_CORNER))
            .background(tgPopupMenuColor())
            .verticalScroll(rememberScrollState()),
        content = content,
    )
}
