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
import io.element.android.features.preferences.impl.R
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
import io.element.android.libraries.designsystem.theme.components.ListItemStyle
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
    onSecureBackupClick: () -> Unit,
    onManageAccountClick: (url: String) -> Unit,
    onLinkNewDeviceClick: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onOpenRageShake: () -> Unit,
    onOpenLockScreenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenDeveloperSettings: () -> Unit,
    onOpenAdvancedSettings: () -> Unit,
    onOpenLabs: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenUserProfile: (MatrixUser) -> Unit,
    onOpenBlockedUsers: () -> Unit,
    onSignOutClick: () -> Unit,
    onDeactivateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = rememberSnackbarHostState(snackbarMessage = state.snackbarMessage)

    // Include pref from other modules
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
        // 'Account' section
        ManageAccountSection(
            state = state,
            onManageAccountClick = onManageAccountClick,
            onLinkNewDeviceClick = onLinkNewDeviceClick,
            onOpenBlockedUsers = onOpenBlockedUsers
        )
        // 'Manage my app' section
        ManageAppSection(
            state = state,
            onOpenNotificationSettings = onOpenNotificationSettings,
            onOpenLockScreenSettings = onOpenLockScreenSettings,
            onSecureBackupClick = onSecureBackupClick,
        )

        // General section
        GeneralSection(
            state = state,
            onOpenAbout = onOpenAbout,
            onOpenAnalytics = onOpenAnalytics,
            onOpenRageShake = onOpenRageShake,
            onOpenAdvancedSettings = onOpenAdvancedSettings,
            onOpenDeveloperSettings = onOpenDeveloperSettings,
            onOpenLabs = onOpenLabs,
            onSignOutClick = onSignOutClick,
            onDeactivateClick = onDeactivateClick,
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
private fun ColumnScope.ManageAppSection(
    state: PreferencesRootState,
    onOpenNotificationSettings: () -> Unit,
    onOpenLockScreenSettings: () -> Unit,
    onSecureBackupClick: () -> Unit,
) {
    TgSettingsGroup {
        TgSettingsItem(
            title = stringResource(id = R.string.screen_notification_settings_title),
            subtitle = "Звуки, звонки, счётчик сообщений",
            color = TgSettingsColors.Red,
            iconVector = CompoundIcons.Notifications(),
            onClick = onOpenNotificationSettings,
        )
        TgSettingsItem(
            title = stringResource(id = CommonStrings.common_screen_lock),
            subtitle = "Код-пароль и биометрия",
            color = TgSettingsColors.Orange,
            iconVector = CompoundIcons.Lock(),
            onClick = onOpenLockScreenSettings,
        )
        if (state.showSecureBackup) {
            TgSettingsItem(
                title = stringResource(id = CommonStrings.common_encryption),
                subtitle = "Резервные ключи, безопасный бэкап",
                color = TgSettingsColors.Green,
                iconVector = CompoundIcons.Key(),
                trailingContent = ListItemContent.Badge.takeIf { state.showSecureBackupBadge },
                onClick = onSecureBackupClick,
            )
        }
    }
}

@Composable
private fun ColumnScope.ManageAccountSection(
    state: PreferencesRootState,
    onManageAccountClick: (url: String) -> Unit,
    onLinkNewDeviceClick: () -> Unit,
    onOpenBlockedUsers: () -> Unit,
) {
    if (state.accountManagementUrl == null && !state.showLinkNewDevice && !state.showBlockedUsersItem) {
        return
    }
    TgSettingsGroup {
        state.accountManagementUrl?.let { url ->
            TgSettingsItem(
                title = stringResource(id = CommonStrings.action_manage_account_and_devices),
                color = TgSettingsColors.Blue,
                iconVector = CompoundIcons.UserProfile(),
                trailingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.PopOut())),
                onClick = { onManageAccountClick(url) },
            )
        }
        if (state.showLinkNewDevice) {
            TgSettingsItem(
                title = stringResource(id = CommonStrings.common_link_new_device),
                color = TgSettingsColors.Cyan,
                iconVector = CompoundIcons.Devices(),
                onClick = onLinkNewDeviceClick,
            )
        }
        if (state.showBlockedUsersItem) {
            TgSettingsItem(
                title = stringResource(id = CommonStrings.common_blocked_users),
                color = TgSettingsColors.Gray,
                iconVector = CompoundIcons.Block(),
                onClick = onOpenBlockedUsers,
                trailingContent = ListItemContent.Text(state.nbOfBlockedUsers.toString()),
            )
        }
    }
}

