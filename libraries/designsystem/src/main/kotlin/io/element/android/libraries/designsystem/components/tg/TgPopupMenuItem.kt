/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.components.tg

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text

// Пункт меню — `ActionBarMenuSubItem` Telegram: высота 48, отступы 18, иконка 24, текст 16sp с
// отступом 43 от начала пункта, в одну строку.
private val MENU_ITEM_HEIGHT = 48.dp
private val MENU_ITEM_PADDING = 18.dp
private val MENU_ICON_SIZE = 24.dp
private val MENU_TEXT_START = 43.dp

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
