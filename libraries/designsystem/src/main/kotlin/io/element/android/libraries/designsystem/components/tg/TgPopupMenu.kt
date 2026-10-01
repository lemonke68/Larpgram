/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.components.tg

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text

// Пункт меню — `ActionBarMenuSubItem` Telegram: высота 48, отступы 18, иконка 24, текст 16sp с
// отступом 43 от начала пункта, в одну строку.
private val MENU_CORNER = 14.dp
private val MENU_WIDTH = 280.dp
private val MENU_MIN_WIDTH = 200.dp
private val MENU_ITEM_HEIGHT = 48.dp
private val MENU_ITEM_PADDING = 18.dp
private val MENU_ICON_SIZE = 24.dp
private val MENU_TEXT_START = 43.dp

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

@Composable
fun TgPopupMenuItem(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
) {
    val color = if (destructive) ElementTheme.colors.textCriticalPrimary else ElementTheme.colors.textPrimary
    val iconColor = if (destructive) ElementTheme.colors.iconCriticalPrimary else ElementTheme.colors.iconSecondary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(MENU_ITEM_HEIGHT)
            .clickable(onClick = onClick)
            .padding(horizontal = MENU_ITEM_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(MENU_ICON_SIZE),
        )
        Spacer(modifier = Modifier.width(MENU_TEXT_START - MENU_ICON_SIZE))
        Text(
            text = text,
            color = color,
            style = ElementTheme.typography.fontBodyLgRegular,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
