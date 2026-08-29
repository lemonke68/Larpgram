/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.root

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.preferences.impl.user.UserPreferences
import io.element.android.features.preferences.impl.userstatus.UserStatusState
import io.element.android.features.preferences.impl.userstatus.UserStatusView
import io.element.android.libraries.architecture.coverage.ExcludeFromCoverage
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.components.preferences.PreferencePage
import io.element.android.libraries.designsystem.preview.ElementPreviewDark
import io.element.android.libraries.designsystem.preview.ElementPreviewLight
import io.element.android.libraries.designsystem.preview.PreviewWithLargeHeight
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.utils.CommonDrawables
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarHost
import io.element.android.libraries.designsystem.utils.snackbar.rememberSnackbarHostState
import io.element.android.libraries.emoji.api.picker.EmojiPickerRenderer
import io.element.android.libraries.emoji.api.picker.NoOpEmojiPickerRenderer
import io.element.android.libraries.matrix.api.core.DeviceId
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.matrix.ui.components.MatrixUserRow
import io.element.android.libraries.ui.strings.CommonStrings

@Composable
fun PreferencesRootView(
    state: PreferencesRootState,
    emojiPickerRenderer: EmojiPickerRenderer,
    onBackClick: () -> Unit,
    onAddAccountClick: () -> Unit,
    onOpenCategory: (SettingsCategory) -> Unit,
    onOpenUserProfile: (MatrixUser) -> Unit,
    onOpenAbout: () -> Unit,
    onOpenRageShake: () -> Unit,
    onOpenLabs: () -> Unit,
    onOpenDeveloperSettings: () -> Unit,
    onOpenAdvancedSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = rememberSnackbarHostState(snackbarMessage = state.snackbarMessage)

    PreferencePage(
        modifier = modifier,
        onBackClick = onBackClick,
        title = stringResource(id = CommonStrings.common_settings),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) {
        UserPreferences(
            modifier = Modifier.clickable {
                onOpenUserProfile(state.myUser)
            },
            matrixUser = state.myUser,
        )
        if (state.isMultiAccountEnabled) {
            MultiAccountSection(
                state = state,
                onAddAccountClick = onAddAccountClick,
            )
        }
        if (state.userStatusState != null) {
            UserStatusSection(
                userStatusState = state.userStatusState,
                emojiPickerRenderer = emojiPickerRenderer,
                showTopDivider = !state.isMultiAccountEnabled,
            )
        }
        // TG-категории (Ф2): один верхнеуровневый список, каждая строка ведёт в свой под-экран.
        CategoriesSection(onOpenCategory = onOpenCategory)
        // «О приложении» — аналог блока «Помощь» в TG-настройках.
        AppInfoSection(
            state = state,
            onOpenAbout = onOpenAbout,
            onOpenRageShake = onOpenRageShake,
            onOpenLabs = onOpenLabs,
            onOpenDeveloperSettings = onOpenDeveloperSettings,
            onOpenAdvancedSettings = onOpenAdvancedSettings,
        )
        // Version
        Footer(
            version = state.version,
            deviceId = state.deviceId,
            onClick = if (!state.showDeveloperSettings) {
                { state.eventSink(PreferencesRootEvent.OnVersionInfoClick) }
            } else {
                null
            }
        )
    }
}

@Composable
private fun ColumnScope.CategoriesSection(
    onOpenCategory: (SettingsCategory) -> Unit,
) {
    TgSettingsGroup {
        SettingsCategory.entries.forEach { category ->
            TgSettingsItem(
                title = category.title,
                subtitle = category.subtitle,
                color = category.color,
                iconVector = category.icon,
                trailingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.ChevronRight())),
                onClick = { onOpenCategory(category) },
            )
        }
    }
}

