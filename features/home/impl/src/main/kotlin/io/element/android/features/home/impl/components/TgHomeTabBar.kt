/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.LarpgramType
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.compound.tokens.larpgramColors
import io.element.android.features.home.impl.TgChatsTabIcon
import io.element.android.features.home.impl.TgHomeTab
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.avatar.TgOwnAvatar
import io.element.android.libraries.designsystem.components.glass.TgGlassTint
import io.element.android.libraries.designsystem.components.glass.tgGlass
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.matrix.ui.model.getAvatarData
import io.element.android.libraries.ui.strings.CommonStrings

/** Bar sizes from `tokens.json` → `sizes`. */
internal object TgHomeBarDefaults {
    val barHeight = 64.dp
    val tabWidth = 96.dp
    val tabHeight = 56.dp
    val searchSize = 64.dp
    val gap = 10.dp

    /** The bar floats this far above the navigation-bar inset. */
    val bottomMargin = 18.dp

    /** Space the scrolling content leaves under the bar. */
    val contentBottomPadding = barHeight + bottomMargin + 12.dp
}

/**
 * Bottom bar of the redesign: a centered glass pill with [Чаты | Профиль] and, 10 dp to its right,
 * a separate round glass search button tinted with the accent. Long-press on Профиль opens the
 * account switcher ([onProfileLongClick] is null with a single account: then it does nothing).
 */
@Composable
internal fun TgHomeTabBar(
    selectedItem: TgHomeTab,
    currentUser: MatrixUser?,
    unreadCount: Int,
    isProfilePressed: Boolean,
    onItemClick: (TgHomeTab) -> Unit,
    onProfileLongClick: (() -> Unit)?,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(TgHomeBarDefaults.gap, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .height(TgHomeBarDefaults.barHeight)
                .tgGlass(RoundedCornerShape(32.dp), tint = TgGlassTint.SurfaceStrong, elevation = 10.dp)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TgHomeTab.entries.forEach { item ->
                TgTab(
                    item = item,
                    isSelected = item == selectedItem || (item == TgHomeTab.Profile && isProfilePressed),
                    isPressed = item == TgHomeTab.Profile && isProfilePressed,
                    currentUser = currentUser,
                    unreadCount = if (item == TgHomeTab.Chats) unreadCount else 0,
                    onClick = { onItemClick(item) },
                    onLongClick = if (item == TgHomeTab.Profile) onProfileLongClick else null,
                )
            }
        }
        TgSearchButton(onClick = onSearchClick)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TgTab(
    item: TgHomeTab,
    isSelected: Boolean,
    isPressed: Boolean,
    currentUser: MatrixUser?,
    unreadCount: Int,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val colors = larpgramColors
    val background by animateColorAsState(
        targetValue = if (isSelected) colors.tabSelected else Color.Transparent,
        label = "tabBackground",
    )
    val scale by animateFloatAsState(if (isPressed) 0.94f else 1f, label = "tabScale")
    val contentColor = if (isSelected) colors.accentText else ElementTheme.colors.textPrimary
    val haptics = LocalHapticFeedback.current
    Column(
        modifier = Modifier
            .size(TgHomeBarDefaults.tabWidth, TgHomeBarDefaults.tabHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(28.dp))
            .background(background)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick?.let {
                    {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        it()
                    }
                },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (item) {
            TgHomeTab.Chats -> Box {
                Icon(
                    modifier = Modifier.size(25.dp),
                    imageVector = TgChatsTabIcon,
                    contentDescription = null,
                    tint = contentColor,
                )
                if (unreadCount > 0) {
                    TabBadge(
                        count = unreadCount,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 11.dp, y = (-4).dp),
                    )
                }
            }
            TgHomeTab.Profile -> if (currentUser != null) {
                TgOwnAvatar(
                    avatarData = currentUser.getAvatarData(size = AvatarSize.TgTabAvatar),
                    size = 26.dp,
                )
            } else {
                Icon(
                    modifier = Modifier.size(24.dp),
                    imageVector = CompoundIcons.UserProfile(),
                    contentDescription = null,
                    tint = contentColor,
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(item.labelRes),
            style = LarpgramType.tabLabel,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TabBadge(
    count: Int,
    modifier: Modifier = Modifier,
) {
    val colors = larpgramColors
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
            .clip(CircleShape)
            .border(2.dp, colors.background, CircleShape)
            .background(colors.accent, CircleShape)
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            style = LarpgramType.badge.copy(fontSize = LarpgramType.badge.fontSize * (10f / 12f)),
            color = Color.White,
            maxLines = 1,
        )
    }
}

@Composable
private fun TgSearchButton(
    onClick: () -> Unit,
) {
    val colors = larpgramColors
    Box(
        modifier = Modifier
            .size(TgHomeBarDefaults.searchSize)
            .tgGlass(
                shape = CircleShape,
                overrideTint = colors.searchButton,
                borderColor = colors.searchButtonBorder,
                elevation = 10.dp,
            )
            .combinedClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = CompoundIcons.Search(),
            contentDescription = stringResource(CommonStrings.action_search),
            tint = Color.White,
        )
    }
}
