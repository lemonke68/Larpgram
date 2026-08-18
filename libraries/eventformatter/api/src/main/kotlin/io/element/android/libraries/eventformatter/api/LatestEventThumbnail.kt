/*
 * Правка форка: мини-превью медиа в строке списка чатов.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.eventformatter.api

/**
 * Метка места под картинку в тексте последнего сообщения.
 *
 * Картинка вставляется прямо в текст, а не отдельным элементом слева, по двум причинам: в
 * Telegram она стоит ПОСЛЕ имени отправителя («Вы: [фото] Photo»), и строка обрезается
 * многоточием. Отдельным `Image` в `Row` пришлось бы руками считать, сколько места осталось
 * тексту, а `InlineTextContent` делает это сам.
 *
 * Лежит в api, потому что метку ставит форматтер, а рисует картинку список чатов.
 */
const val LATEST_EVENT_THUMBNAIL_ID = "larpgram_thumb"