@Composable
private fun ColumnScope.AppInfoSection(
    state: PreferencesRootState,
    onOpenAbout: () -> Unit,
    onOpenRageShake: () -> Unit,
    onOpenLabs: () -> Unit,
    onOpenDeveloperSettings: () -> Unit,
    onOpenAdvancedSettings: () -> Unit,
) {
    TgSettingsGroup {
        TgSettingsItem(
            title = stringResource(id = CommonStrings.common_about),
            color = TgSettingsColors.Blue,
            iconVector = CompoundIcons.Info(),
            onClick = onOpenAbout,
        )
        // Полный экран «Дополнительно» (dev-режим, live-location и пр. — то, что не легло в категории).
        TgSettingsItem(
            title = stringResource(id = CommonStrings.common_advanced_settings),
            color = TgSettingsColors.Gray,
            iconVector = CompoundIcons.Settings(),
            onClick = onOpenAdvancedSettings,
        )
        if (state.canReportBug) {
            TgSettingsItem(
                title = stringResource(id = CommonStrings.common_report_a_problem),
                color = TgSettingsColors.Orange,
                iconVector = CompoundIcons.ChatProblem(),
                onClick = onOpenRageShake,
            )
        }
        if (state.showLabsItem) {
            TgSettingsItem(
                title = stringResource(id = io.element.android.features.preferences.impl.R.string.screen_labs_title),
                color = TgSettingsColors.Purple,
                iconVector = CompoundIcons.Labs(),
                onClick = onOpenLabs,
            )
        }
        // В конце, чтобы случайный 8-кратный тап по версии ничего не ломал.
        if (state.showDeveloperSettings) {
            TgSettingsItem(
                title = stringResource(id = CommonStrings.common_developer_options),
                color = TgSettingsColors.Gray,
                iconVector = CompoundIcons.Code(),
                onClick = onOpenDeveloperSettings,
            )
        }
    }
}

@Composable
private fun ColumnScope.UserStatusSection(
    userStatusState: UserStatusState,
    emojiPickerRenderer: EmojiPickerRenderer,
    showTopDivider: Boolean,
) {
    if (showTopDivider) {
        HorizontalDivider(
            thickness = 8.dp,
            color = ElementTheme.colors.bgSubtleSecondary,
        )
    }
    UserStatusView(
        state = userStatusState,
        emojiPickerRenderer = emojiPickerRenderer,
        modifier = Modifier.fillMaxWidth(),
    )
    HorizontalDivider(
        thickness = 1.dp,
        color = ElementTheme.colors.bgSubtleSecondary,
    )
}

@Composable
private fun ColumnScope.MultiAccountSection(
    state: PreferencesRootState,
    onAddAccountClick: () -> Unit,
) {
    HorizontalDivider(
        thickness = 8.dp,
        color = ElementTheme.colors.bgSubtleSecondary,
    )
    state.otherSessions.forEach { matrixUser ->
        MatrixUserRow(
            modifier = Modifier
                .clickable {
                    state.eventSink(PreferencesRootEvent.SwitchToSession(matrixUser.userId))
                }
                .padding(top = 2.dp, bottom = 2.dp, end = 8.dp),
            matrixUser = matrixUser,
            avatarSize = AvatarSize.AccountItem,
            verticalSpaceWidth = 16.dp,
        )
    }
    ListItem(
        leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Plus())),
        headlineContent = {
            Text(stringResource(CommonStrings.common_add_another_account))
        },
        onClick = onAddAccountClick,
    )
    HorizontalDivider(
        thickness = 8.dp,
        color = ElementTheme.colors.bgSubtleSecondary,
    )
}

@Composable
private fun ColumnScope.Footer(
    version: String,
    deviceId: DeviceId?,
    onClick: (() -> Unit)?,
) {
    val text = remember(version, deviceId) {
        buildString {
            append(version)
            if (deviceId != null) {
                append("\n")
                append(deviceId)
            }
        }
    }
    Text(
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .clickable(enabled = onClick != null, onClick = onClick ?: {})
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        textAlign = TextAlign.Center,
        text = text,
        style = ElementTheme.typography.fontBodySmRegular,
        color = ElementTheme.colors.textSecondary,
    )
}

@PreviewWithLargeHeight
@Composable
internal fun PreferencesRootViewLightPreview(@PreviewParameter(PreferencesRootStateProvider::class) state: PreferencesRootState) =
    ElementPreviewLight(
        drawableFallbackForImages = CommonDrawables.sample_avatar,
    ) { ContentToPreview(state) }

@PreviewWithLargeHeight
@Composable
internal fun PreferencesRootViewDarkPreview(@PreviewParameter(PreferencesRootStateProvider::class) state: PreferencesRootState) =
    ElementPreviewDark(
        drawableFallbackForImages = CommonDrawables.sample_avatar,
    ) { ContentToPreview(state) }

@ExcludeFromCoverage
@Composable
private fun ContentToPreview(state: PreferencesRootState) {
    PreferencesRootView(
        state = state,
        emojiPickerRenderer = NoOpEmojiPickerRenderer,
        onBackClick = {},
        onAddAccountClick = {},
        onOpenCategory = {},
        onOpenUserProfile = {},
        onOpenAbout = {},
        onOpenRageShake = {},
        onOpenLabs = {},
        onOpenDeveloperSettings = {},
        onOpenAdvancedSettings = {},
    )
}
