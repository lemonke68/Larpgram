/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.root

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.preferences.impl.R

/**
 * TG-стиль настроек, Ф2: верхний уровень настроек — это список категорий, каждая ведёт в
 * свой под-экран (drill-down), как в Telegram. Часть категорий маппится напрямую на готовый
 * экран Element (см. [directTarget]); остальные показывает [io.element.android.features.preferences.impl.category.SettingsCategoryView],
 * а где у Element нет бэкенда — экран-заглушка «скоро».
 */
enum class SettingsCategory(
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val colorHex: Long,
) {
    Account(
        titleRes = R.string.larpgram_settings_account_title,
        subtitleRes = R.string.larpgram_settings_account_subtitle,
        colorHex = 0xFF3478F6,
    ),
    Chats(
        titleRes = R.string.larpgram_settings_chats_title,
        subtitleRes = R.string.larpgram_settings_chats_subtitle,
        colorHex = 0xFFF3A33B,
    ),
    Privacy(
        titleRes = R.string.larpgram_settings_privacy_title,
        subtitleRes = R.string.larpgram_settings_privacy_subtitle,
        colorHex = 0xFF4CB050,
    ),
    Notifications(
        titleRes = R.string.larpgram_settings_notifications_title,
        subtitleRes = R.string.larpgram_settings_notifications_subtitle,
        colorHex = 0xFFEB5545,
    ),
    Data(
        titleRes = R.string.larpgram_settings_data_title,
        subtitleRes = R.string.larpgram_settings_data_subtitle,
        colorHex = 0xFF29B6D8,
    ),
    Folders(
        titleRes = R.string.larpgram_settings_folders_title,
        subtitleRes = R.string.larpgram_settings_folders_subtitle,
        colorHex = 0xFF3478F6,
    ),
    Devices(
        titleRes = R.string.larpgram_settings_devices_title,
        subtitleRes = R.string.larpgram_settings_devices_subtitle,
        colorHex = 0xFF37AEA0,
    ),
    Power(
        titleRes = R.string.larpgram_settings_power_title,
        subtitleRes = R.string.larpgram_settings_power_subtitle,
        colorHex = 0xFFF3A33B,
    ),
    Language(
        titleRes = R.string.larpgram_settings_language_title,
        subtitleRes = R.string.larpgram_settings_language_subtitle,
        colorHex = 0xFF8E64E8,
    );

    val color: Color get() = Color(colorHex)

    val icon: ImageVector
        @androidx.compose.runtime.Composable
        get() = when (this) {
            Account -> CompoundIcons.UserProfile()
            Chats -> CompoundIcons.Chat()
            Privacy -> CompoundIcons.Lock()
            Notifications -> CompoundIcons.Notifications()
            Data -> CompoundIcons.Chart()
            Folders -> CompoundIcons.Folder()
            Devices -> CompoundIcons.Devices()
            Power -> CompoundIcons.Settings()
            Language -> CompoundIcons.Keyboard()
        }

    /**
     * Правка форка (ф4): показываем только то, что работает. Папок и энергосбережения пока нет —
     * пустой экран «Скоро» TG не показывает. Язык — системный экран языка приложения, он есть
     * только с Android 13.
     */
    val isAvailable: Boolean
        get() = when (this) {
            Folders, Power -> false
            Language -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            else -> true
        }

    /**
     * Категории, которые ведут прямо на готовый экран Element (без промежуточного экрана-категории).
     */
    val directTarget: DirectTarget?
        get() = when (this) {
            Notifications -> DirectTarget.Notifications
            // Свой экран сеансов вместо ссылки на страницу MAS.
            Devices -> DirectTarget.Devices
            else -> null
        }

    enum class DirectTarget { Notifications, Devices }
}
