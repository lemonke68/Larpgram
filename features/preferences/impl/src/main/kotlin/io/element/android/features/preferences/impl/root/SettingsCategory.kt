/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.root

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import io.element.android.compound.tokens.generated.CompoundIcons

/**
 * TG-стиль настроек, Ф2: верхний уровень настроек — это список категорий, каждая ведёт в
 * свой под-экран (drill-down), как в Telegram. Часть категорий маппится напрямую на готовый
 * экран Element (см. [directTarget]); остальные показывает [io.element.android.features.preferences.impl.category.SettingsCategoryView],
 * а где у Element нет бэкенда — экран-заглушка «скоро».
 */
enum class SettingsCategory(
    val title: String,
    val subtitle: String,
    val colorHex: Long,
) {
    Account(
        title = "Аккаунт",
        subtitle = "Номер, имя пользователя, «О себе»",
        colorHex = 0xFF3478F6,
    ),
    Chats(
        title = "Настройки чатов",
        subtitle = "Обои, оформление, анимации",
        colorHex = 0xFFF3A33B,
    ),
    Privacy(
        title = "Конфиденциальность",
        subtitle = "Устройства, ключи доступа, блокировки",
        colorHex = 0xFF4CB050,
    ),
    Notifications(
        title = "Уведомления",
        subtitle = "Звуки, звонки, счётчик сообщений",
        colorHex = 0xFFEB5545,
    ),
    Data(
        title = "Данные и память",
        subtitle = "Настройки загрузки медиафайлов",
        colorHex = 0xFF29B6D8,
    ),
    Folders(
        title = "Папки с чатами",
        subtitle = "Сортировка чатов по папкам",
        colorHex = 0xFF3478F6,
    ),
    Devices(
        title = "Устройства",
        subtitle = "Управление активными сеансами",
        colorHex = 0xFF37AEA0,
    ),
    Power(
        title = "Энергосбережение",
        subtitle = "Экономия энергии при низком заряде",
        colorHex = 0xFFF3A33B,
    ),
    Language(
        title = "Язык",
        subtitle = "Язык интерфейса",
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
     * Категории, которые ведут прямо на готовый экран Element (без промежуточного экрана-категории).
     */
    val directTarget: DirectTarget?
        get() = when (this) {
            Notifications -> DirectTarget.Notifications
            Data -> DirectTarget.Advanced
            else -> null
        }

    enum class DirectTarget { Notifications, Advanced }
}
