/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.category

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.preferences.impl.advanced.AdvancedSettingsState
import io.element.android.features.preferences.impl.advanced.AppearanceThemeItem
import io.element.android.features.preferences.impl.advanced.MediaUploadSection
import io.element.android.features.preferences.impl.advanced.ModerationAndSafetySection
import io.element.android.features.preferences.impl.advanced.SharePresenceItem
import io.element.android.features.preferences.impl.root.PreferencesRootState
import io.element.android.features.preferences.impl.root.SettingsCategory
import io.element.android.features.preferences.impl.root.TgSettingsColors
import io.element.android.features.preferences.impl.root.TgSettingsGroup
import io.element.android.features.preferences.impl.root.TgSettingsItem
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.components.preferences.PreferencePage
import io.element.android.libraries.designsystem.theme.components.ListItemStyle
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.ui.strings.CommonStrings

@Composable
fun SettingsCategoryView(
    category: SettingsCategory,
    state: PreferencesRootState,
    advancedSettingsState: AdvancedSettingsState,
    onBackClick: () -> Unit,
    onOpenUserProfile: (io.element.android.libraries.matrix.api.user.MatrixUser) -> Unit,
    onAddAccountClick: () -> Unit,
    onLinkNewDeviceClick: () -> Unit,
    onOpenBlockedUsers: () -> Unit,
    onSecureBackupClick: () -> Unit,
    onOpenLockScreenSettings: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onManageAccountClick: (url: String) -> Unit,
    onSignOutClick: () -> Unit,
    onDeactivateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferencePage(
        modifier = modifier,
        onBackClick = onBackClick,
        title = category.title,
    ) {
        when (category) {
            SettingsCategory.Account -> AccountCategory(
                state = state,
                onOpenUserProfile = onOpenUserProfile,
                onAddAccountClick = onAddAccountClick,
                onManageAccountClick = onManageAccountClick,
                onSignOutClick = onSignOutClick,
                onDeactivateClick = onDeactivateClick,
            )
            SettingsCategory.Privacy -> PrivacyCategory(
                state = state,
                advancedSettingsState = advancedSettingsState,
                onOpenBlockedUsers = onOpenBlockedUsers,
                onSecureBackupClick = onSecureBackupClick,
                onOpenLockScreenSettings = onOpenLockScreenSettings,
                onOpenAnalytics = onOpenAnalytics,
            )
            SettingsCategory.Devices -> DevicesCategory(
                state = state,
                onLinkNewDeviceClick = onLinkNewDeviceClick,
                onManageAccountClick = onManageAccountClick,
            )
            SettingsCategory.Chats -> ChatsCategory(advancedSettingsState = advancedSettingsState)
            SettingsCategory.Data -> DataCategory(advancedSettingsState = advancedSettingsState)
            // Категории без бэкенда в Element — заглушка «скоро». Наполнение придёт
            // с TG-фичами (обои/размер текста, папки, энергосбережение, язык), пишем с нуля позже.
            SettingsCategory.Folders,
            SettingsCategory.Power,
            SettingsCategory.Language,
            SettingsCategory.Notifications -> ComingSoonCategory(category)
        }
    }
}

