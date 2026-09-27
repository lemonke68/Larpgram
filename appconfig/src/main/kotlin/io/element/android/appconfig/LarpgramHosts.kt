/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.appconfig

/**
 * Правка форка: все адреса нашего сервера в одном месте, выведены из одного домена. Стенд, переезд
 * домена или второй инстанс — правка здесь, а не поиск по семи модулям (аудит C-010).
 */
object LarpgramHosts {
    /** server_name Matrix; Synapse и MAS живут на поддоменах. */
    const val DOMAIN = "mango-kokos.ru"
    const val HOMESERVER_URL = "https://$DOMAIN"

    /** Sygnal: шлюз пушей. На том же хосте под /escrow — сервис ключей (server/key-escrow). */
    const val PUSH_GATEWAY_URL = "https://push.$DOMAIN/_matrix/push/v1/notify"
    const val KEY_ESCROW_URL = "https://push.$DOMAIN/escrow"

    /** Прокси поиска GIF (server/gif-proxy). */
    const val GIFS_URL = "https://gifs.$DOMAIN"

    /** Импорт стикерпаков из Telegram (server/stickers). */
    const val STICKERS_IMPORT_URL = "https://stickers.$DOMAIN/import"

    /** Страница раздачи APK и манифест обновлений (larpgram-site). */
    const val SITE_URL = "https://larpgram.$DOMAIN"
}