@Composable
private fun ColumnScope.GeneralSection(
    state: PreferencesRootState,
    onOpenAbout: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onOpenRageShake: () -> Unit,
    onOpenAdvancedSettings: () -> Unit,
    onOpenLabs: () -> Unit,
    onOpenDeveloperSettings: () -> Unit,
    onSignOutClick: () -> Unit,
    onDeactivateClick: () -> Unit,
) {
    TgSettingsGroup {
        TgSettingsItem(
            title = stringResource(id = CommonStrings.common_advanced_settings),
            color = TgSettingsColors.Gray,
            iconVector = CompoundIcons.Settings(),
            onClick = onOpenAdvancedSettings,
        )
        if (state.showLabsItem) {
            TgSettingsItem(
                title = stringResource(id = R.string.screen_labs_title),
                color = TgSettingsColors.Purple,
                iconVector = CompoundIcons.Labs(),
                onClick = onOpenLabs,
            )
        }
        TgSettingsItem(
            title = stringResource(id = CommonStrings.common_about),
            color = TgSettingsColors.Blue,
            iconVector = CompoundIcons.Info(),
            onClick = onOpenAbout,
        )
        if (state.canReportBug) {
            TgSettingsItem(
                title = stringResource(id = CommonStrings.common_report_a_problem),
                color = TgSettingsColors.Orange,
                iconVector = CompoundIcons.ChatProblem(),
                onClick = onOpenRageShake,
            )
        }
        if (state.showAnalyticsSettings) {
            TgSettingsItem(
                title = stringResource(id = CommonStrings.common_analytics),
                subtitle = "Отправка обезличенных данных",
                color = TgSettingsColors.Cyan,
                iconVector = CompoundIcons.Chart(),
                onClick = onOpenAnalytics,
            )
        }
        // Put developer settings at the end, so nothing bad happens if the user clicks 8 times to enable the entry
        if (state.showDeveloperSettings) {
            TgSettingsItem(
                title = stringResource(id = CommonStrings.common_developer_options),
                color = TgSettingsColors.Gray,
                iconVector = CompoundIcons.Code(),
                onClick = onOpenDeveloperSettings,
            )
        }
    }
    TgSettingsGroup {
        TgSettingsItem(
            title = stringResource(id = CommonStrings.action_signout),
            color = TgSettingsColors.Red,
            iconVector = CompoundIcons.SignOut(),
            style = ListItemStyle.Destructive,
            onClick = onSignOutClick,
        )
        if (state.canDeactivateAccount) {
            TgSettingsItem(
                title = stringResource(id = CommonStrings.action_delete_account),
                color = TgSettingsColors.Red,
                iconVector = CompoundIcons.Delete(),
                style = ListItemStyle.Destructive,
                onClick = onDeactivateClick,
            )
        }
    }
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
        onOpenAnalytics = {},
        onOpenRageShake = {},
        onOpenDeveloperSettings = {},
        onOpenAdvancedSettings = {},
        onOpenLabs = {},
        onOpenAbout = {},
        onSecureBackupClick = {},
        onManageAccountClick = {},
        onLinkNewDeviceClick = {},
        onOpenNotificationSettings = {},
        onOpenLockScreenSettings = {},
        onOpenUserProfile = {},
        onOpenBlockedUsers = {},
        onSignOutClick = {},
        onDeactivateClick = {},
    )
}
