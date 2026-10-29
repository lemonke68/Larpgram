/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.LarpgramType
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.compound.tokens.larpgramColors
import io.element.android.features.home.impl.R
import io.element.android.features.home.impl.TgAccount
import io.element.android.libraries.designsystem.animation.TgMotion
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.components.avatar.TgOwnAvatar
import io.element.android.libraries.designsystem.components.glass.TgGlassTint
import io.element.android.libraries.designsystem.components.glass.tgGlass
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.ui.model.getAvatarData
import kotlinx.collections.immutable.ImmutableList

/**
 * Account switcher opened by a long-press on the Профиль tab: the screen dims with a light blur
 * and a glass popover floats above the tab. One row per account (avatar, name, `@localpart`), the
 * current one with an accent check, the others with their unread count; «Добавить аккаунт» last.
 * Tap outside closes it.
 */
@Composable
internal fun TgAccountSwitcher(
    isVisible: Boolean,
    accounts: ImmutableList<TgAccount>,
    hazeState: HazeState?,
    onAccountClick: (SessionId) -> Unit,
    onAddAccountClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = isVisible, onBack = onDismiss)
    val colors = larpgramColors
    // Plan 9.3: the dim comes in over 180 ms; the popover pops out of the Профиль tab.
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(TgMotion.tween(TgMotion.ACCOUNTS_DIM_MS, TgMotion.Std)),
        exit = fadeOut(TgMotion.tween(TgMotion.ACCOUNTS_OUT_MS, TgMotion.Exit)),
        modifier = modifier,
    ) {
        // A plain dim: a haze layer here would paint the blurred list over the floating header.
        val dimModifier = Modifier.background(colors.dim)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(dimModifier)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
    }
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(TgMotion.tween(TgMotion.POP_FADE_MS, TgMotion.Std)) +
            scaleIn(TgMotion.tween(TgMotion.ACCOUNTS_IN_MS, TgMotion.Pop), initialScale = 0.6f, transformOrigin = PopoverOrigin),
        exit = fadeOut(TgMotion.tween(TgMotion.ACCOUNTS_OUT_MS, TgMotion.Exit)) +
            scaleOut(TgMotion.tween(TgMotion.ACCOUNTS_OUT_MS, TgMotion.Exit), targetScale = 0.6f, transformOrigin = PopoverOrigin),
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Column(
                modifier = Modifier
                    .padding(bottom = TgHomeBarDefaults.bottomMargin + TgHomeBarDefaults.barHeight + 14.dp)
                    .width(268.dp)
                    .tgGlass(RoundedCornerShape(24.dp), tint = TgGlassTint.SurfaceStrong, elevation = 12.dp)
                    .padding(vertical = 6.dp),
            ) {
                accounts.forEach { account ->
                    AccountRow(account = account, onClick = { onAccountClick(SessionId(account.user.userId.value)) })
                }
                HorizontalDivider(color = ElementTheme.colors.separatorPrimary)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onAddAccountClick)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = CompoundIcons.Plus(),
                            contentDescription = null,
                            tint = colors.accentText,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Text(
                        text = stringResource(R.string.larpgram_add_account),
                        style = LarpgramType.listItem.copy(fontWeight = LarpgramType.chatRowName.fontWeight),
                        color = colors.accentText,
                    )
                }
            }
        }
    }
}

// The popover (268 dp) is centered over the bar; the Профиль tab's centre is 12 dp right of the
// screen centre (bar group 276 dp: tabs pill 202 + gap 10 + search 64). As a fraction of the popover.
private val PopoverOrigin = TransformOrigin((134f + 12f) / 268f, 1f)

@Composable
private fun AccountRow(
    account: TgAccount,
    onClick: () -> Unit,
) {
    val colors = larpgramColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (account.isCurrent) colors.accentSoft.copy(alpha = colors.accentSoft.alpha * 0.8f) else Color.Transparent)
            .clickable(enabled = !account.isCurrent, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val avatarData = account.user.getAvatarData(size = AvatarSize.AccountItem)
        if (account.isCurrent) {
            TgOwnAvatar(avatarData = avatarData, size = 40.dp)
        } else {
            Avatar(avatarData = avatarData, avatarType = AvatarType.User, forcedAvatarSize = 40.dp)
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = account.user.displayName?.takeIf { it.isNotBlank() } ?: account.user.userId.extractedDisplayName,
                style = LarpgramType.listItem.copy(fontWeight = LarpgramType.chatRowName.fontWeight),
                color = ElementTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                // Only the localpart: one server, the domain says nothing to the user.
                text = "@" + account.user.userId.extractedDisplayName,
                style = LarpgramType.caption,
                color = ElementTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when {
            account.isCurrent -> Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(colors.accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = CompoundIcons.Check(),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
            account.unreadCount > 0 -> Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
                    .background(colors.accent, CircleShape)
                    .padding(horizontal = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (account.unreadCount > 999) "999+" else account.unreadCount.toString(),
                    style = LarpgramType.badge.copy(lineHeight = 14.sp),
                    color = Color.White,
                )
            }
        }
    }
}
