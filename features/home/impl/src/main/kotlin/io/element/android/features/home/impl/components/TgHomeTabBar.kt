/*
 * Правка форка: нижние вкладки Telegram 12 (`MainTabsLayout`, `GlassTabView`) — стеклянная
 * пилюля во всю ширину с полями 8, высота 56, иконка сверху и подпись 12 жирным; у выбранной
 * вкладки подложка. У «Профиля» вместо иконки — свой аватар. Над панелью справа — круглая кнопка
 * «Новое сообщение», как плавающая кнопка списка чатов TG.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.home.impl.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.home.impl.HomeNavigationBarItem
import io.element.android.features.home.impl.R
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.components.glass.tgGlass
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.matrix.ui.model.getAvatarData

private val TAB_BAR_HEIGHT = 56.dp
private val TAB_BAR_MARGIN = 8.dp
private val FAB_SIZE = 56.dp

@Composable
internal fun TgHomeTabBar(
    selectedItem: HomeNavigationBarItem,
    currentUser: MatrixUser?,
    onItemClick: (HomeNavigationBarItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = TAB_BAR_MARGIN, vertical = TAB_BAR_MARGIN)
            .height(TAB_BAR_HEIGHT)
            .tgGlass(RoundedCornerShape(50))
            .padding(4.dp),
    ) {
        HomeNavigationBarItem.entries.forEach { item ->
            TgTab(
                item = item,
                isSelected = item == selectedItem,
                currentUser = currentUser,
                onClick = { onItemClick(item) },
            )
        }
    }
}

@Composable
private fun RowScope.TgTab(
    item: HomeNavigationBarItem,
    isSelected: Boolean,
    currentUser: MatrixUser?,
    onClick: () -> Unit,
) {
    val background by animateColorAsState(
        targetValue = if (isSelected) ElementTheme.colors.textPrimary.copy(alpha = 0.08f) else Color.Transparent,
        label = "tabBackground",
    )
    val contentColor = if (isSelected) ElementTheme.colors.textActionAccent else ElementTheme.colors.textPrimary
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(50))
            .background(background)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (item == HomeNavigationBarItem.Profile && currentUser != null) {
            Avatar(
                avatarData = currentUser.getAvatarData(size = AvatarSize.TgTabAvatar),
                avatarType = AvatarType.User,
            )
        } else {
            Icon(
                modifier = Modifier.size(24.dp),
                imageVector = item.icon(isSelected),
                contentDescription = null,
                tint = contentColor,
            )
        }
        Spacer(Modifier.height(1.dp))
        Text(
            text = stringResource(item.labelRes),
            style = ElementTheme.typography.fontBodySmMedium.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp),
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Круглая кнопка «Новое сообщение» с меню: новый чат или новый канал. */
@Composable
internal fun TgNewMessageFab(
    onCreateChat: () -> Unit,
    onCreateChannel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(FAB_SIZE)
                .tgGlass(CircleShape)
                .clip(CircleShape)
                .clickable { showMenu = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = CompoundIcons.Compose(),
                contentDescription = stringResource(R.string.screen_home_new_chat),
                tint = ElementTheme.colors.iconPrimary,
            )
        }
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            shape = RoundedCornerShape(12.dp),
            containerColor = ElementTheme.colors.bgCanvasDefault,
        ) {
            FabMenuItem(stringResource(R.string.screen_home_new_chat), CompoundIcons.Chat()) {
                showMenu = false
                onCreateChat()
            }
            FabMenuItem(stringResource(R.string.screen_home_new_channel), CompoundIcons.Public()) {
                showMenu = false
                onCreateChannel()
            }
        }
    }
}

@Composable
private fun FabMenuItem(text: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        leadingIcon = { Icon(imageVector = icon, contentDescription = null, tint = ElementTheme.colors.iconSecondary) },
        text = { Text(text = text, style = ElementTheme.typography.fontBodyLgRegular, color = ElementTheme.colors.textPrimary) },
        onClick = onClick,
    )
}
