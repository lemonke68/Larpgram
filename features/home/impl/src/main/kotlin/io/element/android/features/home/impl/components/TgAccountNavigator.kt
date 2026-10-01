/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.components

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Куда ведут баннеры списка чатов про аккаунт: на экраны настроек «Почта» и «Устройства».
 * Передаётся через CompositionLocal из `HomeFlowNode`: баннеры лежат на четыре слоя глубже, и
 * тянуть два колбэка через все экраны главной ради них незачем. `null` — открыть экран некому
 * (превью, тесты): баннер тогда открывает страницу аккаунта в браузере, как раньше.
 */
internal class TgAccountNavigator(
    val openEmail: () -> Unit,
    val openDevices: () -> Unit,
)

internal val LocalTgAccountNavigator = staticCompositionLocalOf<TgAccountNavigator?> { null }
