/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.appconfig

object PushConfig {
    /**
     * Note: pusher_app_id cannot exceed 64 chars.
     */
    // Правка форка: идентификатор приложения для push-шлюза. Должен совпадать с ключом
    // в конфиге Sygnal, иначе он не найдёт приложение и уведомление молча пропадёт.
    // В апстриме тут im.vector.app.android, то есть Element.
    const val PUSHER_APP_ID: String = "ru.mangokokos.larpgram"
}