@Composable
private fun ColumnScope.AccountCategory(
    state: PreferencesRootState,
    onOpenUserProfile: (io.element.android.libraries.matrix.api.user.MatrixUser) -> Unit,
    onAddAccountClick: () -> Unit,
    onManageAccountClick: (url: String) -> Unit,
    onSignOutClick: () -> Unit,
    onDeactivateClick: () -> Unit,
) {
    TgSettingsGroup {
        TgSettingsItem(
            title = "Изменить профиль",
            subtitle = "Имя, аватар, «О себе»",
            color = TgSettingsColors.Blue,
            iconVector = CompoundIcons.UserProfile(),
            onClick = { onOpenUserProfile(state.myUser) },
        )
        state.accountManagementUrl?.let { url ->
            TgSettingsItem(
                title = stringResource(id = CommonStrings.action_manage_account_and_devices),
                color = TgSettingsColors.Cyan,
                iconVector = CompoundIcons.UserProfile(),
                trailingContent = ListItemContent.Icon(
                    io.element.android.libraries.designsystem.theme.components.IconSource.Vector(CompoundIcons.PopOut())
                ),
                onClick = { onManageAccountClick(url) },
            )
        }
        if (state.isMultiAccountEnabled) {
            TgSettingsItem(
                title = stringResource(id = CommonStrings.common_add_another_account),
                color = TgSettingsColors.Green,
                iconVector = CompoundIcons.Plus(),
                onClick = onAddAccountClick,
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
private fun ColumnScope.PrivacyCategory(
    state: PreferencesRootState,
    advancedSettingsState: AdvancedSettingsState,
    onOpenBlockedUsers: () -> Unit,
    onSecureBackupClick: () -> Unit,
    onOpenLockScreenSettings: () -> Unit,
    onOpenAnalytics: () -> Unit,
) {
    TgSettingsGroup {
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
        if (state.showBlockedUsersItem) {
            TgSettingsItem(
                title = stringResource(id = CommonStrings.common_blocked_users),
                color = TgSettingsColors.Gray,
                iconVector = CompoundIcons.Block(),
                trailingContent = ListItemContent.Text(state.nbOfBlockedUsers.toString()),
                onClick = onOpenBlockedUsers,
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
    }
    // Реальные тумблеры Element: присутствие + модерация (перенесены из «Дополнительно»).
    SharePresenceItem(advancedSettingsState)
    ModerationAndSafetySection(advancedSettingsState)
}

@Composable
private fun ColumnScope.ChatsCategory(advancedSettingsState: AdvancedSettingsState) {
    // Единственное, что реально есть в Element: тема оформления (день/ночь/чёрная).
    AppearanceThemeItem(advancedSettingsState)
    // Обои, размер текста, цвет имени, углы блоков и т.п. — TG-фичи без бэкенда, пишем позже.
    ComingSoonCategory(SettingsCategory.Chats)
}

@Composable
private fun ColumnScope.DataCategory(advancedSettingsState: AdvancedSettingsState) {
    // Сжатие и качество загрузки медиа (перенесено из «Дополнительно»).
    MediaUploadSection(advancedSettingsState)
}

@Composable
private fun ColumnScope.DevicesCategory(
    state: PreferencesRootState,
    onLinkNewDeviceClick: () -> Unit,
    onManageAccountClick: (url: String) -> Unit,
) {
    if (!state.showLinkNewDevice && state.accountManagementUrl == null) {
        ComingSoonCategory(SettingsCategory.Devices)
        return
    }
    TgSettingsGroup {
        if (state.showLinkNewDevice) {
            TgSettingsItem(
                title = stringResource(id = CommonStrings.common_link_new_device),
                color = TgSettingsColors.Cyan,
                iconVector = CompoundIcons.Devices(),
                onClick = onLinkNewDeviceClick,
            )
        }
        state.accountManagementUrl?.let { url ->
            TgSettingsItem(
                title = stringResource(id = CommonStrings.action_manage_account_and_devices),
                color = TgSettingsColors.Teal,
                iconVector = CompoundIcons.Devices(),
                trailingContent = ListItemContent.Icon(
                    io.element.android.libraries.designsystem.theme.components.IconSource.Vector(CompoundIcons.PopOut())
                ),
                onClick = { onManageAccountClick(url) },
            )
        }
    }
}

@Composable
private fun ColumnScope.ComingSoonCategory(category: SettingsCategory) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = category.subtitle,
            textAlign = TextAlign.Center,
            style = ElementTheme.typography.fontBodyLgMedium,
            color = ElementTheme.colors.textPrimary,
        )
        Text(
            text = "Скоро",
            textAlign = TextAlign.Center,
            style = ElementTheme.typography.fontBodyMdRegular,
            color = ElementTheme.colors.textSecondary,
        )
    }
}
